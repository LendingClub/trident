package org.lendingclub.trident.swarm;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.NotFoundException;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.crypto.CertificateAuthority.CertDetail;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.crypto.CryptoService;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public class SwarmClusterManagerImplTest extends TridentIntegrationTest {

	@Autowired
	SwarmClusterManager swarmClusterManager;

	@Autowired
	CertificateAuthorityManager ca;

	@Autowired
	CryptoService cryptoService;

	@Test
	@Ignore
	public void testIt() throws JsonProcessingException {

		Assertions.assertThat(swarmClusterManager).isNotNull();

		CertDetail cd = swarmClusterManager.getSwarmCertDetail("e37054dc-a9d3-4420-aa39-20899d7f81df");

		swarmClusterManager.getSwarmManagerClient("e37054dc-a9d3-4420-aa39-20899d7f81df").infoCmd().exec();

	}

	JsonNode getSwarmJson(String id) {
		return getNeoRxClient()
				.execCypher("match (s:DockerSwarm) where s.tridentClusterId={id} or s.name={id} return s", "id", id)
				.blockingFirst(MissingNode.getInstance());
	}

	@Test
	public void testLastContact() {
		String clusterId = "junit-" + System.currentTimeMillis();
		String name = UUID.randomUUID().toString();
		getNeoRxClient().execCypher(
				"merge (s:DockerSwarm {tridentClusterId:{id},name:{name}}) set s.managerAddress='1.2.3.4:2377' return s",
				"id", clusterId, "name", name);

		swarmClusterManager.markSuccessfulConnection(clusterId);

		JsonNode n = getSwarmJson(clusterId);
		Assertions.assertThat(System.currentTimeMillis() - n.path("lastContactTs").asLong(0)).isCloseTo(0,
				Offset.offset(TimeUnit.MINUTES.toMillis(5)));

	}

	@Test
	public void testAddressList() {

		String clusterId = "junit-" + System.currentTimeMillis();
		String name = UUID.randomUUID().toString();
		List<org.lendingclub.trident.swarm.SwarmClusterManager.Address> list = swarmClusterManager
				.getManagerAddressList(clusterId);
		Assertions.assertThat(list).isEmpty();

		getNeoRxClient().execCypher(
				"merge (s:DockerSwarm {tridentClusterId:{id},name:{name}}) set s.managerAddress='1.2.3.4:2377' return s",
				"id", clusterId, "name", name);

		list = swarmClusterManager.getManagerAddressList(clusterId);
		Assertions.assertThat(list.get(0).tridentClusterId).isEqualTo(clusterId);
		Assertions.assertThat(list.get(0).url).isEqualTo("tcp://1.2.3.4:2376");

		{
			getNeoRxClient().execCypher(
					"merge (s:DockerSwarm {tridentClusterId:{id}}) set s.managerApiUrl='tcp://10.0.0.1:2376'", "id",
					clusterId);

			list = swarmClusterManager.getManagerAddressList(clusterId);

			Assertions.assertThat(list.get(0).tridentClusterId).isEqualTo(clusterId);
			Assertions.assertThat(list.get(0).url).isEqualTo("tcp://10.0.0.1:2376");
			Assertions.assertThat(list.get(1).tridentClusterId).isEqualTo(clusterId);
			Assertions.assertThat(list.get(1).url).isEqualTo("tcp://1.2.3.4:2376");
		}

		{
			// add a worker
			getNeoRxClient().execCypher(
					"match (s:DockerSwarm {tridentClusterId:{id}}) merge (h:DockerHost {role:'worker',name:'junit-1',addr:'192.168.10.1'}) merge (s)-[:TEST]-(h)",
					"id", clusterId);

			list = swarmClusterManager.getManagerAddressList(clusterId);

			Assertions.assertThat(list.get(0).tridentClusterId).isEqualTo(clusterId);
			Assertions.assertThat(list.get(0).url).isEqualTo("tcp://10.0.0.1:2376");
			Assertions.assertThat(list.get(1).tridentClusterId).isEqualTo(clusterId);
			Assertions.assertThat(list.get(1).url).isEqualTo("tcp://1.2.3.4:2376");
		}

		{
			// add a manager
			getNeoRxClient().execCypher(
					"match (s:DockerSwarm {tridentClusterId:{id}}) merge (h:DockerHost {role:'manager',name:'junit-1',addr:'192.168.0.1'}) merge (s)-[:TEST]-(h)",
					"id", clusterId);

			list = swarmClusterManager.getManagerAddressList(clusterId);

			Assertions.assertThat(list.get(0).tridentClusterId).isEqualTo(clusterId);
			Assertions.assertThat(list.get(0).url).isEqualTo("tcp://10.0.0.1:2376");
			Assertions.assertThat(list.get(1).tridentClusterId).isEqualTo(clusterId);
			Assertions.assertThat(list.get(1).url).isEqualTo("tcp://192.168.0.1:2376");
			Assertions.assertThat(list.get(2).tridentClusterId).isEqualTo(clusterId);
			Assertions.assertThat(list.get(2).url).isEqualTo("tcp://1.2.3.4:2376");

			Assertions.assertThat(swarmClusterManager.getManagerAddressList(name))
					.isEqualTo(swarmClusterManager.getManagerAddressList(clusterId));

			ca.createCertificateAuthority(clusterId);

		}

	}

	@Test
	public void testIt2() {
		swarmClusterManager.scanAllSwarms();
	}

	@Test
	public void testJoinTokensFromLocalDockerDaemon() {
		Assume.assumeTrue(isLocalDockerDaemonAvailable());
	
		Assertions.assertThat(swarmClusterManager.getJoinToken("local",SwarmNodeType.MANAGER).getToken().startsWith("SWMTKN")).isTrue();
		Assertions.assertThat(swarmClusterManager.getJoinToken("local",SwarmNodeType.WORKER).getToken().startsWith("SWMTKN")).isTrue();
		Assertions.assertThat(swarmClusterManager.getJoinToken("local",SwarmNodeType.WORKER).getAddress()).endsWith(":2377");
		Assertions.assertThat(swarmClusterManager.getJoinToken("local",SwarmNodeType.MANAGER).getAddress()).endsWith(":2377");
	}

	@Test
	public void testJoinTokensFromNeo4jWithoutPort() {

		String id = UUID.randomUUID().toString();
		String managerToken = "SWMTKN-" + UUID.randomUUID().toString();
		String workerToken = "SWMTKN-" + UUID.randomUUID().toString();
		String managerAddress = "1.2.3.4";
		swarmClusterManager.neo4j.execCypher(
				"merge (a:DockerSwarm {name:{name}}) set a.junitData=true, a.workerJoinToken={workerToken}, a.managerJoinToken={managerToken}, a.managerAddress={addr} return a",
				"name", id, "managerToken", cryptoService.encrypt(managerToken), "workerToken",
				cryptoService.encrypt(workerToken), "addr", managerAddress);

		SwarmToken mt = swarmClusterManager.getJoinToken(id,SwarmNodeType.MANAGER);
		SwarmToken wt = swarmClusterManager.getJoinToken(id,SwarmNodeType.WORKER);
		Assertions.assertThat(mt.getToken()).startsWith("SWMTKN");
		Assertions.assertThat(wt.getToken()).startsWith("SWMTKN");
		Assertions.assertThat(wt.getToken()).isNotEqualTo(mt.getToken());
		Assertions.assertThat(mt.getAddress()).isEqualTo("1.2.3.4:2377");
		Assertions.assertThat(wt.getAddress()).isEqualTo("1.2.3.4:2377");
		Assertions.assertThat(mt.getNodeType()).isEqualTo(SwarmNodeType.MANAGER);
		Assertions.assertThat(wt.getNodeType()).isEqualTo(SwarmNodeType.WORKER);
		
		

	}

	@Test
	public void testJoinTokensFromNeo4j() {

		String id = UUID.randomUUID().toString();
		String managerToken = "SWMTKN-" + UUID.randomUUID().toString();
		String workerToken = "SWMTKN-" + UUID.randomUUID().toString();
		String managerAddress = "1.2.3.4:2377";
		swarmClusterManager.neo4j.execCypher(
				"merge (a:DockerSwarm {name:{name}}) set a.junitData=true, a.workerJoinToken={workerToken}, a.managerJoinToken={managerToken}, a.managerAddress={addr} return a",
				"name", id, "managerToken", cryptoService.encrypt(managerToken), "workerToken",
				cryptoService.encrypt(workerToken), "addr", managerAddress);

		SwarmToken mt = swarmClusterManager.getJoinToken(id, SwarmNodeType.MANAGER);
		Assertions.assertThat(swarmClusterManager.getJoinToken(id, SwarmNodeType.MANAGER).getToken()).startsWith("SWMTKN");
		Assertions.assertThat(swarmClusterManager.getJoinToken(id, SwarmNodeType.WORKER).getToken()).startsWith("SWMTKN");
		Assertions.assertThat(swarmClusterManager.getJoinToken(id, SwarmNodeType.WORKER).getAddress()).isEqualTo(managerAddress);
		Assertions.assertThat(swarmClusterManager.getJoinToken(id, SwarmNodeType.MANAGER).getAddress()).isEqualTo(managerAddress);

		try {
			swarmClusterManager.getJoinToken(UUID.randomUUID().toString(),SwarmNodeType.MANAGER);
			Assertions.failBecauseExceptionWasNotThrown(NotFoundException.class);
		} catch (Exception e) {
			Assertions.assertThat(e).isInstanceOf(NotFoundException.class);
		}

	}

	@Test
	public void testMissingJoinTokensFromNeo4j() {

		try {
			String id = UUID.randomUUID().toString();

			String managerAddress = "1.2.3.4:2377";
			swarmClusterManager.neo4j.execCypher(
					"merge (a:DockerSwarm {name:{name}}) set a.junitData=true,  a.managerAddress={addr} return a",
					"name", id, "addr", managerAddress);

			SwarmToken token = swarmClusterManager.getJoinToken(id,SwarmNodeType.MANAGER);
		} catch (TridentException e) {
			Assertions.assertThat(e).hasMessageContaining("obtain join tokens");
		}

	}

}
