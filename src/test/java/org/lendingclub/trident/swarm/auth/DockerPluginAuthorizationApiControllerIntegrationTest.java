package org.lendingclub.trident.swarm.auth;

import java.io.IOException;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import springfox.documentation.spring.web.json.Json;

public class DockerPluginAuthorizationApiControllerIntegrationTest extends TridentIntegrationTest {

	@Autowired
	DockerPluginAuthorizationApiController controller;

	@Autowired
	DockerPluginAuthorizationManager dockerAuthManager;

	List<DockerPluginAuthorizationVoter> preservedState = Lists.newArrayList();

	@Before
	public void preserveState() {
		preservedState.clear();
		preservedState.addAll(dockerAuthManager.getVoters());
	}

	@Before
	public void restoreState() {
		dockerAuthManager.getVoters().clear();
		dockerAuthManager.getVoters().addAll(preservedState);

	}

	@Test
	public void testIt() {
		Assertions.assertThat(controller).isNotNull();

	
	}

	@Test
	public void testDefault() throws IOException {
		
		JsonNode n = JsonUtil.createObjectNode();
		JsonNode response = controller.authorizeRequest(n, new MockHttpServletRequest()).getBody();
		


		Assertions.assertThat(response.path("Allow").asBoolean()).isTrue();

	}

	@Test
	public void testDenyRule() throws IOException {
		JsonNode n = JsonUtil.createObjectNode();
		
		
		dockerAuthManager.getVoters().add(new DockerPluginAuthorizationVoter() {
			
			@Override
			public void authorize(DockerPluginAuthorizationContext ctx) {
			ctx.deny("deny all by default");
				
			}
		});
		JsonNode response = controller.authorizeRequest(n, new MockHttpServletRequest()).getBody();
		
		Assertions.assertThat(response.path("Allow").asBoolean()).isFalse();
		Assertions.assertThat(response.path("Msg").asText()).isEqualTo("deny all by default");
		
	}
}
