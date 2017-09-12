package org.lendingclub.trident.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class CryptoProvider {
	
	private String providerName;
	
	protected CryptoProvider(String name) {
		this.providerName = name;
	}
	public final String getProviderName() {
		return providerName;
	}


	abstract byte[] decrypt(JsonNode n);



	abstract void encrypt( byte[] b, ObjectNode output);

	
}
