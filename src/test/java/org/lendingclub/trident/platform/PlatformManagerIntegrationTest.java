package org.lendingclub.trident.platform;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.haproxy.HAProxyHostDiscoveryContext;
import org.lendingclub.trident.haproxy.HAProxyManager;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.platform.AppClusterManager;
import org.lendingclub.trident.swarm.platform.BlueGreenState;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import io.netty.handler.codec.http.HttpContentEncoder.Result;

public class PlatformManagerIntegrationTest extends TridentIntegrationTest {

	static String env = "junit-" + System.currentTimeMillis();
	@Autowired
	AppClusterManager platform;

	@Autowired
	SwarmClusterManager swarmManager;

	@Autowired
	NeoRxClient neo4j;

	@Autowired org.lendingclub.trident.haproxy.HAProxyManager HAProxyManager;

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
		Assume.assumeTrue(isLocalDockerDaemonAvailable());

		String appCluster = platform.createClusterCommand().withAppId("junit").withEnvironment(env)
				.withRegion("us-east-1").withSwarm("local").execute().getAppClusterId().get();

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
	public void testIt() {

		Assume.assumeTrue(isLocalDockerDaemonAvailable());

		String serviceGroup = "foo-"+System.currentTimeMillis();
		String env = "junit-env-" + System.currentTimeMillis();
		String appCluster = platform.createClusterCommand().withAppId("junit").withEnvironment(env)
				.withRegion("us-west-2").withSwarm("local").execute().getAppClusterId().get();

		Assertions.assertThat(BlueGreenState.DARK.toString()).isEqualTo("dark");
		
		String id = platform.deployCommand().withImage("nginx:latest").withBlueGreenState(BlueGreenState.DARK)
				.withAppClusterId(appCluster).withSwarm("local").withPort(8080).withPath("/foo").execute().getSwarmServiceId().get();

		swarmManager.getSwarm("local").getSwarmScanner().scan();
		
		JsonNode n = neo4j.execCypher("match (a:DockerService) where a.serviceId={id} return a","id",id).blockingFirst();
		
		JsonUtil.logInfo("", n);
		Assertions.assertThat(n.path("taskImage").asText()).startsWith("nginx:latest@");
		Assertions.assertThat(n.path("label_tsdAppId").asText()).isEqualTo("junit");
		Assertions.assertThat(n.path("label_tsdRegion").asText()).isEqualTo("us-west-2");
		Assertions.assertThat(n.path("label_tsdAppClusterId").asText()).isEqualTo(appCluster);
		Assertions.assertThat(n.path("label_tsdSubEnv").asText()).isEqualTo("default");
		Assertions.assertThat(n.path("label_tsdEnv").asText()).isEqualTo(env);
		Assertions.assertThat(n.path("label_tsdBlueGreenState").asText()).isEqualTo("dark");
		Assertions.assertThat(n.path("serviceId").asText()).isEqualTo(id);
		Assertions.assertThat(n.path("swarmClusterId").asText()).isEqualTo(swarmManager.getSwarm("local").getSwarmClusterId().get());
		Assertions.assertThat(n.path("updateTs").asLong()).isCloseTo(System.currentTimeMillis(), Offset.offset(30000L));
		Assertions.assertThat(id).isNotEmpty();
		
		AtomicInteger count = new AtomicInteger();
		swarmManager.newServiceDiscoverySearch().withEnvironment(env).withRegion("us-west-2").withServiceGroup(serviceGroup).search().forEach(it->{
			// The search above will pick up other garbage
			JsonUtil.logInfo("", it.getData());
			if (it.getData().path("s").path("serviceId").asText().equals(id)) {
				
				Assertions.assertThat(it.getPort().get()).isEqualTo(8080);
				Assertions.assertThat(it.getPaths().get(0)).isEqualTo("/foo");
				count.incrementAndGet();
			}
			
		});
		
		Assertions.assertThat(count.get()).isEqualTo(1);
		
		
		// Now make it dark
		platform.blueGreenCommand().withBlueGreenState(BlueGreenState.LIVE).withSwarmServiceId(id).execute();


		n = neo4j.execCypher("match (a:DockerService) where a.serviceId={id} return a","id",id).blockingFirst();
		Assertions.assertThat(n.path("label_tsdBlueGreenState").asText()).isEqualTo("live");
		JsonUtil.logInfo("", n);
	}

	public int getDarkHosts(JsonNode tridentHaproxyHostConfig) {
		int result = 0;

		for ( JsonNode host: tridentHaproxyHostConfig.get("hosts") ) {
			if( host.path("priority").asInt() == 0 ) result++;
		}
		return result;
	}

