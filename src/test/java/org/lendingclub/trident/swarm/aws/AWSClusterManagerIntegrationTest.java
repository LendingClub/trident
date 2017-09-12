package org.lendingclub.trident.swarm.aws;

import java.util.UUID;

import javax.inject.Inject;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Test;
import org.lendingclub.mercator.aws.ASGScanner;
import org.lendingclub.mercator.aws.EC2InstanceScanner;
import org.lendingclub.mercator.aws.ELBScanner;
import org.lendingclub.mercator.aws.SubnetScanner;
import org.lendingclub.mercator.aws.VPCScanner;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.SwarmNodeType;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.aws.AWSMetadataSync;
import org.lendingclub.trident.swarm.aws.SwarmASGBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.regions.Regions;
import com.google.common.hash.Hashing;

public class AWSClusterManagerIntegrationTest extends TridentIntegrationTest {

	String NOT_FOUND = UUID.randomUUID().toString();
	@Inject
	AWSClusterManager clusterManager;

	@Inject
	NeoRxClient neo4j;

	@Autowired
	AWSMetadataSync metadataSync;
	
	@Test(expected = TridentException.class)
	public void testWorkerNotFound() {

		clusterManager.newWorkerASGBuilder(NOT_FOUND);
	}

	@Test(expected = TridentException.class)
	public void testManagerClusterNotFound() {
		clusterManager.newManagerASGBuilder(NOT_FOUND);
	}

	@Test
	public void testCreateManager() {
		String name = "junit-" + System.currentTimeMillis();
		String id = UUID.randomUUID().toString();
		neo4j.execCypher("merge (c:DockerSwarm {tridentClusterId:{id}}) set c.name={name} return c", "id", id, "name",
				name);

		SwarmASGBuilder b = clusterManager.newManagerASGBuilder(id);

		Assertions.assertThat(b.getTridentClusterId()).isEqualTo(id);
		Assertions.assertThat(b.getSwarmNodeType()).isEqualTo(SwarmNodeType.MANAGER);
		Assertions.assertThat(b.getAWSClusterManager().getAccountManager()).isNotNull();

	}

	@Test
	public void testBackfillDockerSwarm() {
		
		
		clusterManager.scanAll();
	}

	@Test
	public void testCreateWorkerBuilder() {
		String name = "junit-" + System.currentTimeMillis();
		String id = UUID.randomUUID().toString();
		neo4j.execCypher("merge (c:DockerSwarm {tridentClusterId:{id}}) set c.name={name} return c", "id", id, "name",
				name);

		SwarmASGBuilder b = clusterManager.newWorkerASGBuilder(id);

		Assertions.assertThat(b.getTridentClusterId()).isEqualTo(id);
		Assertions.assertThat(b.getSwarmNodeType()).isEqualTo(SwarmNodeType.WORKER);
		Assertions.assertThat(b.getAWSClusterManager().getAccountManager()).isNotNull();
		Assertions.assertThat(b.getAWSClusterManager()).isSameAs(clusterManager);
	}

	
	@Test
	public void testCreateRelationships() {
		metadataSync.createMissingASGRelationships();
	}

	@After
	public void cleanup() {
		if (neo4j.checkConnection()) {
			neo4j.execCypher("match (c:DockerSwarm) where c.name=~'junit.*' delete c");
		}
	}
}
