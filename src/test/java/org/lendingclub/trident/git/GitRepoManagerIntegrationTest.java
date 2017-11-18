package org.lendingclub.trident.git;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

public class GitRepoManagerIntegrationTest  extends TridentIntegrationTest {

	@Autowired
	GitRepoManager repoMan;
	
	@Test
	public void testIt() throws Exception {
		
		Assertions.assertThat(repoMan).isNotNull();
		
	}
}
