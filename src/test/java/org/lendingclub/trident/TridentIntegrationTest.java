package org.lendingclub.trident;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.config.TridentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@RunWith(SpringJUnit4ClassRunner.class)
@ComponentScan(basePackageClasses = { TridentConfig.class, TridentTestConfig.class })
public abstract class TridentIntegrationTest {

	private Logger logger = LoggerFactory.getLogger(getClass());
	@Autowired
	NeoRxClient neo4j;

	Boolean integrationTestEnabled = null;
	static DockerClient localDockerClient;

	public NeoRxClient getNeoRxClient() {
		return neo4j;
	}

	@Before
	public void checkIt() {
		if (integrationTestEnabled != null) {
			Assume.assumeTrue(integrationTestEnabled);
			return;
		} else {
			integrationTestEnabled = neo4j != null && neo4j.checkConnection();
		}
		Assume.assumeTrue(integrationTestEnabled);

	}

	public boolean isIntegrationTestEnabled() {
		return integrationTestEnabled != null && integrationTestEnabled;
	}

	public JsonNode createDummySwarm() {
		String name = "junit-" + System.currentTimeMillis();
		String tridentClusterId = name;
		String swarmClusterId = "junit-" + UUID.randomUUID().toString();
		return neo4j.execCypher(
				"merge (s:DockerSwarm {name:{name}}) set s.swarmClusterId={swarmClusterId}, s.tridentClusterId={tridentClusterId} return s",
				"swarmClusterId", swarmClusterId, "name", name, "tridentClusterId", tridentClusterId).blockingFirst();

	}

	boolean isTestDataCleanupEnabled() {
		String val = Strings.nullToEmpty(System.getProperty("testDataCleanup",System.getenv("testDataCleanup")));
		if (val.toLowerCase().equals("false")) {
			return false;
		}
		return true;
	}
	@After
	public void cleanupUnitTestData() {
		if (!isTestDataCleanupEnabled()) {
			if (isIntegrationTestEnabled()) {
				Stopwatch sw = Stopwatch.createStarted();
				neo4j.execCypher("match (d:DockerSwarm)--(x) where d.name=~'junit.*' detach delete x");
				neo4j.execCypher("match (d:DockerSwarm) where d.name=~'junit.*' detach delete d");
				neo4j.execCypher("match (d:DockerSwarm) where d.tridentClusterId=~'junit.*' detach delete d");
				neo4j.execCypher("match (d:DockerHost) where d.name=~'junit.*' detach delete d");
				neo4j.execCypher("match (d) where exists(d.junitData) detach delete d");
				if (sw.elapsed(TimeUnit.MILLISECONDS) > 500) {
					logger.info("test data cleanup took {}ms", sw.elapsed(TimeUnit.MILLISECONDS));
				}
			}
		}
	}

	public Optional<DockerClient> getLocalDockerClient() {
		if (isLocalDockerDaemonAvailable()) {
			return Optional.ofNullable(localDockerClient);
		}
		return Optional.empty();
	}

	public synchronized boolean isLocalDockerDaemonAvailable() {
		if (localDockerClient == null) {
			DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
					.withDockerHost("unix:///var/run/docker.sock")

					.build();
			localDockerClient = DockerClientBuilder.getInstance(config).build();
		}
		try {
			localDockerClient.pingCmd().exec();
			return true;
		} catch (Exception e) {
			return false;
		}

	}
}
