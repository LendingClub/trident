package org.lendingclub.trident.envoy;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.mock.web.MockHttpServletRequest;

public class BasicEnvoyConfigDecoratorTest {

	@Test
	public void testIt() {
		
		EnvoyBootstrapConfigContext ctx = new EnvoyBootstrapConfigContext();
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setProtocol("https");
		request.setServerName("discovery.example.com");
	
		
		Assertions.assertThat(ctx.getConfig()).isNotNull();
		
		
		System.out.println(JsonUtil.prettyFormat(ctx.getConfig()));
		
	}
	

}
