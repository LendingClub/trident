package org.lendingclub.trident.envoy;

import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

public class EnvoyManagerIntegrationTest extends TridentIntegrationTest {

	
	@Autowired
	EnvoyManager envoyManager;
	
	
	@Test
	public void testIt() {
		
		String name = "junit-"+UUID.randomUUID().toString();
		EnvoyListenerDiscoveryContext ctx = new EnvoyListenerDiscoveryContext()
			
				
				.withServiceGroup("junit-group")
				.withServiceNode(name)
				.withEnvironment("myenv")
				.withSubEnvironment("default")
				.withServiceZone("myregion");
		envoyManager.record(ctx);
		
		JsonNode n = getNeoRxClient().execCypher("match (x:EnvoyInstance {node:{node}}) return x","node",name).blockingFirst();
		
		Assertions.assertThat(n.path("node").asText()).isEqualTo(ctx.getServiceNode().get());
		Assertions.assertThat(n.path("environment").asText()).isEqualTo(ctx.getEnvironment().get());
		Assertions.assertThat(n.path("subEnvironment").asText()).isEqualTo(ctx.getSubEnvironment().get());
		Assertions.assertThat(n.path("region").asText()).isEqualTo(ctx.getServiceZone().get());
		Assertions.assertThat(n.path("serviceGroup").asText()).isEqualTo(ctx.getServiceCluster().get());
	}
	
	@After
	public void cleanupAfter() {
		if (isIntegrationTestEnabled()) {
			getNeoRxClient().execCypher("match (x:EnvoyInstance) where x.serviceGroup=~'junit.*' detach delete x");
		}
	}
}
