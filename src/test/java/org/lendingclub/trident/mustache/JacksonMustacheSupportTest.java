package org.lendingclub.trident.mustache;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.mustache.JacksonMustacheSupport.JacksonFormatter;
import org.lendingclub.trident.util.JsonUtil;
import org.rapidoid.u.U;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.samskivert.mustache.DefaultCollector;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Collector;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Mustache.Formatter;
import com.samskivert.mustache.Mustache.VariableFetcher;

public class JacksonMustacheSupportTest {

	Compiler mustache = Mustache.compiler().defaultValue("").withFormatter(new JacksonMustacheSupport.JacksonFormatter())
			.withCollector(new JacksonMustacheSupport.JacksonCollector(new DefaultCollector())).escapeHTML(false);

	@Test
	public void testObjectNodeContext() {
		ObjectNode n = JsonUtil.getObjectMapper().createObjectNode();
		n.put("foo", "bar").put("fizz", "buzz").put("a", "b");

		ObjectNode context = JsonUtil.getObjectMapper().createObjectNode();
		context.set("x", n);

		Assertions.assertThat(mustache.compile("what the {{#x}}{{fizz}}{{/x}}")
				.execute(context)).isEqualTo("what the buzz");
	}

	@Test
	public void testEmpty() {
		List<JsonNode> list = Lists.newArrayList();

		list.add(JsonUtil.createObjectNode().put("a", "world"));
		list.add(JsonUtil.createObjectNode().put("b", "universe"));
		
		Map<String,Object> context = Maps.newConcurrentMap();
		context.put("x", list);

		Assertions.assertThat(mustache.compile("hello {{#x}}{{#a}}{{this}}{{/a}}{{/x}}")
				.execute(context)).isEqualTo("hello world");
	}
	@Test
	public void testIt() {
		ObjectNode n = JsonUtil.getObjectMapper().createObjectNode();
		n.put("foo", "bar").put("fizz", "buzz").put("a", "b");

		Map<String, Object> context = U.map("x", n);
		
		String output = Mustache.compiler().withFormatter(new JacksonMustacheSupport.JacksonFormatter())
				.withCollector(new JacksonMustacheSupport.JacksonCollector()).compile("what the {{#x}}{{fizz}}{{/x}}")
				.execute(context);
		System.out.println(output);
	}
	
	@Test
	public void testFormatObjectNode() {
		ObjectNode n = JsonUtil.getObjectMapper().createObjectNode();
		n.put("foo", "bar");
		
		JacksonFormatter f = new JacksonFormatter();
		Assertions.assertThat(f.format(n)).isEqualTo("{\"foo\":\"bar\"}");
	}
	@Test
	public void testFormatArray() {
		ArrayNode n = JsonUtil.getObjectMapper().createArrayNode();
		n.add("a").add("b");
		
		Map<String, Object> context = U.map("x", n);
		
		JacksonFormatter f = new JacksonFormatter();
		
		Assertions.assertThat(f.format(n)).isEqualTo("[\"a\",\"b\"]");
	}
	@Test
	public void testFormatMissingNull() {
		ObjectNode n = JsonUtil.getObjectMapper().createObjectNode();
		n.put("foo", "bar").put("fizz", "buzz").put("a", "b");
		n.set("nullNode", NullNode.getInstance());
		n.set("missingNode", MissingNode.getInstance());
		n.set("null", null);
		Map<String, Object> context = U.map("x", n);
		
	
		Assertions.assertThat(mustache.compile("<{{#x}}{{missingNode}}{{/x}}>")
				.execute(context)).isEqualTo("<>");
		Assertions.assertThat(mustache.compile("<{{#x}}{{nullNode}}{{/x}}>")
				.execute(context)).isEqualTo("<>");
		Assertions.assertThat(mustache.compile("<{{#x}}{{null}}{{/x}}>")
				.execute(context)).isEqualTo("<>");
		Assertions.assertThat(mustache.compile("<{{#x}}{{notfound}}{{/x}}>")
				.execute(context)).isEqualTo("<>");
	}
	@Test
	public void testArray() {
		ArrayNode n = JsonUtil.getObjectMapper().createArrayNode();
		n.add("a").add(1).add("2");

		Map<String, Object> context = U.map("x", n);
		JacksonMustacheSupport jc = new JacksonMustacheSupport();

		String output = Mustache.compiler().withFormatter(new JacksonMustacheSupport.JacksonFormatter())
				.withCollector(new JacksonMustacheSupport.JacksonCollector()).compile("{{#x}}{{this}}{{/x}}")
				.execute(context);
		System.out.println(output);
	}
	
	@Test
	public void testFormatter() {
		
		Formatter f = new JacksonMustacheSupport.JacksonFormatter();
		Assertions.assertThat(f.format("a")).isEqualTo("a");
		Assertions.assertThat(f.format(null)).isEqualTo("null");
		Assertions.assertThat(f.format(JsonUtil.getObjectMapper().createObjectNode().put("a","1"))).isEqualTo("{\"a\":\"1\"}");
		Assertions.assertThat(f.format(JsonUtil.getObjectMapper().createObjectNode().put("a",1))).isEqualTo("{\"a\":1}");
		Assertions.assertThat(f.format(MissingNode.getInstance())).isEqualTo("");
		Assertions.assertThat(f.format(NullNode.getInstance())).isEqualTo("");
	}
}
