package org.lendingclub.trident.swarm.local;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;

import org.lendingclub.mercator.core.Projector;
import org.lendingclub.mercator.docker.DockerScanner;
import org.lendingclub.mercator.docker.DockerScannerBuilder;
import org.lendingclub.mercator.docker.SwarmScanner;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;

@Component
public class LocalSwarmManager implements TridentStartupListener {

	Logger logger = LoggerFactory.getLogger(LocalSwarmManager.class);
	@Autowired
	Projector projector;

	public void onStart(ApplicationContext ctx) {

		DockerScanner scanner = null;
		try {
			scanner = projector.createBuilder(DockerScannerBuilder.class).withLocalDockerDaemon().build();
			scanner.getDockerClient().pingCmd().exec();
		} catch (Exception e) {
			logger.info("could not connect to local docker daemon (not a problem)");
			return;
		}

		try {
			logger.info("scanning local docker daemon...");
			scanner.scan();
			logger.info("done scanning local docker daemon");
		} catch (RuntimeException e) {
			logger.info(
					"problem scanning local docker daemon (maybe a problem, maybe not...you have to think about it)",
					e);
		}

		try {
			WebTarget wt = SwarmScanner.extractWebTarget(scanner.getDockerClient());
			JsonNode n = wt.path("/info").request().get(JsonNode.class);
			if (Strings.isNullOrEmpty(n.path("Swarm").path("NodeID").asText())) {
				// we are NOT in swarm mode
				enableSwarmMode(wt);
				// this should probably be added to Mercator...deleting local
				// DockerSwarm node if there is no swarm
				scanner.getNeoRxClient().execCypher("match (x:DockerSwarm {name:'local'}) detach delete x");
				scanner.scan();

			} else {
				logger.info("local daemon is already in swarm mode...no need to enable");
			}
		} catch (RuntimeException e) {
			logger.warn("problem", e);
		}

	}

	protected void enableSwarmMode(WebTarget wt) {
		try {
			String postBody = "{\"ListenAddr\":\"0.0.0.0:2377\",\"AdvertiseAddr\":\"\",\"DataPathAddr\":\"\",\"ForceNewCluster\":false,\"Spec\":{\"Labels\":null,\"Orchestration\":{},\"Raft\":{\"ElectionTick\":0,\"HeartbeatTick\":0},\"Dispatcher\":{},\"CAConfig\":{},\"TaskDefaults\":{},\"EncryptionConfig\":{\"AutoLockManagers\":false}},\"AutoLockManagers\":false,\"Availability\":\"\"}";
			JsonNode pb = JsonUtil.getObjectMapper().readTree(postBody);
			JsonNode n = wt.path("/swarm/init").request().post(Entity.entity(pb, "application/json"), JsonNode.class);
			String swarmId = n.asText();
			logger.info("created swarm: {}", swarmId);

		} catch (Exception e) {
			logger.warn("problem enabling swarm mode", e);
		}
	}
}
