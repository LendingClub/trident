package org.lendingclub.trident.auth;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;

public class AuthorizationUtilIntegrationTest extends TridentIntegrationTest {

	@Test
	public void testIt() {
		
		// by default auth succeeds
		Assertions.assertThat(AuthorizationUtil.isAuthorized("foo")).isTrue();
		
		Assertions.assertThat(AuthorizationUtil.isAuthorized("foo")).isTrue();
	}
}
