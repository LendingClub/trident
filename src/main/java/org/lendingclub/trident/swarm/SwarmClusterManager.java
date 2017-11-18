package org.lendingclub.trident.swarm;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.lendingclub.mercator.core.Projector;
import org.lendingclub.mercator.docker.DockerScanner;
import org.lendingclub.mercator.docker.DockerScannerBuilder;
import org.lendingclub.mercator.docker.SwarmScanner;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.NotFoundException;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.crypto.CertificateAuthority;
import org.lendingclub.trident.crypto.CertificateAuthority.CertDetail;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.crypto.CryptoService;
import org.lendingclub.trident.provision.SwarmTemplateManager;
import org.lendingclub.trident.swarm.aws.AWSController;
import org.lendingclub.trident.swarm.baremetal.BareMetalController;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

@Component
public class SwarmClusterManager implements TridentStartupListener {

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	Projector projector;

	@Autowired
	org.lendingclub.trident.scheduler.DistributedTaskScheduler taskExecutor;

	@Autowired
	CertificateAuthorityManager certificateAuthorityManager;

	@Autowired
	CryptoService cryptoService;

	Logger logger = LoggerFactory.getLogger(SwarmClusterManager.class);

	File transientCertStoreDir;

	@Autowired
	SwarmTemplateManager swarmTemplateManager;

	@Autowired
	AWSController awsController;

	@Autowired
	BareMetalController bareMetalController;

	long certValidityMinutes = TimeUnit.HOURS.toMinutes(4);
	Cache<String, CertDetail> certCache = CacheBuilder.newBuilder()
			.expireAfterWrite(certValidityMinutes / 2, TimeUnit.MINUTES).build();

	Cache<Address, DockerClient> clientCache = CacheBuilder.newBuilder().expireAfterAccess(10, TimeUnit.MINUTES)
			.build();

	ScheduledExecutorService watchdog;


	public static class Address {
		String url;
		String tridentClusterId;

		public boolean isUnixDomainSocket() {
			return url.startsWith("unix://");
		}

		public Address(String url, String cluster) {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(url));
			Preconditions.checkArgument(!Strings.isNullOrEmpty(cluster));
			this.url = url;
			this.tridentClusterId = cluster;
		}

		public String getUrl() {
			return url;
		}

		public String getCacheKey() {
			return tridentClusterId + "-" + url;
		}

		public int hashCode() {
			return getCacheKey().hashCode();
		}

		public String toString() {
			return MoreObjects.toStringHelper(this).add("tridentClusterId", tridentClusterId).add("url", url)
					.toString();
		}

