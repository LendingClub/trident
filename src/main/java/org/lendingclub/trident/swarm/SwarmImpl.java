package org.lendingclub.trident.swarm;

import java.util.Map;
import java.util.Optional;

import javax.ws.rs.client.WebTarget;

import org.lendingclub.mercator.docker.DockerScanner;
import org.lendingclub.trident.NotFoundException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.github.dockerjava.api.DockerClient;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class SwarmImpl implements Swarm {

	String swarmId;
	String tridentId;
	String swarmName;
	SwarmClusterManager swarmClusterManager;

	public class SwarmNodeImpl implements SwarmNode {

		String nodeId;

		@Override
		public Swarm getSwarm() {
			return SwarmImpl.this;
		}

		@Override
		public String getNodeId() {
			return this.nodeId;
		}

		public String toString() {
			return MoreObjects.toStringHelper(this).add("nodeId", nodeId).add("swarmName", swarmName)
					.add("swarmId", swarmId).toString();
		}
		
	}

	public class SwarmServiceImpl implements SwarmService {

		private String serviceId;
		private String serviceName;
		@Override
		public Swarm getSwarm() {
			return SwarmImpl.this;
		}

		@Override
		public String getServiceId() {
			return serviceId;
		}
		
		public String getServiceName() {
			return serviceName;
		}

		@Override
		public SwarmServiceEditor newServiceEditor() {
			return new SwarmServiceEditor().withSwarmId(SwarmImpl.this.getSwarmClusterId().get()).withServiceId(getServiceId());
		}
	}
	
	
	@Override
	public Optional<String> getSwarmClusterId() {
		if (swarmId == null) {
			// We should only be in a situation where we don't know the swarmId
			// before the swarm has formed.
			String id = swarmClusterManager.neo4j
					.execCypher("match (a:DockerSwarm {tridentClusterId:{id}}) return a", "id", tridentId)
					.blockingFirst(NullNode.instance).path("swarmClusterId").asText(null);
			if (id != null) {
				this.swarmId = id;
			}

		}
		return Optional.ofNullable(swarmId);
	}

	@Override
	public String getTridentClusterId() {
		return tridentId;
	}

	@Override
	public DockerClient getManagerClient() {
		Preconditions.checkState(!Strings.isNullOrEmpty(swarmId));
		return swarmClusterManager.getSwarmManagerClient(swarmId);
	}

	@Override
	public WebTarget getManagerWebTarget() {
		Preconditions.checkState(!Strings.isNullOrEmpty(swarmId));
		return swarmClusterManager.getSwarmManagerWebTarget(swarmId);
	}

	@Override
	public JsonNode getInfo() {
		return getManagerWebTarget().path("info").request().get(JsonNode.class);
	}

	@Override
	public DockerScanner getSwarmScanner() {
		Preconditions.checkState(!Strings.isNullOrEmpty(swarmName));
		return swarmClusterManager.getSwarmScanner(swarmName);
	}

	@Override
	public String getSwarmName() {
		return swarmName;
	}

	public Map<String, SwarmNode> getSwarmNodes() {
		Map<String, SwarmNode> map = Maps.newHashMap();
		swarmClusterManager.neo4j
				.execCypher("match (s:DockerSwarm {tridentClusterId:{tridentClusterId}})--(h:DockerHost) return h",
						"tridentClusterId", tridentId)
				.forEach(it -> {
					SwarmNodeImpl sni = new SwarmNodeImpl();
					sni.nodeId = it.path("swarmNodeId").asText();
					if (!Strings.isNullOrEmpty(sni.nodeId)) {
						map.put(sni.nodeId, sni);
					}
				});
		return ImmutableMap.copyOf(map);

	}

	public SwarmNode getSwarmNode(String id) {
		JsonNode n = swarmClusterManager.neo4j.execCypher(
				"match (s:DockerSwarm {tridentClusterId:{tridentClusterId}})--(a:DockerHost {swarmNodeId:{id}}) return a",
				"tridentClusterId", tridentId, "id", id).blockingFirst(null);

		if (n == null) {
			throw new NotFoundException("SwarmNode", id);
		}

		SwarmNodeImpl node = new SwarmNodeImpl();
		node.nodeId = id;
		return node;

	}

	public SwarmService getSwarmService(String serviceId) {
		JsonNode n = swarmClusterManager.neo4j.execCypher(
				"match (s:DockerSwarm {tridentClusterId:{tridentClusterId}})--(a:DockerService) where a.name={serviceId} or a.serviceId={serviceId} return a",
				"tridentClusterId", tridentId, "id", serviceId).blockingFirst(null);
		if (n==null) {
			throw new NotFoundException("DockerService",serviceId);
		}
		
		SwarmServiceImpl service = new SwarmServiceImpl();
		service.serviceId = n.path("serviceId").asText();
		service.serviceName = n.path("serviceName").asText();
		return service;
	}
	public String toString() {
		return MoreObjects.toStringHelper(this).add("swarmName", this.swarmName).add("swarmId", this.swarmId)
				.add("tridentId", this.tridentId).toString();
	}
}
