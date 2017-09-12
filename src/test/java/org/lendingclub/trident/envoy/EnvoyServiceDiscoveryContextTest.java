package org.lendingclub.trident.envoy;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class EnvoyServiceDiscoveryContextTest {

	
	@Test
	public void testIt() {
		EnvoyServiceDiscoveryContext ctx = new EnvoyServiceDiscoveryContext();
		
		ctx.addHost("1.2.3.4", 8080);
		ctx.addHost("5.6.7.8", 9090).withCanary(true).withWeight(40);
		
		System.out.println(ctx.getConfig());
		
		Assertions.assertThat(ctx.getConfig().path("hosts").get(0).path("ip_address").asText()).isEqualTo("1.2.3.4");
		Assertions.assertThat(ctx.getConfig().path("hosts").get(0).path("port").asInt()).isEqualTo(8080);
		Assertions.assertThat(ctx.getConfig().path("hosts").get(1).path("ip_address").asText()).isEqualTo("5.6.7.8");
		Assertions.assertThat(ctx.getConfig().path("hosts").get(1).path("port").asInt()).isEqualTo(9090);
		Assertions.assertThat(ctx.getConfig().path("hosts").get(1).path("tags").path("canary").asBoolean()).isTrue();
		Assertions.assertThat(ctx.getConfig().path("hosts").get(1).path("tags").path("load_balancing_weight").asInt()).isEqualTo(40);
	}
}
