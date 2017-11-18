package org.lendingclub.trident.swarm.auth;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DockerPluginAuthorizationManagerIntegrationTest extends TridentIntegrationTest {

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
		Assertions.assertThat(dockerAuthManager).isNotNull();

	
	}

	@Test
	public void testDefault() {
		JsonNode n = JsonUtil.createObjectNode();

		DockerPluginAuthorizationContext ctx = new DockerPluginAuthorizationContext.DockerPluginRequestAuthorizationContext(n);

		Assertions.assertThat(dockerAuthManager.authorize(ctx).path("Allow").asBoolean()).isTrue();

	}

	@Test
	public void testDenyRule() {
		JsonNode n = JsonUtil.createObjectNode();
		
		DockerPluginAuthorizationContext ctx = new DockerPluginAuthorizationContext.DockerPluginRequestAuthorizationContext(n);
		dockerAuthManager.getVoters().add(new DockerPluginAuthorizationVoter() {
			
			@Override
			public void authorize(DockerPluginAuthorizationContext ctx) {
			ctx.deny("deny all by default");
				
			}
		});
		Assertions.assertThat(dockerAuthManager.authorize(ctx).path("Allow").asBoolean()).isFalse();
		Assertions.assertThat(dockerAuthManager.authorize(ctx).path("Msg").asText()).isEqualTo("deny all by default");
		
	}
}
