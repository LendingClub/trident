package org.lendingclub.trident.cluster;

import java.net.Inet4Address;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import javax.annotation.PostConstruct;

import org.lendingclub.mercator.core.SchemaManager;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.base.Preconditions;

@Component
public class TridentClusterManager implements TridentStartupListener {

	String instanceId = UUID.randomUUID().toString();

	Logger logger = LoggerFactory.getLogger(TridentClusterManager.class);

	AtomicReference<String> installationId = new AtomicReference<>();
	@Autowired
	NeoRxClient neo4j;

	AtomicReference<String> leader = new AtomicReference<>();

	// This will keep track of the last time we have observed a leader.  
	long lastLeaderObserved = System.currentTimeMillis(); 
	boolean paused=false;
	
	AtomicReference<BooleanSupplier> ref = new AtomicReference<>(new BooleanSupplier() {

		@Override
		public boolean getAsBoolean() {
			return true;
		}
	});

	public void setLeaderEligibility(BooleanSupplier supplier) {
		Preconditions.checkArgument(supplier != null);
		ref.set(supplier);
	}


	public String getTridentInstallationId() {

		if (installationId.get() != null) {
			return installationId.get();
		}
		JsonNode n = neo4j.execCypher("match (c:Config {type:'trident',name:'installation'}) return c")
				.blockingFirst(NullNode.getInstance());
		String val = n.path("installationId").asText(null);
		if (val != null) {
			this.installationId.set(val);
		} else {
			val = neo4j
					.execCypher(
							"merge (c:Config {type:'trident',name:'installation'}) set c.installationId={id} return c",
							"id", UUID.randomUUID().toString())
					.blockingFirst(NullNode.getInstance()).path("installationId").asText(null);
			if (val == null) {
				throw new IllegalStateException("could not set installationId");
			}
			this.installationId.set(val);

		}
		Preconditions.checkState(installationId.get() != null);
		return this.installationId.get();
	}


	public String getInstanceId() {
		return instanceId;
	}

	public boolean isLeader() {
		String v = leader.get();
		return v != null && v.equals(getInstanceId());
	}
	public Optional<String> getIpAddress() {
		try {
			String val = Inet4Address.getLocalHost().getHostAddress();
			return Optional.ofNullable(val);
		}
		catch (Exception e) {
			logger.warn("problem getting ip address: {}",e.toString());
		}
		return Optional.empty();
	}
	protected void forceElection() {
		logger.info("forcing election");
		leader.set("NOT_FOUND");
		BooleanSupplier amIlive = ref.get();
		if (amIlive != null && amIlive.getAsBoolean() == false) {
			// If we are not live, make sure that
			logger.info("node is not live...cannot be a leader");
			neo4j.execCypher("match (t:TridentClusterState) set  t.leader=false");
			return;
		}

		// mark all the nodes with a random string. We'll use this as a key when
		// we nominate ourself as leader.
		String electionId = UUID.randomUUID().toString();
		neo4j.execCypher("match (t:TridentClusterState) set t.electionId={electionId}, t.leader=false", "electionId",
				electionId);

		// now mark ourselves as a leader, but only if the electionId matches
		neo4j.execCypher(
				"match (t:TridentClusterState) where t.electionId={electionId} and t.instanceId={instanceId} set t.leader=true",
				"electionId", electionId, "instanceId", getInstanceId());

		neo4j.execCypher(
				"match (t:TridentClusterState) return t.instanceId as instanceId,t.electionId as electionId,t.leader as leader, timestamp()-t.heartbeat as lastHeartbeatInterval")
				.forEach(it -> {
					logger.info("cluster state: " + it + " "
							+ (getInstanceId().equals(it.path("instanceId").asText("NOT_FOUND")) ? "*" : ""));
					if (it.path("leader").asBoolean(false) == true) {
						logger.info("congratulations, we have been elected the leader!");
						leader.set(it.path("instanceId").asText());
					}
				});
	}

	protected void manageClusterState() {
		neo4j.execCypher("merge (t:TridentClusterState {instanceId:{instanceId}}) set t.heartbeat=timestamp(),t.ipAddress={ip}",
				"instanceId", getInstanceId(),"ip",getIpAddress().orElse(""));

		AtomicLong leaderHeartbeatInterval = new AtomicLong(Long.MAX_VALUE);
		AtomicInteger leaderCount = new AtomicInteger();
		neo4j.execCypher(
				"match (t:TridentClusterState) return t.instanceId as instanceId,t.electionId as electionId,t.leader as leader, timestamp()-t.heartbeat as lastHeartbeatInterval")
				.forEach(it -> {
					boolean leader = it.path("leader").asBoolean(false);
					if (leader) {
						leaderCount.incrementAndGet();
						long heartbeatInterval = it.path("lastHeartbeatInterval").asLong(TimeUnit.DAYS.toMillis(1));
						leaderHeartbeatInterval.set(heartbeatInterval);
						lastLeaderObserved = System.currentTimeMillis();
					}

				});
		logger.debug("# of leaders: {}", leaderCount.get());
		if (leaderCount.get() != 1) {
			forceElection();
		} else if (leaderHeartbeatInterval.get() > TimeUnit.SECONDS.toMillis(45)) {
			forceElection();
		}
		if (isLeader() && isEligibleLeader()==false) {
			// I am the leader but not actually eligible
			forceElection();
		}
		if (System.currentTimeMillis()-lastLeaderObserved>TimeUnit.MINUTES.toMillis(5)) {
			logger.warn("trident cluster={} has no leader!",getTridentInstallationId());
		}
		// delete old cruft
		neo4j.execCypher(
				"match (t:TridentClusterState) where NOT EXISTS(t.heartbeat) OR timestamp()-t.heartbeat>{millis} delete t",
				"millis", TimeUnit.MINUTES.toMillis(15));
	}

	@PostConstruct
	public void startup() {
		Runnable r = new Runnable() {

			@Override
			public void run() {
				try {
					if (paused) {
						// this is really here to facilitate unit testing
						logger.warn("cluster manager is paused");
						return;
					}
					manageClusterState();
				} catch (Exception e) {
					logger.warn("problem with cluster state", e);
				}
			}

		};
		
	
		Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(r, 30, 10, TimeUnit.SECONDS);
	}

	@Override
	public void onStart(org.springframework.context.ApplicationContext context) {
		new SchemaManager(neo4j).applyUniqueConstraint("TridentClusterState", "instanceId");
		logger.info("trident instanceId: {}", getInstanceId());
		logger.info("trident installationId: {}", getTridentInstallationId());
	}

	public boolean isEligibleLeader() {
		BooleanSupplier supplier = ref.get();
		if (supplier==null) {
			return true;
		}
		else {
			return supplier.getAsBoolean();
		}
	}

}
