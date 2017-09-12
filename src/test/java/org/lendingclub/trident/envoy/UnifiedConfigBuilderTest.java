package org.lendingclub.trident.envoy;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;

public class UnifiedConfigBuilderTest extends TridentIntegrationTest {

	@Autowired
	EnvoyBootstrapController controller;
	
	@Test
	public void testRequest() {
		
		JsonNode n = controller.unifiedConfig(new MockHttpServletRequest(), "zone--test--default--services", "mynode").getBody();
		JsonUtil.logInfo(getClass(), "test", n);
		
		Assertions.assertThat(n.path("cluster_manager").path("clusters").isArray()).isTrue();
		Assertions.assertThat(n.path("cluster_manager").has("sds")).isFalse();
		Assertions.assertThat(n.path("cluster_manager").has("cds")).isFalse();
	}
}
