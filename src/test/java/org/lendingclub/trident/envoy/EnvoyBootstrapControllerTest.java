package org.lendingclub.trident.envoy;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class EnvoyBootstrapControllerTest {

	
	@Test
	public void testIt() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("foobar");
		request.setServerPort(1234);
		request.setRequestURI("/a/b/c/");
		
		Assertions.assertThat(request.getRequestURL().toString()).isEqualTo("http://foobar:1234/a/b/c/");
		Assertions.assertThat(EnvoyDiscoveryController.getBaseUrl(request)).isEqualTo("http://foobar:1234");
	}
}
