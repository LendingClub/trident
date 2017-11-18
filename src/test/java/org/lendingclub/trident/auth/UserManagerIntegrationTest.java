package org.lendingclub.trident.auth;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;

import com.google.common.collect.ImmutableList;

public class UserManagerIntegrationTest extends TridentIntegrationTest {

	@Inject
	UserManager um;
	
	@Test
	public void testIt() {
		Assertions.assertThat(um).isNotNull();
		
		String name = "junit-"+System.currentTimeMillis();
		um.createUser(name,ImmutableList.of("TRIDENT_USER"));
		
		Assertions.assertThat(um.authenticate(name, "hello")).isFalse();
		um.setPassword(name, "hello");
		Assertions.assertThat(um.authenticate(name, "hello")).isTrue();
		
	}
	
	@After
	public void cleanupTestUsers() {
		if (isIntegrationTestEnabled()) {
			getNeoRxClient().execCypher("match (a:TridentUser) where a.username=~'.*' detach delete a");
		}
	}
}
