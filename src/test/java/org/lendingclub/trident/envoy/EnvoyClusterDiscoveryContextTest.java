package org.lendingclub.trident.envoy;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.util.JsonUtil;

public class EnvoyClusterDiscoveryContextTest {

	@Test
	public void testIt() {
		
		
		EnvoyClusterDiscoveryContext ctx = new EnvoyClusterDiscoveryContext();
		Assertions.assertThat(ctx.getConfig().get("clusters").isArray()).isTrue();
		Assertions.assertThat(ctx.getConfig().get("clusters").size()).isEqualTo(0);
		
		
		ctx.addCluster("junit", "qa", "default", "sg","service-a");
		ctx.addCluster("junit", "qa", "default", "sg","service-b");
		System.out.println(JsonUtil.prettyFormat(ctx.getConfig()));
		
		Assertions.assertThat(ctx.getConfig().get("clusters").get(0).path("name").asText()).isEqualTo("junit--qa--default--sg--service-a");
		Assertions.assertThat(ctx.getConfig().get("clusters").get(1).path("name").asText()).isEqualTo("junit--qa--default--sg--service-b");
		
		ctx.getConfig().get("clusters").forEach(it->{
			Assertions.assertThat(it.path("connect_timeout_ms").isNumber()).isTrue();
			Assertions.assertThat(it.path("type").asText()).isEqualTo("sds");
			Assertions.assertThat(it.path("lb_type").asText()).isEqualTo("round_robin");
		});
	}
}
