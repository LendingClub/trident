package org.lendingclub.trident.haproxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.lendingclub.trident.util.JsonUtil;

/**
 * Created by hasingh on 9/20/17.
 */
public class HAProxyHostDiscoveryContext extends HAProxyDiscoveryContext {

	public class Host {
		ObjectNode data = JsonUtil.createObjectNode();

		public Host withAttribute(String name, String val) {
			data.put(name, val);
			return this;
		}
		public Host withAttribute(String name, int val) {
			data.put(name, val);
			return this;
		}
		public Host withAttribute(String name, JsonNode val) {
			data.set(name, val);
			return this;
		}

	}

	public HAProxyHostDiscoveryContext() {
		super();
	}

	public HAProxyHostDiscoveryContext(ObjectNode d) {
		super(d);
	}

}
