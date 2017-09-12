package org.lendingclub.trident.event;

import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;

public class EventSystemTest extends TridentIntegrationTest {

	@Test
	public void testIt() throws InterruptedException {
		
		
		
		new TridentEvent().withAttribute("foo", "bar").send();
		new TridentEvent().withAttribute("fizze", "bar").send();
		
		Thread.sleep(5000);
	}
}
