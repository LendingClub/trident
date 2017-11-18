package org.lendingclub.trident.envoy;

import java.io.IOException;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;

public class UnifiedConfigBuilderTest extends TridentIntegrationTest {

	@Autowired
	EnvoyDiscoveryController controller;
	
	@Test
	public void testRequest() throws IOException {
		
		JsonNode n = JsonUtil.getObjectMapper().readTree(controller.configUnified(new MockHttpServletRequest(), "zone--test--default--services", "mynode").getBody());
		JsonUtil.logInfo(getClass(), "test", n);
		
		Assertions.assertThat(n.path("cluster_manager").path("clusters").isArray()).isTrue();
		Assertions.assertThat(n.path("cluster_manager").has("sds")).isFalse();
		Assertions.assertThat(n.path("cluster_manager").has("cds")).isFalse();
	}
}
