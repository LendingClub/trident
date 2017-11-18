package org.lendingclub.trident.haproxy;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HAProxyHostInfoValidatorTest {

	Logger logger = LoggerFactory.getLogger(HAProxyHostInfoValidatorTest.class);

	public ObjectNode getSampleValidHostInfo() {
		ObjectNode host = JsonUtil.createObjectNode();

		host.put("host", "localhost");
		host.put("port", "8080");
		host.put("priority", 256);
		return host;
	}

	@Test
	public void testFilterInvalidHostInfo() {
		ArrayNode hosts = JsonUtil.createArrayNode();

		ObjectNode hostInfo = (ObjectNode) getSampleValidHostInfo();

		//invalidate the host info by removing port field
		hostInfo.remove("port");

		hosts.add(hostInfo);

		Assertions.assertThat(hosts.size()).isEqualTo(1);

		hosts = HAProxyHostInfoValidator.filterOutInvalidHostInfo(hosts);

		Assertions.assertThat(hosts).hasSize(0);

		//now test non existent host field filtering
		hostInfo.remove("host");

		hosts.add(hostInfo);

		Assertions.assertThat(hosts.size()).isEqualTo(1);

		hosts = HAProxyHostInfoValidator.filterOutInvalidHostInfo(hosts);

		Assertions.assertThat(hosts).hasSize(0);

		// try invalid priority filtering
		hostInfo = (ObjectNode) getSampleValidHostInfo();

		//add in an invalid priority
		hostInfo = hostInfo.put("priority", 257);

		hosts.add(hostInfo);

		hosts = HAProxyHostInfoValidator.filterOutInvalidHostInfo(hosts);

		Assertions.assertThat(hosts).hasSize(0);

		//now try negative priority
		hostInfo = hostInfo.put("priority", -1);

		hosts.add(hostInfo);

		hosts = HAProxyHostInfoValidator.filterOutInvalidHostInfo(hosts);

		Assertions.assertThat(hosts).hasSize(0);

	}
}
