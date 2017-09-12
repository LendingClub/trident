package org.lendingclub.trident.crypto;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class TridentCryptoProviderIntegrationTest extends TridentIntegrationTest {

	@Autowired
	TridentCryptoProvider crypto;
	
	@Test
	public void testIt() {
		ObjectNode n = JsonUtil.getObjectMapper().createObjectNode();
		crypto.encrypt("test".getBytes(), n);

		Assertions.assertThat(new String(crypto.decrypt(n))).isEqualTo("test");
	}
}
