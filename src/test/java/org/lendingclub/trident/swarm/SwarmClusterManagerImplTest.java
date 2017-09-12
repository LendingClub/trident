package org.lendingclub.trident.swarm;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.junit.Ignore;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.crypto.CertificateAuthority.CertDetail;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public class SwarmClusterManagerImplTest extends TridentIntegrationTest {

	@Autowired
	SwarmClusterManager swarmClusterManager;

	@Autowired 
	CertificateAuthorityManager ca;
	
	@Test
	@Ignore
	public void testIt() throws JsonProcessingException {

		Assertions.assertThat(swarmClusterManager).isNotNull();

		CertDetail cd = swarmClusterManager.getSwarmCertDetail("e37054dc-a9d3-4420-aa39-20899d7f81df");

		swarmClusterManager.getSwarmManagerClient("e37054dc-a9d3-4420-aa39-20899d7f81df").infoCmd().exec();

	}

	JsonNode getSwarmJson(String id) {
		return getNeoRxClient().execCypher("match (s:DockerSwarm) where s.tridentClusterId={id} or s.name={id} return s","id",id).blockingFirst(MissingNode.getInstance());
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
		Assertions.assertThat(System.currentTimeMillis()-n.path("lastContactTs").asLong(0)).isCloseTo(0,Offset.offset(TimeUnit.MINUTES.toMillis(5)));
		
	}
	@Test
	public void testAddressList() {


		String clusterId = "junit-" + System.currentTimeMillis();
		String name = UUID.randomUUID().toString();
		List<org.lendingclub.trident.swarm.SwarmClusterManager.Address> list = swarmClusterManager.getManagerAddressList(clusterId);
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
			// add a mnaager
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

			Assertions.assertThat(swarmClusterManager.getManagerAddressList(name)).isEqualTo(swarmClusterManager.getManagerAddressList(clusterId));
			
			ca.createCertificateAuthority(clusterId);
			
			
		
			
			
		}

	}

	
	@Test
	public void testIt2() {
		swarmClusterManager.scanAllSwarms();
	}
}
