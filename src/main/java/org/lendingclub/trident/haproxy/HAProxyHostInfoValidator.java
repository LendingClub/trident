package org.lendingclub.trident.haproxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HAProxyHostInfoValidator {

	static Logger logger = LoggerFactory.getLogger(HAProxyHostInfoValidator.class);

	public static ArrayNode filterOutInvalidHostInfo(ArrayNode hostsArray) {
		ArrayNode result = hostsArray.deepCopy();

		for(int i = 0; i < hostsArray.size(); i++ ) {

			JsonNode host = hostsArray.get(i);

			if( 	host.path("host").isMissingNode() ||
					host.path("port").isMissingNode() ||
					host.path("priority").isMissingNode() ||
					host.path("priority").asInt(-1) < 1 ||
					host.path("priority").asInt(-1) > 256
					)  {

				result.remove(i);
			}
		}
		return result;
	}
}
