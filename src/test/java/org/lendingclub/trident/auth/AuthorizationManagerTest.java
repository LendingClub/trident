package org.lendingclub.trident.auth;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.util.JsonUtil;

import com.google.common.collect.ImmutableSet;

public class AuthorizationManagerTest {

	@Test
	public void testDefault() {

		AuthorizationManager m = new AuthorizationManager();
		AuthorizationResult result = m.authorize("user", null, "test", JsonUtil.createObjectNode());

		Assertions.assertThat(result.isAuthorized()).isTrue();
	}

	@Test
	public void testDeny() {

		AuthorizationManager m = new AuthorizationManager();

		AuthorizationVoter v = new AuthorizationVoter() {

			@Override
			public void vote(AuthorizationContext ctx) {
				ctx.deny("no good");

			}
		};
		m.addVoter(v);
		AuthorizationResult result = m.authorize("user", null, "test", JsonUtil.createObjectNode().put("foo", "bar"));

		Assertions.assertThat(result.isAuthorized()).isFalse();
		Assertions.assertThat(result.getMessage()).isEqualTo("no good");

		m.addVoter(new AuthorizationVoter() {

			@Override
			public void vote(AuthorizationContext ctx) {
				ctx.permit("allow");
			}
		});
		result = m.authorize("user", ImmutableSet.of(), "", JsonUtil.createObjectNode());
		Assertions.assertThat(result.isAuthorized()).isTrue();
		Assertions.assertThat(result.getMessage()).isEqualTo("allow");

	}
	
	@Test
	public void testConditional() {
		AuthorizationManager m = new AuthorizationManager();

		m.addVoter(new AuthorizationVoter() {
			
			@Override
			public void vote(AuthorizationContext ctx) {
				ctx.deny("deny all");
				
			}
		});
		m.addVoter(new AuthorizationVoter() {

			@Override
			public void vote(AuthorizationContext ctx) {
				
				if (ctx.getObjectData().path("fizz").equals("buzz")) {
					ctx.permit("");
				}
				

			}
		});
		Assertions.assertThat(m.authorize("user", null, "abc", JsonUtil.createObjectNode()).isAuthorized()).isFalse();
		Assertions.assertThat(m.authorize("user", null, "abc", JsonUtil.createObjectNode().put("fizz", "buzz")).isAuthorized()).isFalse();
	}
}
