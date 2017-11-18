package org.lendingclub.trident.haproxy;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentIntegrationTest;

public class HAProxyMetaResourceProviderIntegrationTest extends TridentIntegrationTest{

	@Test
	public void testSpring() {
		Assertions.assertThat(Trident.getApplicationContext().getBean(HAProxyGitResourceProvider.class)).isNotNull();
		Assertions.assertThat(Trident.getApplicationContext().getBean(HAProxyFileSystemResourceProvider.class)).isNotNull();
	}
}
