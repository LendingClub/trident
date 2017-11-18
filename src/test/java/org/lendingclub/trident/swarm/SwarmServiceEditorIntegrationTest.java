package org.lendingclub.trident.swarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.platform.AppClusterManager;
import org.lendingclub.trident.swarm.platform.BlueGreenState;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

public class SwarmServiceEditorIntegrationTest extends TridentIntegrationTest {

	Logger logger = LoggerFactory.getLogger(SwarmServiceEditor.class);

	static String env = "junit-" + System.currentTimeMillis();

	@Autowired
	AppClusterManager platform;

	@Autowired
	SwarmClusterManager swarmManager;

	@Autowired NeoRxClient neo4j;


	@After
	public void cleanupTestData() {
		if (isIntegrationTestEnabled()) {
			getNeoRxClient().execCypher("match (a:AppCluster) where  a.environment=~'junit.*' detach delete a");

			if (isLocalDockerDaemonAvailable()) {
				swarmManager.getSwarm("local").getManagerWebTarget().path("services").request().get(JsonNode.class)
						.forEach(it -> {
							try {
								String name = it.path("Spec").path("Name").asText();
								if (name.contains("trident") && name.contains("-junit-")) {
									swarmManager.getSwarm("local").getManagerWebTarget().path("services")
											.path(it.path("ID").asText()).request().delete();
								}
							} catch (Exception e) {
								logger.warn("problem cleaning service", e);
							}
						});
			}
		}
	}


	@Test
	public void testIt() throws InterruptedException {

		Assume.assumeTrue(isLocalDockerDaemonAvailable());

		String env = "junit-env-" + System.currentTimeMillis();
		String appCluster = platform.createClusterCommand().withAppId("junit").withEnvironment(env)
				.withRegion("us-west-2").withSwarm("local").execute().getAppClusterId().get();

		Assertions.assertThat(BlueGreenState.DARK.toString()).isEqualTo("dark");

		String id = platform.deployCommand().withImage("nginx:latest").withBlueGreenState(BlueGreenState.DARK)
				.withAppClusterId(appCluster).withSwarm("local").withPort(8080).withPath("/foo").execute().getSwarmServiceId().get();

		swarmManager.getSwarm("local").getSwarmScanner().scan();

		SwarmServiceEditor swarmServiceEditor = new SwarmServiceEditor();

		ObjectNode configBeforeEdit = swarmServiceEditor.withSwarmId("local").withServiceId(id).getConfig();

		logger.info("config before edit {}", JsonUtil.prettyFormat( configBeforeEdit ));

		swarmServiceEditor.withServiceId(id)
		.withReplicaCount(2)
		.withAddLabel("tsdBlueGreenState", "live")
				.execute();

		TimeUnit.SECONDS.sleep(15);
		ObjectNode configAfterEdit = swarmServiceEditor.withServiceId(id).getConfig();

		Assertions.assertThat(configAfterEdit.get("Spec").get("Mode").get("Replicated").get("Replicas").asInt()).isEqualTo(2);
		Assertions.assertThat(configAfterEdit.get("Spec").get("Labels").get("tsdBlueGreenState").asText().equals("live"));
		logger.info("config after edit {}", JsonUtil.prettyFormat( configAfterEdit));

		//now test changing replicas using convenience method
		TimeUnit.SECONDS.sleep(15);
		swarmServiceEditor.withServiceId(id).withReplicaCount(3).execute();
		configAfterEdit = swarmServiceEditor.withServiceId(id).getConfig();
		Assertions.assertThat(configAfterEdit.get("Spec").get("Mode").get("Replicated").get("Replicas").asInt()).isEqualTo(3);

		//now test adding label using convenience method
		swarmServiceEditor.withServiceId(id).withAddLabel("foo", "bar").execute();
		configAfterEdit = swarmServiceEditor.withServiceId(id).getConfig();
		Assertions.assertThat(configAfterEdit.get("Spec").get("Labels").get("foo").asText().equals("bar"));

		//now test removing label using convenience
		swarmServiceEditor.withServiceId(id).withRemoveLabel("foo").execute();
		configAfterEdit = swarmServiceEditor.withServiceId(id).getConfig();
		Assertions.assertThat(configAfterEdit.get("Spec").get("Labels").get("foo")).isNull();
	}

}
