package org.lendingclub.trident.haproxy.swarm;

import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.haproxy.HAProxyDiscoveryController;
import org.lendingclub.trident.haproxy.HAProxyHostDiscoveryContext;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.platform.AppClusterManager;
import org.lendingclub.trident.swarm.platform.BlueGreenState;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;

public class SwarmHostDiscoveryDecoratorIntegrationTest extends TridentIntegrationTest {

	static String env = "junit-" + System.currentTimeMillis();
	@Autowired
	AppClusterManager platform;

	@Autowired
	SwarmClusterManager swarmManager;

	@Autowired 
	SwarmHostDiscoveryInterceptor discovery;
	
	@Autowired
	NeoRxClient neo4j;

	@Autowired HAProxyDiscoveryController haProxyDiscoveryController;
	
	Logger logger = LoggerFactory.getLogger(getClass());
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
	public void testCreateCluster() {

		String appCluster = platform.createClusterCommand().withAppId("junit").withEnvironment(env)
				.withSwarm("junit").withRegion("us-east-1").execute().getAppClusterId().get();

		JsonNode n = getNeoRxClient().execCypher("match (a:AppCluster {appClusterId:{id}}) return a", "id", appCluster)
				.blockingFirst();
		JsonUtil.logInfo("junit", n);

		Assertions.assertThat(n.path("environment").asText()).isEqualTo(env);
		Assertions.assertThat(n.path("appId").asText()).isEqualTo("junit");
		Assertions.assertThat(n.path("appClusterId").asText()).isEqualTo(appCluster);
		Assertions.assertThat(n.path("subEnvironment").asText()).isEqualTo("default");
		Assertions.assertThat(n.path("region").asText()).isEqualTo("us-east-1");
		Assertions.assertThat(n.path("createTs").asLong()).isCloseTo(System.currentTimeMillis(),
				Offset.offset(TimeUnit.SECONDS.toMillis(15)));
	}

	@Test
	@Ignore
	public void testIt() {

		/// This will spin up real containers for now....need to improve mock data capability
		Assume.assumeTrue(isLocalDockerDaemonAvailable());

		String env = "junit-env-" + System.currentTimeMillis();
		String appCluster = platform.createClusterCommand().withAppId("junit").withEnvironment(env)
				.withRegion("us-west-2").execute().getAppClusterId().get();

		Assertions.assertThat(BlueGreenState.DARK.toString()).isEqualTo("dark");
		
		String id = platform.deployCommand().withImage("nginx:latest").withBlueGreenState(BlueGreenState.LIVE)
				.withAppClusterId(appCluster).withSwarm("local").withPort(8080).withPath("/foo").execute().getSwarmServiceId().get();

		try {
			// wait for the tasks...again, this test would be better with mock data injected into neo4j
			Thread.sleep(10000);
		}
		catch (Exception e){}
		
		System.out.println(neo4j.execCypherAsList("match (a:DockerService {serviceId:{serviceId}})--(x:DockerTask)--(h:DockerHost) return h","serviceId",id));
		swarmManager.getSwarm("local").getSwarmScanner().scan();
		
		neo4j.execCypher("match (a:DockerTask)  return a","id",id).forEach(it->{
			JsonUtil.logInfo("", it);
		});
		
		HAProxyHostDiscoveryContext ctx = new HAProxyHostDiscoveryContext();
		ctx.withEnvironment(env).withAppId("junit").withSubEnvironment("default").withServiceZone("us-west-2").withServiceGroup("services");
		
		discovery.accept(ctx);
		
		System.out.println(ctx.getConfig());
	}

}
