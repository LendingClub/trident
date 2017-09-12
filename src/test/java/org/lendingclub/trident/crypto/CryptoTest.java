package org.lendingclub.trident.crypto;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.BaseEncoding;

public class CryptoTest {

	Logger logger = LoggerFactory.getLogger(CryptoTest.class);

	@Test
	public void testDecrypt() {

		Assertions.assertThat(new CryptoService().decryptString("QHsicCI6Im51bGwiLCJkIjoiUm05dklRPT0ifQ=="))
				.isEqualTo("Foo!");
	}

	@Test
	public void testIt2() {
		CryptoService cs = new CryptoService();

		String val = cs.encrypt("Hello!");

		Assertions.assertThat(cs.decryptString(val)).isEqualTo("Hello!");
	}

	@Test
	public void testIt23() {
		System.out.println(new String(BaseEncoding.base64().decode("QHsicCI6")));
		logger.info("{}",new CryptoService().encrypt(""));
		logger.info("{}",new CryptoService().encrypt("1"));
		logger.info("{}",new CryptoService().encrypt("2"));
		logger.info("{}",new CryptoService().encrypt("3"));
	}
}
