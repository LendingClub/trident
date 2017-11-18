package org.lendingclub.trident.cluster;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIdentifier;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.loadbalancer.LoadBalancerManager;
import org.lendingclub.trident.loadbalancer.LoadBalancerManager.LoadBalancerCommand;
import org.lendingclub.trident.loadbalancer.LoadBalancerManager.LoadBalancerType;
import org.springframework.beans.factory.annotation.Autowired;

public class TridentClusterManagerIntegrationTest extends TridentIntegrationTest {

	@Autowired
	TridentClusterManager tridentClusterManager;

	@Autowired
	NeoRxClient neo4j;
	



	
	@Test
	public void testElectionAfterDeleteAll() {
		neo4j.execCypher("match (a:TridentClusterState) detach delete a");
		tridentClusterManager.leader.set("");
		
		Assertions.assertThat(tridentClusterManager.isLeader()).isFalse();
		tridentClusterManager.manageClusterState();
		
		AtomicInteger count = new AtomicInteger(0);
		neo4j.execCypherAsList("match (a:TridentClusterState) return a").forEach(it->{
		
			Assertions.assertThat(it.path("electionTs").asLong()).isCloseTo(System.currentTimeMillis(), Offset.offset(1000L));
			Assertions.assertThat(it.path("leader").asBoolean()).isTrue();
			count.incrementAndGet();
		});
		
		Assertions.assertThat(count.get()).isEqualTo(1);
		Assertions.assertThat(tridentClusterManager.isLeader()).isTrue();
		Assertions.assertThat(tridentClusterManager.leader.get()).isEqualTo(tridentClusterManager.getInstanceId());
		
	}
	
	@Test
	public void testOtherLeaderWithExpiredHeartbeat() {
		neo4j.execCypher("match (a:TridentClusterState) detach delete a");
		neo4j.execCypher("create (a:TridentClusterState {instanceId:{id}}) set a.heartbeat=timestamp()-15000, a.leader=true","id",UUID.randomUUID().toString());
		neo4j.execCypher("create (a:TridentClusterState {instanceId:{id}}) set a.leder=true","id",UUID.randomUUID().toString());
		tridentClusterManager.manageClusterState();
		
		AtomicInteger count = new AtomicInteger(0);
		// note that we MUST use execCypherAsList here
		neo4j.execCypher("match (a:TridentClusterState) return a").forEach(it->{
			count.incrementAndGet();
			if (it.path("leader").asBoolean()) {
				Assertions.assertThat(it.path("instanceId").asText()).isEqualTo(tridentClusterManager.getInstanceId());
			}
		});
		Assertions.assertThat(count.get()).isEqualTo(2);
		Assertions.assertThat(tridentClusterManager.isLeader()).isTrue();
		
	}
	
	@Test
	public void testOtherLeaderWithExpiredInstance() {
		neo4j.execCypher("match (a:TridentClusterState) detach delete a");
		neo4j.execCypher("create (a:TridentClusterState {instanceId:{id}}) set a.heartbeat=timestamp()-900000, a.leader=true","id",UUID.randomUUID().toString());
		neo4j.execCypher("create (a:TridentClusterState {instanceId:{id}}) set a.leder=true","id",UUID.randomUUID().toString());
		tridentClusterManager.manageClusterState();
		
		AtomicInteger count = new AtomicInteger(0);
		// note that we MUST use execCypherAsList here
		neo4j.execCypher("match (a:TridentClusterState) return a").forEach(it->{
			count.incrementAndGet();
			if (it.path("leader").asBoolean()) {
				Assertions.assertThat(it.path("instanceId").asText()).isEqualTo(tridentClusterManager.getInstanceId());
			}
		});
		Assertions.assertThat(count.get()).isEqualTo(1);  // the other node should be sufficiently old that it is deleted completely
		Assertions.assertThat(tridentClusterManager.isLeader()).isTrue();
		
	}
	@Test
	public void testOtherLeaderWithRecentHeartbeat() {
		String otherId = UUID.randomUUID().toString();
	
		neo4j.execCypher("match (a:TridentClusterState) detach delete a");
		neo4j.execCypher("create (a:TridentClusterState {instanceId:{id}}) set a.heartbeat=timestamp()-1000, a.leader=true","id",otherId);

		tridentClusterManager.manageClusterState();
		
		AtomicInteger count = new AtomicInteger(0);
		
		// note that we MUST use execCypherAsList here
		neo4j.execCypherAsList("match (a:TridentClusterState) return a").forEach(it->{
			count.incrementAndGet();
			if (it.path("leader").asBoolean()) {
				Assertions.assertThat(it.path("instanceId").asText()).isEqualTo(otherId);
			}

		});
		
		Assertions.assertThat(count.get()).isEqualTo(2);
		Assertions.assertThat(tridentClusterManager.leader.get()).isEqualTo(otherId);
	}
	
	
	@Test
	public void testMultipleLeadersWithRecentHeartbeats() {
		// IF we have multiple leaders, we are guaranteed to be elected leader, because the multiple leader situation will immediately
		// trigger an election and the other two nodes are fake, so we are guaranteed to become the leader.
		String otherId = UUID.randomUUID().toString();
		String otherId2 = UUID.randomUUID().toString();
		neo4j.execCypher("match (a:TridentClusterState) detach delete a");
		neo4j.execCypher("create (a:TridentClusterState {instanceId:{id}}) set a.heartbeat=timestamp()-1000, a.leader=true","id",otherId);
		neo4j.execCypher("create (a:TridentClusterState {instanceId:{id}}) set a.heartbeat=timestamp()-1000, a.leader=true","id",otherId2);
		tridentClusterManager.manageClusterState();
		
		AtomicInteger count = new AtomicInteger(0);
		// note that we MUST use execCypherAsList here
		neo4j.execCypherAsList("match (a:TridentClusterState) return a").forEach(it->{
			count.incrementAndGet();
			if (it.path("leader").asBoolean()) {
				Assertions.assertThat(it.path("instanceId").asText()).isEqualTo(tridentClusterManager.getInstanceId());
			}
		});
		Assertions.assertThat(count.get()).isEqualTo(3);
		Assertions.assertThat(tridentClusterManager.isLeader()).isTrue();
	}
}
