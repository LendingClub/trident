package org.lendingclub.trident.swarm;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.ignite.resources.IgniteInstanceResource;
import org.assertj.core.api.Assertions;
import org.junit.Assume;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

public class IncrementalScanEventConsumerIntegrationTest extends TridentIntegrationTest {

	@Autowired
	IncrementalScannerEventConsumer ic;
	
	@Autowired
	SwarmClusterManager scm;
	
	@Test
	public void testIt() {
		Assume.assumeTrue(isLocalDockerDaemonAvailable());
		Assertions.assertThat(scm.getSwarmScanner("local")).isNotNull();
		scm.getSwarmScanner("local").scanService("musing_thompson");
	}
	
	
}
