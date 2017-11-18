package org.lendingclub.trident.provision;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;

public class ProvisioningContextTest extends TridentIntegrationTest {

	
	@Test
	public void testIt() {
		SwarmNodeProvisionContext c = new SwarmNodeProvisionContext();
		
		
		
		Assertions.assertThat(c.getString("os").orElse("")).isEqualTo("centos");
		Assertions.assertThat(c.withOS("ubuntu").getString("os").orElse("")).isEqualTo("ubuntu");
		
		Assertions.assertThat(c.getString("tridentBaseUrl").get()).startsWith("http");
		
	}
	
	@Test
	public void testExports() {
		SwarmNodeProvisionContext c = new SwarmNodeProvisionContext();
		Assertions.assertThat(c.getExports()).contains("http_proxy");
		Assertions.assertThat(c.getExports()).doesNotContain("foo");
		
		c.withExport("foo");
		
		Assertions.assertThat(c.getExports()).contains("foo");
		
	}
}
