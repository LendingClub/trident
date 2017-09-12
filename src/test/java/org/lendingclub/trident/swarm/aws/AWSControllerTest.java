package org.lendingclub.trident.swarm.aws;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.aws.AWSController;

public class AWSControllerTest extends TridentIntegrationTest {

	@Inject
	AWSController controller;
	@Inject
	NeoRxClient neo4j;

	@Test
	public void testDummy() {

	}

	@After
	public void cleanup() {
		if (isIntegrationTestEnabled()) {
			neo4j.execCypher("match (c:DockerSwarm) where c.name=~'junit.*' delete c");
		}
	}
}
