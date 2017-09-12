package org.lendingclub.trident.envoy;

import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;


public class JsonMetadataScrubber {


	static class StandardPredicate implements Predicate<String>{

		@Override
		public boolean test(String n) {
			return n != null && n.startsWith("__");
		}
		
	}
	
	public static JsonNode scrub(JsonNode n) {
		return scrub(n,new StandardPredicate());
	}
	public static JsonNode scrub(JsonNode n, Predicate<String> predicate) {
		if (n.isObject()) {
			ObjectNode on = ObjectNode.class.cast(n);
			List<String> scrubList = Lists.newLinkedList();
			n.fields().forEachRemaining(it -> {
				if (predicate.test(it.getKey())) {
					scrubList.add(it.getKey());
				}
			});
			scrubList.forEach(it -> {
				on.remove(it);
			});
			
			
		}
		n.forEach(it->{
			scrub(it,predicate);
		});
		return n;
	}
}
