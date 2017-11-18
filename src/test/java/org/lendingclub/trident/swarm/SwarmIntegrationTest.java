package org.lendingclub.trident.swarm;

import java.util.List;

import javax.ws.rs.core.MediaType;

import org.assertj.core.api.Assertions;
import org.junit.Assume;
import org.junit.Test;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import okhttp3.Response;

public class SwarmIntegrationTest extends TridentIntegrationTest {

	
	@Autowired
	SwarmClusterManager swarmClusterManager;
	
	
	@Test
	public void testIt() {
		Assume.assumeTrue(isLocalDockerDaemonAvailable());
		Swarm swarm = swarmClusterManager.getSwarm("local");
		
		Assertions.assertThat(swarm.getSwarmName()).isEqualTo("local");
		
		Assertions.assertThat(swarmClusterManager.getSwarm(swarm.getTridentClusterId()).getSwarmName()).isEqualTo("local");
		Assertions.assertThat(swarmClusterManager.getSwarm(swarm.getSwarmClusterId().get()).getSwarmName()).isEqualTo("local");
		
		JsonNode n = swarm.getInfo();
		Assertions.assertThat(n.path("Swarm").path("Cluster").path("ID").asText()).isEqualTo(swarm.getSwarmClusterId().get());
		
		
		
	}
	
	@Test
	public void testXX() {
		Swarm swarm = swarmClusterManager.getSwarm("local");
		
		JsonNode r = swarm.getManagerWebTarget().path("/swarm").request(MediaType.APPLICATION_JSON).get(JsonNode.class);
		JsonNode info = swarm.getInfo();
		String workerJoinToken = r.path("JoinTokens").path("Worker").asText();
		String managerJoinToken  = r.path("JoinTokens").path("Manager").asText();
		
		List<String> addressList = Lists.newArrayList();
		info.path("Swarm").path("RemoteManagers").forEach(it->{
			addressList.add(it.path("Addr").asText());
		});
		JsonUtil.logInfo("",info);
	//	JsonUtil.logInfo("",JsonUtil.getObjectMapper().readTree(r.getEntity()));
		
	}
}
