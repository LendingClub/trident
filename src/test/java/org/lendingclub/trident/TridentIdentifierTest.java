package org.lendingclub.trident;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TridentIdentifierTest {
	
	@Test
	public void testNullValue() { 
		TridentIdentifier identifier = new TridentIdentifier()
				.withAppId("foo");

		Assertions.assertThat(identifier.getEnvironment().isPresent()).isFalse();
		Assertions.assertThat(identifier.getPort().isPresent()).isFalse();
		Assertions.assertThat(identifier.getAppId().get()).isEqualTo("foo");
	}
	
	@Test
	public void testEmptyValue() { 
		TridentIdentifier identifier = new TridentIdentifier()
				.withAppId("foo")
				.withPort(8080)
				.withEnvironment("");

		Assertions.assertThat(identifier.getEnvironment().isPresent()).isFalse();
		Assertions.assertThat(identifier.getPort().get()).isEqualTo(8080);
		Assertions.assertThat(identifier.getAppId().get()).isEqualTo("foo");
	}

}
