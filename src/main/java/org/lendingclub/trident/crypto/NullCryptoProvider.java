package org.lendingclub.trident.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.BaseEncoding;

public class NullCryptoProvider extends CryptoProvider {

	public NullCryptoProvider() {
		super("null");
	}

	
	byte[] decrypt(JsonNode envelope) {

		return BaseEncoding.base64Url().decode(envelope.path("d").asText());

	}

	@Override
	void encrypt(byte [] b, ObjectNode n) {
		n.put("d", BaseEncoding.base64Url().encode(b));
	
	}

}
