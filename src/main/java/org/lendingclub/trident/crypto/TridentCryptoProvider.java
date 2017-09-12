package org.lendingclub.trident.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.lendingclub.trident.TridentException;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteStreams;

public class TridentCryptoProvider extends CryptoProvider {

	@Autowired
	TridentCryptoKeyStoreManager ksm;

	public TridentCryptoProvider() {
		super("trident");
	}

	byte[] decrypt(JsonNode envelope) {
		try {

			byte[] cipherText = BaseEncoding.base64().decode(envelope.path("d").asText());

			String keyAlias = envelope.path("k").asText();

			SecretKey key = (SecretKey) ksm.getKey(keyAlias);
			if (key == null) {
				throw new KeyStoreException("could not load key: " + keyAlias);
			}
			try (ByteArrayInputStream is = new ByteArrayInputStream(cipherText)) {
				return ByteStreams.toByteArray(decrypt(is, key));
			}
		} catch (GeneralSecurityException | IOException e) {
			throw new TridentException(e);
		}

	}

	protected static byte[] readByteArray(InputStream input) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len = 0;
		while ((len = input.read(buffer)) > 0) {
			baos.write(buffer, 0, len);
		}
		baos.close();
		return baos.toByteArray();

	}

	protected InputStream decrypt(InputStream encrypted, SecretKey secretKey)
			throws IOException, GeneralSecurityException {

		byte[] b = readByteArray(encrypted);

		byte[] ivdata = new byte[16];

		ByteArrayInputStream bais = new ByteArrayInputStream(b);
		bais.read(ivdata);

		Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");

		c.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(ivdata));

		return new CipherInputStream(bais, c);
	}

	String chooseEncryptionKey() {
		return ksm.getKeyAlias();
	}
	
	@Override
	void encrypt(byte[] plain, ObjectNode n) {
		String alias = chooseEncryptionKey();
		encrypt(plain,n,alias);
	}

	void encrypt(byte[] plain, ObjectNode n, String alias)  {
		try {
			
			
			SecretKey secretKey = (SecretKey) ksm.getKey(alias);
			Cipher c = cipherForKey(secretKey);

			byte[] ivdata = new byte[16];
			SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
			sr.nextBytes(ivdata);

	
			IvParameterSpec ivps = new IvParameterSpec(ivdata);
			SecretKeySpec sks = new SecretKeySpec(secretKey.getEncoded(),
					secretKey.getAlgorithm());

			c.init(Cipher.ENCRYPT_MODE, sks, ivps);

			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			baos.write(ivdata);
			CipherOutputStream cos = new CipherOutputStream(baos, c);
			cos.write(plain);
			cos.close();

			String encoded = BaseEncoding.base64().encode(baos.toByteArray());
			
			n.put("k",alias).put("d", encoded);
			
		

		} catch (IOException | GeneralSecurityException e) {
			throw new TridentException(e);
		}
	}
	protected String decryptString(String cipherText, SecretKey sk) throws GeneralSecurityException {

		try {

			InputStream input = null;

			;
			input = decrypt(new ByteArrayInputStream(BaseEncoding.base64().decode(cipherText)), sk);
			byte[] b = readFully(input);

			String decryptedString = new String(b, "UTF-8");
			if (Strings.isNullOrEmpty(decryptedString)) {

				throw new GeneralSecurityException();
			}
			return decryptedString;
		} catch (IOException e) {
			throw new GeneralSecurityException(e);
		} catch (IllegalArgumentException e) {
			throw new GeneralSecurityException(e);
		}

	}

	protected Cipher cipherForKey(SecretKey key) throws GeneralSecurityException {

		if (!"AES".equals(key.getAlgorithm())) {
			throw new GeneralSecurityException("key algorithm not supported: " + key.getAlgorithm());
		}
		return Cipher.getInstance("AES/CBC/PKCS5Padding");
	}

	protected byte[] readFully(InputStream input) throws IOException {
		if (input == null) {
			throw new NullPointerException("readFully() cannot accept null");
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1];
		int len = 0;
		while ((len = input.read(buffer)) > 0) {
			baos.write(buffer, 0, len);
		}

		baos.close();
		return baos.toByteArray();
	}
}
