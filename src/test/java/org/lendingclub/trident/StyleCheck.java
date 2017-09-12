package org.lendingclub.trident;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.bouncycastle.crypto.RuntimeCryptoException;
import org.junit.Test;

import com.google.common.base.Charsets;

public class StyleCheck {

	@Test
	public void testIt() throws IOException {

		try (Stream<Path> p = Files.walk(new File("./src/main/java").toPath())) {
			p.forEach(it -> {
				if (it.toFile().getName().endsWith(".java")) {
					try {
						
						com.google.common.io.Files.readLines(it.toFile(), Charsets.UTF_8).forEach(line -> {
							if (line.contains("System.out.println")) {
								Assertions.fail(it+": "+line);
							}
							if (line.contains(
									"org.assertj") || line.contains("jersey.repackaged") ) {
								Assertions.fail(it+": "+line);
							}
						});
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});
		}
	}
}
