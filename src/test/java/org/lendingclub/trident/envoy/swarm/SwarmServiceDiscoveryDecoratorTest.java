package org.lendingclub.trident.envoy.swarm;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.envoy.EnvoyServiceDiscoveryContext;
import org.lendingclub.trident.envoy.swarm.SwarmServiceDiscoveryInterceptor;
import org.springframework.beans.factory.annotation.Autowired;

public class SwarmServiceDiscoveryDecoratorTest extends TridentIntegrationTest {

	@Autowired
	SwarmServiceDiscoveryInterceptor decorator;
	
	@Test
	public void testIt() {
		Assertions.assertThat(decorator).isNotNull();
		
		EnvoyServiceDiscoveryContext ctx = new EnvoyServiceDiscoveryContext().withServiceName("lc-service-json").withEnvironment("demo").withServiceCluster("dummy").withServiceZone("dummy");
		
		decorator.accept(ctx);
		
		System.out.println(ctx.getConfig());
	}
}