		public boolean equals(Object obj) {
			if (obj == null || (!(obj instanceof Address))) {
				return false;
			}
			Address other = (Address) obj;
			return getCacheKey().equals(other.getCacheKey());

		}
	}

	/**
	 * Returns a priority-ordered list of manager endpoints to use.
	 * 
	 * @param name
	 * @return
	 */
	protected List<Address> getManagerAddressList(String name) {

		List<Address> addressList = Lists.newArrayList();

		JsonNode swarmNode = neo4j.execCypher(
				"match (s:DockerSwarm) where s.tridentClusterId={id} or s.name={id} or s.swarmClusterId={id} return s",
				"id", name).blockingFirst(MissingNode.getInstance());
		String managerApiUrl = swarmNode.path("managerApiUrl").asText();
		String tridentClusterId = swarmNode.path("tridentClusterId").asText();
		if (!Strings.isNullOrEmpty(managerApiUrl)) {
			Address address = new Address(managerApiUrl, tridentClusterId);

			addressList.add(address);
			if (address.isUnixDomainSocket()) {
				return addressList;
			}
		}

		neo4j.execCypherAsList(
				"match (s:DockerSwarm)--(n:DockerHost) where (s.tridentClusterId={id} or s.swarmClusterId={id} or s.name={id}) and n.role='manager' return s.tridentClusterId as tridentClusterId, n.state as state, n.addr as addr, n.leader as leader",
				"id", name).forEach(it -> {
					String addr = Splitter.on(":").splitToList(it.path("addr").asText()).get(0);
					addr = addr.trim();

					if (!Strings.isNullOrEmpty(addr)) {
						if (it.path("state").asText().equals("down")) {
							// skip nodes that are down
						} else {
							Address address = new Address("tcp://" + addr + ":2376", tridentClusterId);

							addressList.add(address);
						}
					}
				});

		String managerAddress = removePort(swarmNode.path("managerAddress").asText());

		if (!Strings.isNullOrEmpty(managerAddress)) {
			Address address = new Address("tcp://" + managerAddress + ":2376", tridentClusterId);

			addressList.add(address);
		}

		return addressList;
	}

	File getTransientCertStoreDir() {
		if (transientCertStoreDir == null) {
			transientCertStoreDir = Files.createTempDir();
		}
		if (!transientCertStoreDir.exists()) {
			transientCertStoreDir.mkdirs();
		}
		return transientCertStoreDir;
	}

	File getCertPath(String id) {
		// look up the swarm
		throw new UnsupportedOperationException();
	}

	protected DockerClient getSwarmManagerClient(String name) {

		DockerClient client = null;
		// We are really doing some client-side load balancing here and need a
		// good search algorithm.
		// For instance: try connecting to the leader, then fall back to the
		// other nodes.

		List<Address> managerAddressList = getManagerAddressList(name);

		logger.info("manager addresses: {}", managerAddressList);

		if (managerAddressList.isEmpty()) {
			throw new TridentException("could not find manager address for swarm: " + name);
		}
		for (Address address : managerAddressList) {
			try {
				client = createClientForAddress(address);
				if (client != null) {

					return client;
				}
			} catch (RuntimeException e) {
				logger.info("failed to connect to: {}", address);
				// keep trying
			}
		}
		throw new TridentException("could not obtain connection to: " + managerAddressList);

	}

	DockerClient createClientForAddress(Address address) {

		try {

			Preconditions.checkArgument(address != null);
			Preconditions.checkArgument(!Strings.isNullOrEmpty(address.tridentClusterId));
			Preconditions.checkArgument(!Strings.isNullOrEmpty(address.url));

			DockerClient client = clientCache.getIfPresent(address.getCacheKey());
			if (client != null) {
				return client;
			}

			Optional<String> tridentClusterId = lookupTridentClusterId(address.tridentClusterId);

			if (address.isUnixDomainSocket()) {
				DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
						.withDockerHost(address.getUrl()).build();
				JerseyDockerCmdExecFactory cf = new JerseyDockerCmdExecFactory().withReadTimeout(10000)
						.withConnectTimeout(10000);
				client = DockerClientBuilder.getInstance(config).withDockerCmdExecFactory(cf).build();
			} else {
				CertDetail cd = getSwarmCertDetail(tridentClusterId.get());

				File tempDir = Files.createTempDir();
				cd.writeCertPath(tempDir);
				logger.info("constructing new DockerClient to {}", address.url);
				DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
						.withDockerHost(address.url).withDockerCertPath(tempDir.getAbsolutePath())
						.withDockerTlsVerify(true).build();
				JerseyDockerCmdExecFactory cf = new JerseyDockerCmdExecFactory().withReadTimeout(10000)
						.withConnectTimeout(10000);
				client = DockerClientBuilder.getInstance(config).withDockerCmdExecFactory(cf).build();
			}
			try {
				logger.info("ping check to {} ...", address);
				client.pingCmd().exec();
				markSuccessfulConnection(address);
			} catch (Exception e) {
				try {
					client.close();
				} catch (Exception ignore) {
					// ignore
				}
				logger.info("ping check failed to {}", address);

				if (e instanceof RuntimeException) {
					throw RuntimeException.class.cast(e);
				}
				throw new TridentException(e);
			}

			clientCache.put(address, client);
			return client;
		} catch (IOException e) {
			throw new TridentException(e);
		}
	}

	protected WebTarget getSwarmManagerWebTarget(String name) {
		return SwarmScanner.extractWebTarget(getSwarmManagerClient(name));
	}

	protected DockerScanner getSwarmScanner(String name) {
		return projector.createBuilder(DockerScannerBuilder.class).withDockerClient(getSwarmManagerClient(name))
				.build();

	}

	protected void scanSwarm(String name) {
		DockerScanner scanner = getSwarmScanner(name);

		// First, scan the swarm
		try {
			scanner.scan();
		} catch (RuntimeException e) {
			logger.warn("problem scanning swarm=" + name, e);
		}

		// Second, update the managerAddress, based on info in the previous
		// scan.
		try {
			updateManagerAddressForSwarm(name);
		} catch (RuntimeException e) {
			logger.warn("problem updating maanger endpoint for swarm=" + name, e);
		}

		// Third, find the best value of managerApiUrl and set it.
		try {
			updateManagerApiUrlForSwarm(name);
		} catch (RuntimeException e) {
			logger.warn("problem updating manager api url for swarm=" + name, e);
		}
	}

	void markSuccessfulConnection(String id) {
		neo4j.execCypher(
				"match (s:DockerSwarm {tridentClusterId:{id}}) set s.lastContactTs=timestamp(), s.updateTs=timestamp()",
				"id", id);
	}

	private void markSuccessfulConnection(Address address) {
		markSuccessfulConnection(address.tridentClusterId);
	}

	protected void assignTridentClusterToLocalClusters() {
		neo4j.execCypher(
				"match (a:DockerSwarm) where (a.managerApiUrl =~ 'unix.*') and (not exists(a.tridentClusterId)) return a")
				.forEach(it -> {

					if (Strings.isNullOrEmpty(it.path("tridentClusterId").asText())) {
						String swarmClusterId = it.path("swarmClusterId").asText();
						String uuid = UUID.randomUUID().toString();
						neo4j.execCypher(
								"match (a:DockerSwarm {swarmClusterId:{swarmClusterId}}) set a.tridentClusterId={tridentClusterId} return a",
								"swarmClusterId", swarmClusterId, "tridentClusterId", uuid);
					}

				});

	}

	public List<String> getSwarmNames() {
		return neo4j.execCypher("match (d:DockerSwarm) where exists(d.tridentClusterId) and exists(d.name) return d")
				.map(it -> it.path("name").asText()).toList().blockingGet();
	}

	public void scanAllSwarms() {

		assignTridentClusterToLocalClusters();
		// Not sure why this is happening, but we see DockerSwarm zombies being
		// left over that are missing "name"
		// attributes. This cleans them out and reduces noise.
		neo4j.execCypher(
				"match (a:DockerSwarm) where ((not exists (a.name)) or (not exists(a.tridentClusterId))) detach delete a");

		neo4j.execCypher("match (d:DockerSwarm) where exists(d.tridentClusterId) and exists(d.name) return d")
				.forEach(it -> {
					try {
						String id = it.path("tridentClusterId").asText(it.path("swarmClusterId").asText());
						String name = it.path("name").asText();
						// For some reason we are ending up with DockerSwarm
						// nodes with no name.
						// Until we get this fixed, we suppress these broken
						// nodes.
						if (!(Strings.isNullOrEmpty(id) || Strings.isNullOrEmpty(name))) {
							logger.info("Scanning swarm: {}", id);
							scanSwarm(id);
							markSuccessfulConnection(id);
							
							// with positive contact, we don't need the join tokens any longer
							removeJoinTokens(id);
						}
					} catch (RuntimeException e) {
						if (e.getMessage() != null && e.getMessage().contains("could not find manager address")) {
							logger.warn(e.getMessage());
						} else {
							logger.warn("problem scanning swarm", e);
						}
					}
				});
	}

	boolean isAwsTemplate(JsonNode n) {
		return n.has("awsManagerSubnets") || n.has("awsManagerSecurityGroups");
	}

	private void removeJoinTokens(String id) {
		neo4j.execCypher("match (s:DockerSwarm) where s.tridentClusterId={id} or s.swarmClusterId={id} or s.name={id} remove s.managerJoinToken, s.workerJoinToken","id",id);
	}
	public JsonNode createSwarm(JsonNode n) {
		String name = n.path("name").asText();
		String templateName = n.path("template").asText();

		Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "swarm name cannot be null");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(templateName), "swarm template must be set");

		Optional<JsonNode> template = swarmTemplateManager.getTemplate(templateName);
		if (template.isPresent()) {
			if (isAwsTemplate(template.get())) {
				return awsController.createSwarm(n);
			} else {
				return bareMetalController.createSwarm(n);
			}
		} else {
			throw new IllegalArgumentException("invalid template: " + templateName);
		}

	}

	private String removePort(String addr) {
		return Splitter.on(":").splitToList(addr).get(0).trim();
	}

	Optional<String> lookupTridentClusterId(String id) {

		String tridentClusterId = neo4j.execCypher(
				"match (a:DockerSwarm) where a.tridentClusterId={id} or a.dockerSwarmId={id} or a.name={} return a",
				"id", id).blockingFirst(MissingNode.getInstance()).path("tridentClusterId").asText();
		if (!Strings.isNullOrEmpty(tridentClusterId)) {
			return Optional.of(tridentClusterId);
		}

		tridentClusterId = neo4j
				.execCypher("match (a:DockerHost)--(s:DockerSwarm) where a.swarmNodeId={id} or a.addr={id} return s",
						"id", id)
				.blockingFirst(MissingNode.getInstance()).path("tridentClusterId").asText();
		if (!Strings.isNullOrEmpty(tridentClusterId)) {
			return Optional.of(tridentClusterId);
		}

		return Optional.empty();

	}

	public Swarm getSwarm(String id) {
		List<JsonNode> nl = neo4j.execCypher(
				"match (s:DockerSwarm) where s.name={id} or s.swarmClusterId={id} or s.tridentClusterId={id} return s",
				"id", id).toList().blockingGet();
		if (nl.isEmpty()) {
			throw new NotFoundException("swarm", id);
		}
		if (nl.size() > 1) {
			throw new TridentException("more than one swarm found: " + id);
		}

		SwarmImpl si = new SwarmImpl();
		si.tridentId = nl.get(0).path("tridentClusterId").asText(null);
		si.swarmId = nl.get(0).path("swarmClusterId").asText(null);
		si.swarmName = nl.get(0).path("name").asText(null);
		si.swarmClusterManager = this;
		Preconditions.checkState(!Strings.isNullOrEmpty(si.tridentId));
		Preconditions.checkState(!Strings.isNullOrEmpty(si.swarmName));
		return si;
	}

	protected DockerClient getSwarmNodeClient(String n) {

		DockerClient c = null;
		JsonNode node = neo4j.execCypher(
				"match (a:DockerHost)--(s:DockerSwarm) where a.addr={id} or a.swarmNodeId={id} return a.addr,s.tridentClusterId as tridentClusterId",
				"id", n).blockingFirst();
		String addr = removePort(node.get("addr").asText());
		Address address = null;

		// This is kind of ugly storing a url in the address field.
		if (addr.startsWith("unix://")) {
			address = new Address(addr, node.path("tridentClusterId").asText());
		} else {
			address = new Address("tcp://" + addr + ":2376", node.path("tridentClusterId").asText());
		}

		c = createClientForAddress(address);
		if (c == null) {
			throw new TridentException("could not obtain swarm node client for " + n);
		}
		return c;
	}

	protected WebTarget getSwarmNodeWebTarget(String n) {
		return SwarmScanner.extractWebTarget(getSwarmNodeClient(n));
	}

	public CertDetail getSwarmCertDetail(String id) {

		JsonNode n = neo4j
				.execCypher("match (d:DockerSwarm) where d.tridentClusterId={id} or d.dockerSwarmId={id} return d",
						"id", id)
				.blockingFirst();
		String tridentClusterId = n.get("tridentClusterId").asText();

		CertDetail cd = certCache.getIfPresent(tridentClusterId);
		if (cd == null) {
			CertificateAuthority ca = certificateAuthorityManager.getCertificateAuthority(tridentClusterId);

			// The cert cache needs to be aligned with other caches
			cd = ca.createClientCert().withValidityMinutes((int) this.certValidityMinutes).withCN("internal-trident")
					.build();
			certCache.put(tridentClusterId, cd);
		}

		return cd;
	}

	void checkCachedConnections() {
		Stopwatch sw = Stopwatch.createStarted();
		logger.debug("checking cached connections...");
		List<Address> clients = Lists.newArrayList(clientCache.asMap().keySet());
		clients.forEach(it -> {
			try {
				logger.debug("checking connection: {}", it);
				DockerClient client = clientCache.getIfPresent(it);
				if (client != null) {
					try {
						client.pingCmd().exec();
						logger.debug("client is alive: {}", it);
						markSuccessfulConnection(it);
					} catch (Exception e) {
						logger.info("client to {} failed ping check - {}", it, e.toString());
						clientCache.invalidate(it);
					}
				}
			} catch (RuntimeException e) {
				// we really should never reach here
				logger.error("unexpected exception", e);
			}

		});
		logger.info("cached connection check complete ({} ms)", sw.elapsed(TimeUnit.MILLISECONDS));
	}

	public void onStart(ApplicationContext ctx) {

		watchdog = Executors.newSingleThreadScheduledExecutor();

		Runnable r = new Runnable() {

			@Override
			public void run() {
				// it is not ideal that we block on each connection. However,
				// doing the checks in parallel has its own challenges.
				try {
					checkCachedConnections();
				} catch (RuntimeException e) {
					// we should never blow out of this
					logger.error("unexpected exception", e);
				}
			}

		};
		watchdog.scheduleWithFixedDelay(r, 0, 30, TimeUnit.SECONDS);

		taskExecutor.scheduleTask("* * * * *", SwarmScanTask.class);

	}

	/**
	 * Obtain a manager endpoint <ip>:2377. We use this to keep the managerAddress
	 * field up-to-date. This is used as nodes join the swarm.
	 * 
	 * @param id
	 * @return
	 */
	protected Optional<String> getInternalSwarmManagerAddress(String id) {

		String cypher = "match (a:DockerSwarm)--(h:DockerHost) where "
				+ "(a.name={id} or a.tridentClusterId={id}) and h.role='manager' "
				+ " and timestamp()-h.updateTs<300000 " + "return h.role as role," + "h.state as state,"
				+ "h.addr as addr," + "h.availability as availability, " + "h.leader as leader, "
				+ "timestamp()-h.updateTs " + "as updateMillisAgo";

		List<JsonNode> managerNodes = neo4j.execCypherAsList(cypher, "id", id);

		// First look for a leader
		String ip = managerNodes.stream().filter(it -> it.path("leader").asBoolean(false)).findFirst()
				.orElse(MissingNode.getInstance()).path("addr").asText(null);
		if (Strings.isNullOrEmpty(ip)) {
			// No leader...we'll take anything.
			ip = managerNodes.stream().findFirst().orElse(MissingNode.getInstance()).path("addr").asText(null);
		}
		if (!Strings.isNullOrEmpty(ip)) {
			if (ip.startsWith("unix://")) {

				// not actually an IP address. Looks like a unix domain socket.
				return Optional.empty();

			}
			return Optional.of(ip + ":2377");
		}
		return Optional.empty();
	}

	private void updateManagerAddressForSwarm(String id) {
		Optional<String> addr = getInternalSwarmManagerAddress(id);
		if (addr.isPresent()) {
			neo4j.execCypher(
					"match (s:DockerSwarm) where s.tridentClusterId={id} or s.name={id} set s.managerAddress={addr}",
					"id", id, "addr", addr.get());
		} else {
			logger.warn("could not locate internal swarm manager address for swarm={}", id);
		}
	}

	boolean ping(DockerClient c) {
		if (c == null) {
			return false;
		}
		try {
			c.pingCmd().exec();
			return true;
		} catch (RuntimeException e) {
			logger.warn("ping failed to " + SwarmScanner.extractWebTarget(c).getUri().toString());
		}
		return false;
	}

	boolean ping(Address address) {
		if (address == null) {
			return false;
		}
		return ping(createClientForAddress(address));

	}

	/**
	 * Updates the DockerSwarm.managerApiUrl attribute to a legitimate value. It is
	 * going to try using managerDnsName first. If we can connect successfully, then
	 * we use that value. If we can't connect, we'll fall back to using the existing
	 * connection, which we confirm is correct.
	 * 
	 * Note that managerApiUrl and maangerAddress are different. managerApiUrl
	 * contains the url that clients will use to communicate with manager, which
	 * could be a CNAME to a load balancer.
	 * 
	 * On the other hand, managerAddress contains the swarm-internal communication
	 * ip:2377 pair.
	 * 
	 * This is all kind of hairy and difficult to test. Hopefully all works and we
	 * never touch it again. If it does have issues, we'll need to decompose this
	 * into a separate class that makes it easy to override specific operations with
	 * mock calls.
	 * 
	 * @param id
	 */

	private void updateManagerApiUrlForSwarm(String id) {

		// Locate the swarm in neo4j
		JsonNode n = neo4j
				.execCypher("match (s:DockerSwarm) where s.tridentClusterId={id} or s.name={id} return s", "id", id)
				.blockingFirst(null);

		// If there was no swarm, just move on...
		if (n == null) {
			logger.info("could not locate swarm={}", id);
			return;
		}

		String newUrl = null;
		String tridentClusterId = n.path("tridentClusterId").asText();
		String tridentClusterName = n.path("name").asText();

		if (Strings.isNullOrEmpty(tridentClusterId) || Strings.isNullOrEmpty(tridentClusterName)) {
			// no good if we don't have a clusterId and name
			return;
		}
		try {
			String managerDnsName = n.path("managerDnsName").asText();
			String managerApiUrl = n.path("managerApiUrl").asText();

			// This is tricky. If
			// a) managerDnsName is set
			// b) the connection actually works
			// then we should use that value
			// EXCEPT
			// if it is a unix domain socket.

			if (managerApiUrl.startsWith("unix://")) {
				// don't change anything else
				return;
			}
			if (!Strings.isNullOrEmpty(managerDnsName)) {
				String url = "tcp://" + managerDnsName + ":2376";
				if (!managerApiUrl.equals(url)) {

					Address address = new Address(url, tridentClusterId);
					try {
						if (ping(address)) {
							logger.info("connectivity confirmed to {}", address);
							newUrl = url;
						} else {
							logger.info("could not establish connection to: " + address);
						}
					} catch (Exception e) {
						// stack trace is too noisy here
						logger.info("could not establish connection to: " + address + " (" + e.toString() + ")");
					}

				}
			}

			if (newUrl == null) {
				String candidateUrl = null;
				try {
					DockerClient dc = getSwarmManagerClient(id);
					WebTarget wt = SwarmScanner.extractWebTarget(dc);
					candidateUrl = "tcp://" + wt.getUri().getHost() + ":" + wt.getUri().getPort();
					dc.pingCmd().exec(); // will throw exception if we cannot
											// connect

					newUrl = candidateUrl;

				} catch (RuntimeException ignore) {
					logger.info("could not connect to {}", candidateUrl);
				}
			}

		} catch (RuntimeException e) {
			logger.warn("problem", e);
		}
		if (!Strings.isNullOrEmpty(newUrl)) {
			logger.info("setting cluster={} managerApiUrl={}", tridentClusterId, newUrl);
			neo4j.execCypher("match (s:DockerSwarm {tridentClusterId:{id}}) set s.managerApiUrl={url}", "id",
					tridentClusterId, "url", newUrl);
		}

	}

	@PostConstruct
	protected void attemptLocalSwarmScan() {

		// On startup, we will attempt to scan the loacl docker daemon, but only
		// in a local dev environment.
		// This is convenient for testing, but undesirable in a deployed
		// environment.
		if (new File(".", "src/main/java").exists())

		{
			logger.info("spawning thread to perform initial scan of local docker daemon...");
			// no need to slow things down waiting for the scan...just let it
			// happen
			// in another thread
			new Thread("docker-scan") {

				public void run() {
					// projector.createBuilder(DockerScannerBuilder.class).withLocalDockerDaemon().withFailOnError(false)
					// .build().scan();
				}
			}.start();

		}

	}

	public SwarmDiscoverySearch newServiceDiscoverySearch() {
		SwarmDiscoverySearch s = new SwarmDiscoverySearch(neo4j);

		return s;

	}

	private SwarmToken getJoinTokensFromNeo4j(String name, SwarmNodeType type) {
		JsonNode n = neo4j.execCypher(
				"match (a:DockerSwarm) where a.name={id} or a.tridentClusterId={id} or a.swarmClusterId={id} return a",
				"id", name).blockingFirst(null);
		if (n == null) {
			throw new NotFoundException("DockerSwarm", name);
		}
		String managerAddress = n.path("managerAddress").asText();
		String managerJoinToken = n.path("managerJoinToken").asText();
		String workerJoinToken = n.path("workerJoinToken").asText();

		
		if (Strings.isNullOrEmpty(managerAddress) || Strings.isNullOrEmpty(managerJoinToken) || Strings.isNullOrEmpty(workerJoinToken)) {
			throw new TridentException("Could not obtain join tokens for: " + name);
		}
		managerJoinToken = cryptoService.decryptString(managerJoinToken);
		workerJoinToken = cryptoService.decryptString(workerJoinToken);

		if (!managerAddress.contains(":")) {
			managerAddress=managerAddress+":2377";
		}
		final String wjt = workerJoinToken;
		final String mjt = managerJoinToken;
		final String ma = managerAddress;
		
		return new SwarmToken(type, type==SwarmNodeType.MANAGER ? managerJoinToken : workerJoinToken, managerAddress);
		
	}

	public SwarmToken getJoinToken(String name, SwarmNodeType type) {

		Swarm swarm = null;
		JsonNode r = null;
		JsonNode info = null;
		try {
			swarm = getSwarm(name);
			r = swarm.getManagerWebTarget().path("/swarm").request(MediaType.APPLICATION_JSON).get(JsonNode.class);
			info = swarm.getInfo();
		} catch (RuntimeException e) {
			return getJoinTokensFromNeo4j(name,type);
		}

		swarm = getSwarm(name);

		String workerJoinToken = r.path("JoinTokens").path("Worker").asText();
		String managerJoinToken = r.path("JoinTokens").path("Manager").asText();

		List<String> addressList = Lists.newArrayList();
		info.path("Swarm").path("RemoteManagers").forEach(it -> {
			addressList.add(it.path("Addr").asText());
		});
		SwarmToken st = new SwarmToken(type, type==SwarmNodeType.MANAGER ? managerJoinToken : workerJoinToken, addressList.get(0));
		
		return st;

	}

}
