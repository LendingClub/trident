package org.lendingclub.trident.haproxy.swarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.haproxy.HAProxyDiscoveryController;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.TestServiceBuilder;
import org.lendingclub.trident.swarm.platform.AppClusterManager;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

public class HAProxyDiscoveryControllerIntegrationTest extends TridentIntegrationTest {


	static String env = "junit-" + System.currentTimeMillis();
	@Autowired AppClusterManager platform;

	@Autowired SwarmClusterManager swarmManager;

	@Autowired
	SwarmHostDiscoveryInterceptor discovery;

	@Autowired NeoRxClient neo4j;

	@Autowired HAProxyDiscoveryController haProxyDiscoveryController;

	Logger logger = LoggerFactory.getLogger(HAProxyDiscoveryControllerIntegrationTest.class);


	@Test
	@Ignore
	public void testHAProxyHostsInfoSchema() throws IOException {

		String service = "junit-service-"+System.currentTimeMillis();
		String environment = "junit-env-"+System.currentTimeMillis();
		String subEnvironment = "junit-subenv-"+System.currentTimeMillis();
		String path = "/";
		String appId = "junit-app-"+System.currentTimeMillis();
		String serviceGroup = "junit-servicegroup-"+System.currentTimeMillis();
		String serviceNode = "junit-servicenode-"+System.currentTimeMillis();
		String region = "junit-region-"+System.currentTimeMillis();

		new TestServiceBuilder(neo4j)
				.addService(service)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withPaths(path)
				.withAppId(appId)
				.withPort(8080)
				.withServiceGroup(serviceGroup)
				.addServiceTask()
				.addServiceTask();

		ResponseEntity<String> hostInfoResult = haProxyDiscoveryController.getHostInfo(
				appId,
				serviceGroup,
				serviceNode,
				environment,
				subEnvironment,
				region
		);

		JsonNode hostInfoResultAsJson = JsonUtil.getObjectMapper().readTree( hostInfoResult.getBody() );

		Assertions.assertThat(hostInfoResultAsJson.path("stickySessions").isBoolean());

		logger.info("hostinforesultasjson: {}",
				JsonUtil.getObjectMapper()
						.writerWithDefaultPrettyPrinter()
						.writeValueAsString(hostInfoResultAsJson));

		Assertions.assertThat(hostInfoResultAsJson.path("hosts").isArray());

		ArrayNode hostsArray = (ArrayNode) hostInfoResultAsJson.path("hosts");

		Assertions.assertThat(hostsArray.size()).isEqualTo(2);

		hostsArray.forEach(hostInfo -> {
			Assertions.assertThat(hostInfo.path("host").asText("")).isNotEmpty();
			Assertions.assertThat(hostInfo.path("port").asInt(0)).isNotEqualTo(0);
			Assertions.assertThat(hostInfo.path("priority").asInt(-1)).isBetween(0, 256);
		});

	}

	@Test
	public void testBlueGreenPriorityLive() throws IOException {

		String service = "junit-service-"+System.currentTimeMillis();
		String environment = "junit-env-"+System.currentTimeMillis();
		String subEnvironment = "junit-subenv-"+System.currentTimeMillis();
		String path = "/";
		String appId = "junit-app-"+System.currentTimeMillis();
		String serviceGroup = "junit-servicegroup-"+System.currentTimeMillis();
		String serviceNode = "junit-servicenode-"+System.currentTimeMillis();
		String region = "junit-region-"+System.currentTimeMillis();


		new TestServiceBuilder(neo4j)
				.addService(service)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withPaths(path)
				.withAppId(appId)
				.withPort(8080)
				.withServiceGroup(serviceGroup)
				.addLabel("tsdBlueGreenState", "live")
				.addServiceTask();


		ResponseEntity<String> hostInfoResult = haProxyDiscoveryController.getHostInfo(
				appId,
				serviceGroup,
				serviceNode,
				environment,
				subEnvironment,
				region
		);

		logger.info("service tasks {}",
				neo4j.execCypherAsList(
						"match(e: DockerService {name: {name}})--(d: DockerTask) return d",
						"name", service).size());

		JsonNode hostInfoResultAsJson = JsonUtil.getObjectMapper().readTree( hostInfoResult.getBody() );

		logger.info("hostinforesultasjson: {}", JsonUtil.prettyFormat(hostInfoResultAsJson));

		ArrayNode hostsArray = (ArrayNode) hostInfoResultAsJson.path("hosts");

		hostsArray.forEach( hostInfo -> {
			Assertions.assertThat(hostInfo.path("priority").asInt()).isEqualTo(256);
		});

	}

	@Test
	public void testBlueGreenPriorityDark() throws IOException {

		String service = "junit-service-"+System.currentTimeMillis();
		String environment = "junit-env-"+System.currentTimeMillis();
		String subEnvironment = "junit-subenv-"+System.currentTimeMillis();
		String path = "/";
		String appId = "junit-app-"+System.currentTimeMillis();
		String serviceGroup = "junit-servicegroup-"+System.currentTimeMillis();
		String serviceNode = "junit-servicenode-"+System.currentTimeMillis();
		String region = "junit-region-"+System.currentTimeMillis();

		new TestServiceBuilder(neo4j)
				.addService(service)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withPaths(path)
				.withAppId(appId)
				.withPort(8080)
				.withServiceGroup(serviceGroup)
				.addLabel("tsdBlueGreenState", "dark")
				.addServiceTask();


		ResponseEntity<String> hostInfoResult = haProxyDiscoveryController.getHostInfo(
				appId,
				serviceGroup,
				serviceNode,
				environment,
				subEnvironment,
				region
		);

		JsonNode hostInfoResultAsJson = JsonUtil.getObjectMapper().readTree( hostInfoResult.getBody() );

		ArrayNode hostsArray = (ArrayNode) hostInfoResultAsJson.path("hosts");

		hostsArray.forEach( hostInfo -> {
			Assertions.assertThat(hostInfo.path("priority").asInt()).isEqualTo(0);
		});

	}


	@Test
	@Ignore
	public void testBlueGreenPriorityDraining() throws IOException {

		String service = "junit-service-"+System.currentTimeMillis();
		String environment = "junit-env-"+System.currentTimeMillis();
		String subEnvironment = "junit-subenv-"+System.currentTimeMillis();
		String path = "/";
		String appId = "junit-app-"+System.currentTimeMillis();
		String serviceGroup = "junit-servicegroup-"+System.currentTimeMillis();
		String serviceNode = "junit-servicenode-"+System.currentTimeMillis();
		String region = "junit-region-"+System.currentTimeMillis();

		TestServiceBuilder testServiceBuilder = new TestServiceBuilder(neo4j)
				.addService(service)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withPaths(path)
				.withAppId(appId)
				.withPort(8080)
				.withServiceGroup(serviceGroup)
				.addLabel("tsdBlueGreenState", "drain")
				.addServiceTask();


		ResponseEntity<String> hostInfoResult = haProxyDiscoveryController.getHostInfo(
				appId,
				serviceGroup,
				serviceNode,
				environment,
				subEnvironment,
				region
		);

		JsonNode hostInfoResultAsJson = JsonUtil.getObjectMapper().readTree( hostInfoResult.getBody() );

		ArrayNode hostsArray = (ArrayNode) hostInfoResultAsJson.path("hosts");

		hostsArray.forEach( hostInfo -> {
			Assertions.assertThat(hostInfo.path("priority").asInt()).isEqualTo(0);
		});

	}
}
