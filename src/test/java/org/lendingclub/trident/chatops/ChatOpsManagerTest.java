package org.lendingclub.trident.chatops;

import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

public class ChatOpsManagerTest extends TridentIntegrationTest {

	@Autowired
	ChatOpsManager chatops;
	
	@Test
	public void testIt() throws Exception {
		
		// this may not do anything, but it should not fail
		chatops.newMessage().withMessage("junit").send();
		chatops.newMessage().withMessage("junit").withRoom("some bogus room").send();
	
	}
}
