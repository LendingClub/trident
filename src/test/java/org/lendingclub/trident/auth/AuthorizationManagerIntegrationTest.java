package org.lendingclub.trident.auth;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

public class AuthorizationManagerIntegrationTest extends TridentIntegrationTest {

	@Autowired
	AuthorizationManager authorizationManager;
	
	
	@Test
	public void testIt() {
		Assertions.assertThat(authorizationManager).isNotNull();
		Assertions.assertThat(authorizationManager.authorize("user", null, "test", null).isAuthorized()).isTrue();
	}
}
