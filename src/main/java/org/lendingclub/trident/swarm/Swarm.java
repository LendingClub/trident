package org.lendingclub.trident.swarm;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.client.WebTarget;

import org.lendingclub.mercator.docker.DockerScanner;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.dockerjava.api.DockerClient;

public interface Swarm {

	public interface SwarmNode {
		public Swarm getSwarm();
		String getNodeId();
	}
	
	public interface SwarmService {
		public Swarm getSwarm();
		public String getServiceId();
		public String getServiceName();
		public SwarmServiceEditor newServiceEditor();
	}
	

	
	Optional<String> getSwarmClusterId();
	String getTridentClusterId();
	String getSwarmName();
	SwarmNode getSwarmNode(String id);
	Map<String,SwarmNode> getSwarmNodes();
	DockerScanner getSwarmScanner();
	DockerClient getManagerClient();
	WebTarget getManagerWebTarget();
	JsonNode getInfo();
	
	
}
