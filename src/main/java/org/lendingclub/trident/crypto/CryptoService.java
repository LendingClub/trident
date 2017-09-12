package org.lendingclub.trident.crypto;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.compress.compressors.gzip.GzipUtils;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;

@Component
public class CryptoService {

	private Map<String, CryptoProvider> providers = Maps.newConcurrentMap();
	private static final char MAGIC_PREFIX = '@';
	private String preferredProvider = "null";

	public CryptoService() {
		registerProvider(new NullCryptoProvider());
	}

	public String encrypt(String plainText) {
		return encrypt(plainText, preferredProvider);
	}

	public String encrypt(String plainText, String provider) {
		return encrypt(plainText.getBytes(), provider);
	}

	public String decryptString(String string) {
		return decryptString(decodeArmoredPayload(string));
	}

	private String decryptString(JsonNode d) {
		
		return new String(decryptBytes(d));
	}

	private byte[] decryptBytes(JsonNode envelope) {
		String method = envelope.path("p").asText();
		CryptoProvider crypto = getCryptoProvider(method);
	
		return crypto.decrypt(envelope);

	}

	CryptoProvider getCryptoProvider() {
		return getCryptoProvider(preferredProvider);
	}

	public void setPreferredProvider(String name) {
		Preconditions.checkState(providers.containsKey(name));
	}

	public void registerProvider(CryptoProvider p) {
		providers.put(p.getProviderName(), p);
	}

	CryptoProvider getCryptoProvider(String method) {
		CryptoProvider cp = providers.get(method);

		if (cp == null) {
			throw new TridentException("unsupported crypto provider: " + method);
		}
		return cp;
	}

	public boolean isEncrypted(String x) {
		return x!=null && x.startsWith("QHsicCI6");
	}
	public String encrypt(byte[] b) {
		return encrypt(b, preferredProvider);
	}

	public String encrypt(byte[] b, String provider) {

		ObjectNode n = JsonUtil.getObjectMapper().createObjectNode().put("p", provider);
		getCryptoProvider(provider).encrypt(b,n);
		byte[] data = (MAGIC_PREFIX + n.toString()).getBytes();

		return BaseEncoding.base64Url().encode(data);

	}

	static String encodeArmored(String provider, byte[] payload) {

		ObjectNode envelope = JsonUtil.getObjectMapper().createObjectNode().put("p", provider).put("c",
				BaseEncoding.base64Url().encode(payload));
		byte[] b = (MAGIC_PREFIX + envelope.toString()).getBytes();

		return BaseEncoding.base64Url().encode(b);

	}

	static JsonNode decodeArmoredPayload(String payload) {
		try {
			byte[] b = BaseEncoding.base64Url().decode(payload);

			String s = new String(b);
			if (s.charAt(0) != MAGIC_PREFIX) {
				throw new TridentException("invalid input");
			}
			return JsonUtil.getObjectMapper().readTree(s.substring(1));

		} catch (IOException e) {
			throw new TridentException(e);
		}
	}
}
