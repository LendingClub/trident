package org.lendingclub.trident.swarm.aws;

import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.aws.AWSAccountManager;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class TridentASGBuilderIntegrationTest extends TridentIntegrationTest {

	Logger logger = LoggerFactory.getLogger(TridentASGBuilderIntegrationTest.class);

	@Autowired
	AWSAccountManager manager;

	@Autowired
	AWSClusterManager clusterManager;

	@Test
	public void testIt() {
		try {
			clusterManager.newManagerASGBuilder("junit-" + UUID.randomUUID().toString());
			Assertions.failBecauseExceptionWasNotThrown(TridentException.class);
		} catch (TridentException e) {
			Assertions.assertThat(e).hasMessageContaining("cluster not found");
		}
	}
}
