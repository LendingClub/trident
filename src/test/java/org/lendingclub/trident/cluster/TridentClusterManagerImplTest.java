package org.lendingclub.trident.cluster;

import java.net.Inet4Address;
import java.net.InetAddress;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;

public class TridentClusterManagerImplTest extends TridentIntegrationTest {

	@Autowired
	TridentClusterManager tridentClusterManager;

	@Autowired
	NeoRxClient neo4j;

	@Test
	public void testIt() {
		tridentClusterManager.paused = true;

		tridentClusterManager.leader.set("foo");

		Assertions.assertThat(tridentClusterManager.isEligibleLeader()).isTrue();
		Assertions.assertThat(tridentClusterManager.isLeader()).isFalse();
		neo4j.execCypher("match (a:TridentClusterState) detach delete a");
		tridentClusterManager.manageClusterState();
		Assertions.assertThat(tridentClusterManager.isLeader()).isTrue();
		Assertions.assertThat(neo4j.execCypher("match (a:TridentClusterState {instanceId:{id}}) return a", "id",
				tridentClusterManager.getInstanceId()).toList().blockingGet()).hasSize(1);
		Assertions.assertThat(neo4j.execCypher("match (a:TridentClusterState {instanceId:{id}}) return a", "id",
				tridentClusterManager.getInstanceId()).blockingFirst().path("leader").asBoolean()).isTrue();
		
		// pretend we have another node that has gone out to lunch
		neo4j.execCypher("match (a:TridentClusterState) set a.instanceId='the-other-one', a.heartbeat=timestamp()-(60*1000)");
		tridentClusterManager.manageClusterState();
		
		Assertions.assertThat(neo4j.execCypher("match (a:TridentClusterState {instanceId:{id}}) return a", "id",
				tridentClusterManager.getInstanceId()).blockingFirst().path("leader").asBoolean()).isTrue();
		Assertions.assertThat(neo4j.execCypher("match (a:TridentClusterState {instanceId:{id}}) return a", "id",
				"the-other-one").blockingFirst().path("leader").asBoolean()).isFalse();

	}
	
	
}
