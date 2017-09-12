package org.lendingclub.trident.envoy.swarm;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.envoy.EnvoyServiceDiscoveryContext;
import org.lendingclub.trident.envoy.swarm.SwarmServiceDiscoveryDecorator;
import org.springframework.beans.factory.annotation.Autowired;

public class SwarmServiceDiscoveryDecoratorTest extends TridentIntegrationTest {

	@Autowired
	SwarmServiceDiscoveryDecorator decorator;
	
	@Test
	public void testIt() {
		Assertions.assertThat(decorator).isNotNull();
		
		EnvoyServiceDiscoveryContext ctx = new EnvoyServiceDiscoveryContext().withServiceName("lc-service-json").withEnvironment("demo");
		
		decorator.decorate(ctx);
		
		System.out.println(ctx.getConfig());
	}
}
