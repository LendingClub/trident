package org.lendingclub.trident.envoy;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.util.JsonUtil;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetadataScrubberTest {

	@Test
	public void testIt() {
		
		
		JsonMetadataScrubber s = new JsonMetadataScrubber();
		
		
		ObjectNode n = JsonUtil.createObjectNode().put("foo", "bar").put("__foo", "buzz");
		ArrayNode an = JsonUtil.createArrayNode();
		n.set("array", an);
		an.add(JsonUtil.createObjectNode().put("a","1").put("fizz", "buzz"));
		an.add(JsonUtil.createObjectNode().put("b","2").put("__flip", "top"));
		
		s.scrub(n);
		
		JsonUtil.logInfo(getClass(), "foo", n);
		
		Assertions.assertThat(n.path("foo").asText()).isEqualTo("bar");
		Assertions.assertThat(n.path("array").get(0).get("a").asText()).isEqualTo("1");
		Assertions.assertThat(n.path("array").get(0).get("fizz").asText()).isEqualTo("buzz");
		
		Assertions.assertThat(n.has("__foo")).isFalse();
		Assertions.assertThat(n.path("array").get(1).get("b").asText()).isEqualTo("2");
		Assertions.assertThat(n.get("array").get(1).has("__flip")).isFalse();
	}
}
