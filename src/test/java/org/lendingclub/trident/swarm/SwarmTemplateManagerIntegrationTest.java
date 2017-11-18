package org.lendingclub.trident.swarm;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.provision.SwarmTemplateManager;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SwarmTemplateManagerIntegrationTest extends TridentIntegrationTest {

	@Autowired
	NeoRxClient neo4j;
	
	@Autowired
	SwarmTemplateManager stm;
	
	@Test
	public void testIt() {
		Assertions.assertThat(stm).isNotNull();
		
		String name = "junit-"+System.currentTimeMillis();
		
		ObjectNode data = JsonUtil.createObjectNode().put("foo", "bar");
		
		stm.saveTemplate(name, data);
		
		JsonNode x = stm.getTemplate(name).get();
		
		boolean found = stm.findTemplates().stream().anyMatch(t->{
		
			return t.path("templateName").asText().equals(name);
		});
		
		Assertions.assertThat(found).isTrue();
	}
	
	@After
	public void cleanupTestData() {
		if (isIntegrationTestEnabled()) {
			neo4j.execCypher("match (x:DockerSwarmTemplate) where x.templateName=~'junit.*' detach delete x");
		}
	}
}
