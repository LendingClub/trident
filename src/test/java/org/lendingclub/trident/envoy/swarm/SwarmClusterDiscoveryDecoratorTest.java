package org.lendingclub.trident.envoy.swarm;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.envoy.EnvoyClusterDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyDiscoveryController;
import org.lendingclub.trident.envoy.EnvoyManager;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;
import org.lendingclub.trident.swarm.TestServiceBuilder;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class SwarmClusterDiscoveryDecoratorTest extends TridentIntegrationTest {

	@Autowired
	SwarmClusterDiscoveryInterceptor decorator;

	@Autowired EnvoyManager envoyManager;

	@Autowired NeoRxClient neoRxClient;

	@Autowired SwarmClusterManager swarmClusterManager;

	@Autowired EnvoyDiscoveryController envoyClusterDiscoveryController;

	Logger logger = LoggerFactory.getLogger(SwarmClusterDiscoveryDecoratorTest.class);
	
	@Test
	public void testIt() {
		Assertions.assertThat(decorator).isNotNull();

		EnvoyClusterDiscoveryContext ctx = new EnvoyClusterDiscoveryContext().withEnvironment("demo").withServiceCluster("dummy").withSubEnvironment("default").withServiceZone("uw2");

		decorator.accept(ctx);
		
		System.out.println(ctx.getConfig());
	}

	@Test
	public void testShorten() {
		Assertions.assertThat(SwarmClusterDiscoveryInterceptor.shortenClusterName("foo")).isEqualTo("foo");
		Assertions.assertThat(SwarmClusterDiscoveryInterceptor.shortenClusterName("1234567890123456789012345678901234567890123456789012345678901234567890")).isEqualTo("45678901234567890123456789012345678901234567890-C479EF852HFT").hasSize(60);
		Assertions.assertThat(SwarmClusterDiscoveryInterceptor.shortenClusterName("12345678901234567890123456789012345678901234567890123456789012345678901")).isEqualTo("56789012345678901234567890123456789012345678901-3OC4R2NP5814").hasSize(60);

	}

	@Test
	public void testClusterDiscovery() {

		String appId = "myappId";
		String environment = "test";
		String subEnvironment = "default";
		String serviceGroup = "junit";
		String serviceName = "test-sw";

		int port = 8080;

		new TestServiceBuilder(neoRxClient)
				.addService(serviceName)
				.withAppId(appId)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withPort(port)
				.withServiceGroup(serviceGroup);

		List<SwarmDiscoverySearch.Service> services = new SwarmDiscoverySearch(neoRxClient)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withServiceGroup(serviceGroup)
				.withRegion("us-west-2")
				.search();

		services.forEach(service -> {
			JsonUtil.logInfo(logger, "search-integration-test",
					service.getData());
		});

		SwarmDiscoverySearch.Service testService = null;
		for(SwarmDiscoverySearch.Service service: services) {
			if(service.getAppId().isPresent() &&
					service.getAppId().get().equals(appId) &&
					service.getEnvironmentSelector().equals(environment) &&
					service.getSubEnvironmentSelector().equals(subEnvironment) &&
					service.getServiceGroupSelector().equals(serviceGroup) ) {

				testService = service;

			}
		}

		Assertions.assertThat(testService).isNotNull();
		Assertions.assertThat(testService.getData().path("swarmName").asText()).startsWith("junit");
		Assertions.assertThat(testService.getData().path("s").path("label_tsdPort").asText()).isEqualTo(""+port);
		Assertions.assertThat(testService.getData().path("s").path("label_tsdEnv").asText()).isEqualTo(environment);
		Assertions.assertThat(testService.getData().path("s").path("label_tsdSubEnv").asText()).isEqualTo(subEnvironment);
	
		EnvoyClusterDiscoveryContext ctx = new EnvoyClusterDiscoveryContext()
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withServiceCluster("foo")
				.withServiceZone("foo");
		decorator.accept(ctx);
		Assertions.assertThat(ctx.getConfig()
						.path("clusters").isArray());

		Assertions.assertThat(testService.getData().path("s").path("name").asText())
				.isEqualTo(serviceName);

		JsonUtil.logInfo(logger, "cluster-discovery test", ctx.getConfig());
	}

	//TODO: unignore this test when we come up with a way to make the cluster name in the config be less than or equal to 60 characters
	@Test
	public void testClusterConfigNameLength() {
		String zone = this.getClass().getName();
		String appId = "myappId";
		String environment = "cluster-discovery-test-environment";
		String subEnvironment = "not-default-2";

		String port = "8080";

		new TestServiceBuilder(neoRxClient)
				.addService("junit-test-swarm-2")
				.withAppId(appId)
				.withEnvironment(environment)
				.withServiceGroup("us-west-2")
				.withSubEnvironment(subEnvironment);

		EnvoyClusterDiscoveryContext ctx = new EnvoyClusterDiscoveryContext()
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withServiceCluster("us-west-2")
				.withServiceZone("us-west-2");

		decorator.accept(ctx);
		Assertions.assertThat(ctx.getConfig()
				.path("clusters").isArray());

		ArrayNode allClusters = (ArrayNode) ctx.getConfig().path("clusters");

		Assertions.assertThat(allClusters.get(0).path("name").asText().length()).isLessThanOrEqualTo(60);

	}

	@Test
	public void testConfigHasRequiredFieldsWithCorrectTypes() {
		String zone = this.getClass().getName();
		String appId = Thread.currentThread().getStackTrace()[1].getMethodName();
		String environment = "asdf";
		String subEnvironment = "not-default";
		String serviceGroup = "junit-t";
		String serviceId = "junit-test-swarm";
		int port = 8080;
		MockHttpServletRequest servletRequest = new MockHttpServletRequest();

		new TestServiceBuilder(neoRxClient)
				.addService(serviceId)
				.addLabel("tsdZone", zone)
				.addLabel("tsdRegion", "us-west-2")
				.withPort(port)
				.withAppId(appId)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withServiceGroup(serviceGroup);

		List<SwarmDiscoverySearch.Service> services = new SwarmDiscoverySearch(neoRxClient)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withAppId(appId)
				.withServiceGroup(serviceGroup)
				.withRegion("us-west-2")
				.search();

		services.forEach(service -> {
			JsonUtil.logInfo(logger, "search-integration-test",
					service.getData());
		});

		List<String> serviceIds = Lists.newArrayList();
		serviceIds.add(serviceId);

		SwarmIntegrationTestUtils
				.assertSearchResults("us-west-2", serviceGroup, environment, subEnvironment, serviceIds, neoRxClient);


		EnvoyClusterDiscoveryContext ctx = new EnvoyClusterDiscoveryContext()
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment)
				.withServiceCluster(serviceGroup)
				.withServiceZone("us-west-2");

		decorator.accept(ctx);

		JsonUtil.logInfo(logger, appId, ctx.getConfig());

		Assertions.assertThat(ctx.getConfig().path("clusters").isArray());

		ArrayNode allClusters = (ArrayNode) ctx.getConfig().path("clusters");

		Assertions.assertThat(allClusters.size()).isEqualTo(1);

		Assertions.assertThat(allClusters.get(0).has("name"));

		Assertions.assertThat(allClusters.get(0).path("name").isTextual());

		Assertions.assertThat(allClusters.get(0).has("type"));

		Assertions.assertThat(allClusters.get(0).path("type").isTextual());

		Assertions.assertThat(allClusters.get(0).has("connect_timeout_ms"));

		Assertions.assertThat(allClusters.get(0).path("connect_timeout_ms").isInt());

		Assertions.assertThat(allClusters.get(0).has("lb_type"));

		Assertions.assertThat(allClusters.get(0).path("lb_type").isTextual());

//		if service discovery type is sds, then service_name is required
		if(allClusters.get(0).path("type").asText().equals("sds")) {
			Assertions.assertThat(allClusters.get(0).path("service_name").isTextual());
		}
	}

	@Test
	public void testClusterDiscoveryEndpoint() {

		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setProtocol("https");

		String zone = "cluster-discovery-test-zone";
		String appId = Thread.currentThread().getStackTrace()[1].getMethodName();
		String environment = "cluster-discovery-test-environment";
		String subEnvironment = "default";
		String port = "8080";

		new TestServiceBuilder(neoRxClient)
				.addService("junit-test-swarm")
				.addLabel("tridentPort", port)
				.addLabel("tridentZone", zone)
				.withAppId(appId)
				.withEnvironment(environment)
				.withSubEnvironment(subEnvironment);

		String clusterDiscoveryResult =
				envoyClusterDiscoveryController.clusterDiscovery(request, //"",
						zone+"--"+environment+"--"+subEnvironment+"--"+appId,
						"junit-test-swarm").getBody();

		JsonUtil.logInfo(logger, "cluster-discovery-endpoint-response", clusterDiscoveryResult);


	}
}
