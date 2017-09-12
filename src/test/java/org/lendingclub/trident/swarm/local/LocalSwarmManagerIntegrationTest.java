package org.lendingclub.trident.swarm.local;

import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.WebTarget;

import org.assertj.core.api.Assertions;
import org.assertj.core.data.Offset;
import org.assertj.core.util.Lists;
import org.assertj.core.util.Strings;
import org.junit.Assert;
import org.junit.Test;
import org.lendingclub.mercator.docker.SwarmScanner;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIntegrationTest;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

public class LocalSwarmManagerIntegrationTest extends TridentIntegrationTest {

	Logger logger = LoggerFactory.getLogger(getClass());
	@Autowired
	LocalSwarmManager localSwarmManager;
	
	@Autowired
	NeoRxClient neo4j;
	@Test
	public void testIt() {
		Assertions.assertThat(localSwarmManager).isNotNull();
	
		if (!isLocalDockerDaemonAvailable()) {
			logger.info("docker not avaialble");
			return;
		}
		WebTarget wt = SwarmScanner.extractWebTarget(getLocalDockerClient().get());
		JsonNode info = wt.path("/info").request().get(JsonNode.class);
		
		JsonUtil.logInfo(getClass(), "", info);
		if (Strings.isNullOrEmpty(info.path("Swarm").path("NodeID").asText())) {
			logger.info("we do not seem to be running in swarm mode locally");
			return;
		}
		
		JsonNode n = neo4j.execCypher("match (d:DockerSwarm {name:'local'}) return d").blockingFirst();
		
		JsonUtil.logInfo(getClass(), "", n);
		Assertions.assertThat(n.path("name").asText()).isEqualTo("local");
		Assertions.assertThat(n.path("managerApiUrl").asText()).isEqualTo("unix:///var/run/docker.sock");

		Assertions.assertThat(n.path("swarmClusterId").asText()).isEqualTo(info.path("Swarm").path("Cluster").path("ID").asText());
		
		JsonNode host = neo4j.execCypher("match (d:DockerSwarm {name:'local'})--(h:DockerHost) return h").blockingFirst();
		JsonUtil.logInfo(getClass(), "", host);
		Assertions.assertThat(host.path("swarmNodeId").asText()).isEqualTo(info.path("Swarm").path("NodeID").asText());
		
		
		Lists.newArrayList("engineVersion","leader","hostname","role","swarmNodeId","state","availability","addr","updateTs").forEach(it->{
			Assert.assertTrue("DockerHost node should have attribute: "+it,host.has(it));
		});		
	}
}
