package org.lendingclub.trident.envoy.swarm;

import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryInterceptor;
import org.lendingclub.trident.envoy.swarm.SwarmListenerDiscoveryInterceptor;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class SwarmListenerDiscoveryDecoratorTest extends TridentIntegrationTest {

	@Autowired
	SwarmListenerDiscoveryInterceptor decorator;
	
	@Test
	public void testIt() {
		
		
		EnvoyListenerDiscoveryContext ctx = new EnvoyListenerDiscoveryContext().withEnvironment("demo").withServiceZone("uw2");
		
		decorator.accept(ctx);
		
		JsonUtil.logInfo(getClass(), "hello", ctx.getConfig());
	}

}
