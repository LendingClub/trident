package org.lendingclub.trident.envoy.swarm;

import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryDecorator;
import org.lendingclub.trident.envoy.swarm.SwarmListenerDiscoveryDecorator;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class SwarmListenerDiscoveryDecoratorTest extends TridentIntegrationTest {

	@Autowired
	SwarmListenerDiscoveryDecorator decorator;
	
	@Test
	public void testIt() {
		
		
		EnvoyListenerDiscoveryContext ctx = new EnvoyListenerDiscoveryContext().withEnvironment("demo").withServiceZone("uw2");
		
		decorator.decorate(ctx);
		
		JsonUtil.logInfo(getClass(), "hello", ctx.getConfig());
	}

}