	public int getLiveHosts(JsonNode tridentHaproxyHostConfig) {
		int result = 0;

		for ( JsonNode host: tridentHaproxyHostConfig.get("hosts") ) {
			if( host.path("priority").asInt() == 256 ) result++;
		}
		return result;
	}

	@Test
	public void testHAProxyClusterHostDiscovery() throws InterruptedException {
		String env = "junit-env-" + System.currentTimeMillis();
		String appId = "junit";
		String region = "us-west-2";
		String swarm = "local";
		String serviceGroup = "www";
		String subEnvironment = "default";

		String appCluster = platform.createClusterCommand().withAppId(appId).withEnvironment(env).withSubEnvironment(subEnvironment)
				.withRegion(region).withSwarm(swarm).withServiceGroup(serviceGroup).execute().getAppClusterId().get();

		String id = platform.deployCommand().withImage("nginx:latest").withBlueGreenState(BlueGreenState.LIVE)
				.withAppClusterId(appCluster).withSwarm(swarm).withPort(8080).withEnvironment(env).withSubEnvironment(subEnvironment)
				.withServiceGroup(serviceGroup).withPath("/foo").execute().getSwarmServiceId().get();

		String id2 = platform.deployCommand().withImage("nginx:latest").withBlueGreenState(BlueGreenState.DARK)
				.withAppClusterId(appCluster).withSwarm(swarm).withPort(8080).withEnvironment(env).withSubEnvironment(subEnvironment)
				.withServiceGroup(serviceGroup).withPath("/foo").execute().getSwarmServiceId().get();

		logger.info("id of first created service {} id of second created service {}", id, id2);

		swarmManager.getSwarm("local").getSwarmScanner().scan();

		swarmManager.newServiceDiscoverySearch().withAppId(appId).withRegion(region).withServiceGroup(serviceGroup)
				.withEnvironment(env).withSubEnvironment(subEnvironment).search().forEach(
						service -> {
							logger.info("discovered service {}", service.getServiceId());
						});

		TimeUnit.SECONDS.sleep(30);

		Assertions.assertThat(neo4j.execCypherAsList("match (a:DockerService "
								+ "{serviceId:{serviceId}})--(t:DockerTask)--(h:DockerHost) where t.state='running' "
								+ "return h.addr as addr","serviceId", id2).size()).isEqualTo(1);

		Assertions.assertThat(neo4j.execCypherAsList("match (a:DockerService "
								+ "{serviceId:{serviceId}})--(t:DockerTask)--(h:DockerHost) where t.state='running' "
								+ "return h.addr as addr","serviceId", id).size()).isEqualTo(1);

		HAProxyHostDiscoveryContext ctx = new HAProxyHostDiscoveryContext();

		ctx = ctx.withAppId(appId);

		ctx = ctx.withServiceCluster(serviceGroup);

		ctx = ctx.withServiceNode("asdfasdf");

		ctx = ctx.withEnvironment(env);

		ctx = ctx.withSubEnvironment(subEnvironment);

		ctx = ctx.withRegion(region);

		HAProxyManager.decorate(ctx);

		logger.info("haproxy hosts config {}", JsonUtil.prettyFormat(ctx.getConfig()));

		Assertions.assertThat(ctx.getConfig().get("hosts").size()).isEqualTo(2);

		Assertions.assertThat(getDarkHosts(ctx.getConfig())).isEqualTo(1);

		Assertions.assertThat(getLiveHosts(ctx.getConfig())).isEqualTo(1);

		platform.deployCommand().withImage("nginx:latest").withBlueGreenState(BlueGreenState.LIVE)
				.withAppClusterId(appCluster).withSwarm(swarm).withPort(8080).withEnvironment(env).withSubEnvironment(subEnvironment)
				.withServiceGroup(serviceGroup).withPath("/foo").execute().getSwarmServiceId().get();

		HAProxyManager.decorate(ctx);

		Assertions.assertThat(getLiveHosts(ctx.getConfig())).isEqualTo(2);

		appCluster = platform.createClusterCommand().withAppId(appId).withEnvironment(env).withSubEnvironment("hasingh")
				.withRegion(region).withSwarm(swarm).withServiceGroup(serviceGroup).execute().getAppClusterId().get();

		platform.deployCommand().withImage("nginx:latest").withBlueGreenState(BlueGreenState.LIVE)
				.withAppClusterId(appCluster).withSwarm(swarm).withPort(8080).withEnvironment(env).withSubEnvironment("hasingh")
				.withServiceGroup(serviceGroup).withPath("/foo").execute().getSwarmServiceId().get();

		HAProxyManager.decorate(ctx);

		Assertions.assertThat(getLiveHosts(ctx.getConfig())).isEqualTo(2);
	}
}
