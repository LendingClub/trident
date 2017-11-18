package org.lendingclub.trident.envoy;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.TestServiceBuilder;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class EnvoyClusterDiscoveryControllerIntegrationTest extends TridentIntegrationTest {

	@Autowired
	EnvoyDiscoveryController cds;

	@Autowired
	NeoRxClient neo4j;

	@Test
	public void testIt() throws Exception {

		new TestServiceBuilder(neo4j).addService("foo").withEnvironment("prod").withPort(8080)
				.withServiceGroup("junit");
		new TestServiceBuilder(neo4j).addService("foo1").withPort(8080).withEnvironment("prod")
				.withSubEnvironment("feature1").withServiceGroup("junit");
		new TestServiceBuilder(neo4j).addService("foo2").withPort(8080).withEnvironment("prod")
				.withSubEnvironment("feature2").withServiceGroup("junit");

		MockHttpServletRequest request = new MockHttpServletRequest();

		JsonNode n = JsonUtil.getObjectMapper()
				.readTree(cds.clusterDiscovery(request, "uw2--prod--default--junit", "x").getBody());
		Assertions.assertThat(n.path("clusters").size()).isGreaterThan(0);
		Assertions.assertThat(n.path("clusters").path(0).path("name").asText())
				.isEqualTo("uw2--prod--default--junit--foo");
		Assertions.assertThat(n.path("clusters").path(0).path("service_name").asText())
				.isEqualTo("uw2--prod--default--junit--foo");
		Assertions.assertThat(n.path("clusters").path(0).path("type").asText()).isEqualTo("sds");
		Assertions.assertThat(n.path("clusters").path(0).path("lb_type").asText()).isEqualTo("round_robin");

		n = JsonUtil.getObjectMapper()
				.readTree(cds.clusterDiscovery(request, "ue1--prod--default--junit", "x").getBody());

		Assertions.assertThat(n.path("clusters").path(0).path("name").asText())
				.isEqualTo("ue1--prod--default--junit--foo");
		Assertions.assertThat(n.path("clusters").path(0).path("service_name").asText())
				.isEqualTo("ue1--prod--default--junit--foo");
		Assertions.assertThat(n.path("clusters").path(0).path("type").asText()).isEqualTo("sds");
		Assertions.assertThat(n.path("clusters").path(0).path("lb_type").asText()).isEqualTo("round_robin");

		// set the service group to something else and we should not get data
		n = JsonUtil.getObjectMapper()
				.readTree(cds.clusterDiscovery(request, "uw2--prod--default--junit2", "x").getBody());
		Assertions.assertThat(n.path("clusters").size()).isEqualTo(0);

		// set the service group to something else and we should not get data
		n = JsonUtil.getObjectMapper()
				.readTree(cds.clusterDiscovery(request, "uw2--prod--feature1--junit", "x").getBody());
		List<String> serviceNames = Lists.newArrayList();
		n.path("clusters").iterator().forEachRemaining(it -> {
			serviceNames.add(it.path("service_name").asText());
		});
		Assertions.assertThat(serviceNames).containsExactlyInAnyOrder("uw2--prod--feature1--junit--foo","uw2--prod--feature1--junit--foo1");
	}
}
