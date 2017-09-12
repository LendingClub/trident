package org.lendingclub.trident.envoy.swarm;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.envoy.EnvoyClusterDiscoveryContext;
import org.lendingclub.trident.envoy.EnvoyServiceDiscoveryContext;
import org.lendingclub.trident.envoy.swarm.SwarmClusterDiscoveryDecorator;
import org.springframework.beans.factory.annotation.Autowired;

public class SwarmClusterDiscoveryDecoratorTest extends TridentIntegrationTest {

	@Autowired
	SwarmClusterDiscoveryDecorator decorator;
	
	@Test
	public void testIt() {
		Assertions.assertThat(decorator).isNotNull();
		
		EnvoyClusterDiscoveryContext ctx = new EnvoyClusterDiscoveryContext().withEnvironment("demo").withSubEnvironment("default").withServiceZone("uw2");
		
		decorator.decorate(ctx);
		
		System.out.println(ctx.getConfig());
	}
	
	@Test
	public void testShorten() {
		Assertions.assertThat(SwarmClusterDiscoveryDecorator.shortenClusterName("foo")).isEqualTo("foo");
		Assertions.assertThat(SwarmClusterDiscoveryDecorator.shortenClusterName("1234567890123456789012345678901234567890123456789012345678901234567890")).isEqualTo("1234567890123456789-610e973d05145fd4c3e6c97ff349907fdc8ec4b7").hasSize(60);
		Assertions.assertThat(SwarmClusterDiscoveryDecorator.shortenClusterName("12345678901234567890123456789012345678901234567890123456789012345678901")).isEqualTo("1234567890123456789-1e184d8af92a024fac77a4f81e4d48692784cba3").hasSize(60);

	}
	
}
