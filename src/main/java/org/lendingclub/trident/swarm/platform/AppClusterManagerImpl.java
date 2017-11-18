package org.lendingclub.trident.swarm.platform;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;

import org.lendingclub.mercator.core.NotFoundException;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.swarm.DockerServiceClient;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;
import org.lendingclub.trident.swarm.SwarmServiceEditor;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.DockerClient;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

public class AppClusterManagerImpl extends AppClusterManager {

	Logger logger = org.slf4j.LoggerFactory.getLogger(AppClusterManagerImpl.class);
	@Autowired
	NeoRxClient neo4j;

	@Autowired
	SwarmClusterManager swarmClusterManager;

	class DockerServiceClientImpl implements DockerServiceClient {

		String serviceId;
		String swarmId;
		DockerClient client;

		@Override
		public String getServiceId() {
			return serviceId;
		}

		@Override
		public String getSwarmId() {
			return swarmId;
		}

		@Override
		public DockerClient getDockerClient() {
			return swarmClusterManager.getSwarm(getSwarmId()).getManagerClient();
		}

		@Override
		public WebTarget getDockerWebTarget() {
			return swarmClusterManager.getSwarm(getSwarmId()).getManagerWebTarget();
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("swarmId", getSwarmId()).add("serviceId", getServiceId())
			        .toString();
		}
	}

	private static void applyLabels(AppClusterCommand source, ObjectNode labelsTarget) {
		ObjectNode labels = labelsTarget;

		source.getAppId().ifPresent(it -> {
			labelsTarget.put(SwarmDiscoverySearch.TSD_APP_ID, it);
		});
		source.getEnvironment().ifPresent(it -> {
			labelsTarget.put(SwarmDiscoverySearch.TSD_ENV, it);
		});
		source.getSubEnvironment().ifPresent(it -> {
			labelsTarget.put(SwarmDiscoverySearch.TSD_SUB_ENV, it);
		});
		source.getRegion().ifPresent(it -> {
			labelsTarget.put(SwarmDiscoverySearch.TSD_REGION, it);
		});
		source.getServiceGroup().ifPresent(it -> {
			labelsTarget.put(SwarmDiscoverySearch.TSD_SERVICE_GROUP, it);
		});
		source.getPort().ifPresent(it -> {
			labelsTarget.put(SwarmDiscoverySearch.TSD_PORT, it.toString());
		});
		source.getPath().ifPresent(it -> {
			labelsTarget.put("tsdPath", it);
		});
		source.getBlueGreenState().ifPresent(it -> {
			labelsTarget.put("tsdBlueGreenState", it.toString());
		});
		source.getProtocol().ifPresent(it -> {
			labelsTarget.put("tsdProtocol", it.toString());
		});
		source.getCommandData().path("labelsToRemove").fields().forEachRemaining(it -> {
			labels.remove(it.getKey());
		});

		source.getCommandData().path("labelsToAdd").fields().forEachRemaining(it -> {
			labels.set(it.getKey(), it.getValue());
		});
	}

	class LabelCommandImpl extends ApplyLabelsCommand {

		@Override
		public <T extends AppClusterCommand> T execute() {

			new SwarmServiceEditor()
					.withSwarmId(getDockerServiceClient().getSwarmId())
					.withServiceId(getDockerServiceClient().getServiceId())
					.withServiceConfig(c -> {
						applyLabels(this, ObjectNode.class.cast(c.get("Spec").get("Labels")));
					}).execute();

			rescanService();
			return (T) this;
		}
	}

	class CreateClusterCommandImpl extends CreateClusterCommand {

		@Override
		public <T extends AppClusterCommand> T execute() {

			byte[] b = new byte[8];
			new SecureRandom().nextBytes(b);
			String id = BaseEncoding.base32().encode(b).replaceAll("=", "");

			JsonNode existing = neo4j.execCypher(
			        "match (a:AppCluster {region:{region}, environment:{environment},subEvnironment:{subEnvironment},appId:{appId}}) return a",
			        "appId", getAppId().get(), "environment", getEnvironment().get(), "subEnvironment",
			        getSubEnvironment().orElse("default"), "region", getRegion().get()).blockingFirst(null);
			if (existing != null) {
				throw new TridentException("AppCluster already exists");
			}

			String cypher = "merge (a:AppCluster {appClusterId:{id}}) set a.appId={appId},a.environment={environment},a.subEnvironment={subEnvironment},a.region={region}, a.swarm={swarm}, ";
			
			if (getServiceGroup().isPresent()) {
				cypher += "a.serviceGroup=" +"'"+getServiceGroup().get()  + "', ";
			}
			cypher += "a.createTs=timestamp(),a.updateTs=timestamp() return a";
			neo4j.execCypher(cypher, "id", id, "appId", getAppId().get(), "environment", getEnvironment().get(), "subEnvironment",
			        getSubEnvironment().orElse("default"), "region", getRegion().get(), "swarm", getSwarm().get());
			withAppClusterId(id);
			return (T) this;
		}

	}

	class BlueGreenCommandImpl extends BlueGreenCommand {

		@Override
		public <T extends AppClusterCommand> T execute() {
			if (!getBlueGreenState().isPresent()) {
				throw new IllegalArgumentException("must specify blue/green state");
			}
			if (!(getSwarmServiceName().isPresent() || getSwarmServiceId().isPresent())) {
				throw new IllegalArgumentException("swarm service must be specified");
			}
			ApplyLabelsCommand cmd = labelCommand().withLabel("tsdBlueGreenState", getBlueGreenState().get().toString())
			        .withLabel("tsdBlueGreenStateChangeTs", "" + System.currentTimeMillis());

			getSwarmServiceName().ifPresent(it -> {
				cmd.withSwarmServiceName(getSwarmServiceName().get());
			});
			getSwarmServiceId().ifPresent(it -> {
				cmd.withSwarmServiceName(getSwarmServiceId().get());
			});
			cmd.execute();

			rescanService();
			return (T) this;
		}

	}

	class ScaleServiceCommandImpl extends ScaleServiceCommand {

		@Override
		public <T extends AppClusterCommand> T execute() {
			int numOfReplicas = 1;
			if (!getReplicas().isPresent()) {
				throw new IllegalArgumentException("must specify number of replicas");
			}
			if (getReplicas().isPresent()) {
				String replicas = getReplicas().get();
				try {
					numOfReplicas = Integer.parseInt(replicas);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Replicas specified is not a valid integer");
				}
			}
			if (numOfReplicas < 0 || numOfReplicas > Integer.MAX_VALUE / 4) {
				throw new IllegalArgumentException("Replicas specified is not a valid integer");
			}

			new SwarmServiceEditor()
					.withSwarmId(getDockerServiceClient().getSwarmId())
					.withServiceId(getDockerServiceClient().getServiceId())
					.withReplicaCount(numOfReplicas).execute();

			rescanService();
			return (T) this;
		}
	}
	
	class DeleteClusterCommandImpl extends DeleteClusterCommand {

		@Override
		public <T extends AppClusterCommand> T execute() {
			
			if ( getAppClusterId().isPresent()) {
				String appClusterId = getAppClusterId().get();
				
				// check if any associated release/service for this app cluster
				List<JsonNode> dockerServices = neo4j
				        .execCypherAsList("MATCH (y:DockerService { label_tsdAppClusterId:{id} }) return y", "id", appClusterId);
				logger.info("{} docker services exist for AppCluster {}",dockerServices.size(), appClusterId);
				
				if (!dockerServices.isEmpty()) {
					// delete the corresponding services for app cluster
					dockerServices.forEach(service-> {
						String serviceId = service.path("serviceId").asText();
						String swarmId = service.path("swarmClusterId").asText();
						deleteServiceCommand().withSwarmServiceId(serviceId).withSwarm(swarmId).execute();
					});				
				}
				
				// check if actually got removed before deleting the App cluster
				dockerServices = neo4j
				        .execCypherAsList("MATCH (y:DockerService { label_tsdAppClusterId:{id} }) return y", "id", appClusterId);
				
				if (dockerServices.isEmpty()) {
					// no docker services for the app cluster, proceed with app cluster clean up
					neo4j.execCypher("match (a:AppCluster {appClusterId: {id}}) detach delete a;", "id", appClusterId);
				} else {
					throw new TridentException("Failed to delete the corresponding releases for " + appClusterId);
				}
					
			} else {
				throw new TridentException("AppCluster not specified");
			}
			
			return (T) this;
		}
		
	}
	
	class DeleteServiceCommandImpl extends DeleteServiceCommand {

		@Override
		public <T extends AppClusterCommand> T execute() {
			
			if (!getSwarmServiceId().isPresent()) {
				throw new IllegalArgumentException("Swarm service id is not specified to delete");
			}
			
		    String serviceId = getSwarmServiceId().get();
		    logger.info("Trying to delete the service with Id {}", serviceId);
		    
		    // delete the service
		    WebTarget wt = swarmClusterManager.getSwarm(getSwarm().get()).getManagerWebTarget();
			JsonNode result = wt.path("/services").path(serviceId).request().delete(JsonNode.class);

			// remove from neo4j as well
			neo4j.execCypher("match (a:DockerService {serviceId: {id}}) detach delete a;", "id", serviceId);

			return (T) this;
		}

	}

	class DeployCommandImpl extends DeployCommand {

		public DeployCommandImpl() {
			super();
			data.set("rawDeploymentRequest", newServiceRequestSkeleton());
		}

		ObjectNode getRawDeploymentRequest() {
			return (ObjectNode) data.get("rawDeploymentRequest");
		}

		JsonNode getAppCluster(String id) {
			JsonNode n = neo4j
			        .execCypher("match (a:AppCluster {appClusterId:{id}}) return a", "id", getAppClusterId().get())
			        .blockingFirst(NullNode.getInstance());
			return n;
		}

		private void checkAppCluster() {
			Preconditions.checkArgument(getAppClusterId().isPresent(), "appClusterId must be specified");
			JsonNode n = getAppCluster(getAppClusterId().get());
			Preconditions.checkState(!Strings.isNullOrEmpty(n.path("appClusterId").asText()),
			        "appClusterId not found: " + getAppClusterId().get());
		}

		@Override
		public <T extends AppClusterCommand> T execute() {

			checkAppCluster();

			{
				JsonNode n = getAppCluster(getAppClusterId().get());
				String environment = n.path("environment").asText();
				String subEnvironment = n.path("subEnvironment").asText("default");
				String region = n.path("region").asText();
				String appId = n.path("appId").asText();
				String swarm = n.path("swarm").asText();
				String serviceGroup = n.path("serviceGroup").asText();
				
				if (!Strings.isNullOrEmpty(environment)) {
					withEnvironment(environment);
				}
				if (!Strings.isNullOrEmpty(subEnvironment)) {
					withSubEnvironment(subEnvironment);
				}
				if (!Strings.isNullOrEmpty(region)) {
					withRegion(region);
				}
				if (!Strings.isNullOrEmpty(appId)) {
					withAppId(appId);
				}
				if (!Strings.isNullOrEmpty(swarm)) {
					withSwarm(swarm);
				}
				if (!Strings.isNullOrEmpty(serviceGroup)) {
					withServiceGroup(serviceGroup);
				}
					
			}

			logger.info("Interceptors Found {}", platformCommandInterceptors);
			platformCommandInterceptors.getInterceptors().forEach(interceptor -> {
				logger.info("applying interceptor: {}", interceptor);
				interceptor.accept(this);
			});

			if (!getSwarm().isPresent()) {
				throw new IllegalArgumentException("must specify swarm");
			}

			ObjectNode containerSpec = (ObjectNode) getRawDeploymentRequest().path("TaskTemplate")
			        .path("ContainerSpec");

			if (getPort().orElse(-1) > 0) {
				ObjectNode rawRequest = getRawDeploymentRequest();
				ObjectNode endpointSpec = (ObjectNode) (rawRequest.has("EndpointSpec") ? rawRequest.get("EndpointSpec")
				        : rawRequest.set("EndpointSpec", JsonUtil.createObjectNode()));

				endpointSpec.put("Mode", "vip");

				ArrayNode ports = JsonUtil.createArrayNode();
				ObjectNode port = JsonUtil.createObjectNode();
				endpointSpec.set("Ports", ports);
				port.put("Protocol", "tcp");
				port.put("TargetPort", getPort().get());
				port.put("PublishMode", "host");
				ports.add(port);
				// Add port exposure
				// "EndpointSpec": {
				// "Mode": "vip",
				// "Ports": [
				// {
				// "Protocol": "tcp",
				// "TargetPort": 80,
				// "PublishMode": "host"
				// }
				// ]
				// }
			}

			if (getImage().isPresent()) {

				if (getImage().get().contains("@")) {
					containerSpec.put("Image", getImage().get());
				} else {
					WebTarget wt = swarmClusterManager.getSwarm(getSwarm().get()).getManagerWebTarget();
					JsonNode result = wt.path("distribution").path(getImage().get()).path("json").request()
					        .get(JsonNode.class);
					String imageSpec = getImage().get() + "@" + result.path("Descriptor").path("digest").asText();

					containerSpec.put("Image", imageSpec);
				}
			}
			if (data.has("containerSpecArgs") && data.get("containerSpecArgs").isArray()) {
				containerSpec.set("Args", data.get("containerSpecArgs"));
			}
			if (data.has("containerSpecEnv") && data.get("containerSpecEnv").isArray()) {
				containerSpec.set("Env", data.get("containerSpecEnv"));
			}

			if (!getAppClusterId().isPresent()) {
				String appClusterId = UUID.randomUUID().toString();
				addLabel("tsdAppClusterId", appClusterId);
			}
			
			if (getServiceGroup().isPresent()) {
				String serviceGroup = getServiceGroup().get();
				addLabel("tsdServiceGroup", serviceGroup);
			}
			
			applyLabels(this, ObjectNode.class.cast(getRawDeploymentRequest().get("Labels")));
			if (getSwarmServiceName().isPresent()) {
				getRawDeploymentRequest().put("Name", getSwarmServiceName().get());
			}
			// The interceptors that ran above may have registered
			// customizations of the actual swarm request.
			// This is a bit complicated, but makes it a bit clearer who gets
			// the final say.
			swarmRequestInterceptors.forEach(d -> {
				d.accept(this, getRawDeploymentRequest());
			});

			if ((!getImage().isPresent()) && (!Strings.isNullOrEmpty(containerSpec.path("Image").asText()))) {
				throw new IllegalArgumentException("must specify image");
			}

			WebTarget wt = swarmClusterManager.getSwarm(getSwarm().get()).getManagerWebTarget();

			boolean newDeployment = true;

			if (newDeployment) {
				// If a service name was not set, set it to something globally
				// unique and meaningful
				if (Strings.isNullOrEmpty(getRawDeploymentRequest().path("Name").asText(null))) {
					String newServiceName = "trident-" + getAppClusterId().orElse("cluster") + "-" + getAppId().get()
					        + "-" + System.currentTimeMillis();
					getRawDeploymentRequest().put("Name", newServiceName);
					Preconditions.checkState(getRawDeploymentRequest().path("Name").asText().equals(newServiceName));
				}
				ObjectNode.class.cast(getRawDeploymentRequest().get("Labels")).put("tsdAppClusterId",
				        getAppClusterId().get());
				JsonUtil.logInfo(getClass(), "request to swarm", getRawDeploymentRequest());
				
				JsonNode result = wt.path("services").path("create")
				        .request(javax.ws.rs.core.MediaType.APPLICATION_JSON)
				        .buildPost(
				                Entity.entity(getRawDeploymentRequest(), javax.ws.rs.core.MediaType.APPLICATION_JSON))
				        .invoke(JsonNode.class);
				
				JsonUtil.logInfo(getClass(), "create service response from swarm", result);
				withSwarmServiceId(result.path("ID").asText(null));
				rescanService();
				return (T) this;
			} else {
				throw new UnsupportedOperationException("update not supported");
			}

		}

	}

	@Override
	public DockerServiceClient getDockerServiceClient(String id) {

		List<JsonNode> list = neo4j
		        .execCypher("match (a:DockerService) where a.name={id} or a.serviceId={id} return a", "id", id).toList()
		        .blockingGet();
		if (list.isEmpty()) {
			throw new NotFoundException("service not found: " + id);
		}
		if (list.size() > 1) {
			throw new NotFoundException("more than 1 service matches: " + id);
		}
		JsonNode n = list.get(0);

		DockerServiceClientImpl client = new DockerServiceClientImpl();
		client.serviceId = n.get("serviceId").asText();
		client.swarmId = n.get("swarmClusterId").asText();
		return client;
	}

	@Override
	protected void rescanService(AppClusterCommand cmd) {

		if (cmd.getSwarmServiceId().isPresent()) {
			rescanService(cmd.getSwarmServiceId().get());

		} else if (cmd.getSwarmServiceName().isPresent()) {
			rescanService(cmd.getSwarmServiceName().get());
		}

	}
	
	@Override
	public CreateClusterCommand createClusterCommand() {
		return new CreateClusterCommandImpl();
	}

	@Override
	public ScaleServiceCommand scaleServiceCommand() {
		return new ScaleServiceCommandImpl();
	}

	@Override
	public DeleteClusterCommand deleteClusterCommand() {
		
		return new DeleteClusterCommandImpl();
	}
	
	@Override
	public ApplyLabelsCommand labelCommand() {
		return new LabelCommandImpl();
	}

	@Override
	public BlueGreenCommand blueGreenCommand() {
		return new BlueGreenCommandImpl();
	}
	
	@Override
	public DeployCommand deployCommand() {
		return new DeployCommandImpl();
	}
	
	@Override
	public DeleteServiceCommand deleteServiceCommand() {
		return new DeleteServiceCommandImpl();
	}
	
	/**
	 * Create a skeleton request object.
	 *
	 * @return
	 */
	static private ObjectNode newServiceRequestSkeleton() {

		ObjectNode n = JsonUtil.createObjectNode();
		n.set("Name", NullNode.getInstance());
		n.set("Labels", JsonUtil.createObjectNode());
		ObjectNode taskTemplate = JsonUtil.createObjectNode();
		ObjectNode containerSpec = JsonUtil.createObjectNode();
		taskTemplate.set("ContainerSpec", containerSpec);
		n.set("TaskTemplate", taskTemplate);

		containerSpec.set("Image", NullNode.instance);

		containerSpec.set("Args", JsonUtil.createArrayNode());

		containerSpec.set("Env", JsonUtil.createArrayNode());

		containerSpec.set("DNSConfig", JsonUtil.createObjectNode());

		ObjectNode resources = JsonUtil.createObjectNode();
		resources.set("Limits", JsonUtil.createObjectNode());
		resources.set("Reservations", JsonUtil.createObjectNode());
		taskTemplate.set("Resources", resources);

		ObjectNode placement = JsonUtil.createObjectNode();
		ObjectNode platform = JsonUtil.createObjectNode();
		platform.put("Architecture", "amd64");
		platform.put("OS", "linux");
		placement.set("Platforms", JsonUtil.createArrayNode().add(platform));
		taskTemplate.set("Placement", placement);
		taskTemplate.put("ForcedUpdate", 0);
		n.set("Mode", JsonUtil.createObjectNode().set("Replicated", JsonUtil.createObjectNode()));
		n.set("EndpointSpec", JsonUtil.createObjectNode().put("Mode", "vip"));

		return n;
	}

	private void rescanService(String service) {

		neo4j.execCypher("match (s:DockerService) where s.serviceId={id} or s.name={id} return s", "id", service)
		        .forEach(x -> {

			        try {
				        String serviceId = x.path("serviceId").asText();
				        swarmClusterManager.getSwarm(x.path("swarmClusterId").asText()).getSwarmScanner()
		                        .scanService(serviceId);
				        JsonNode n = neo4j.execCypher("match (s:DockerService {serviceId:{serviceId}}) return s",
		                        "serviceId", serviceId).blockingFirst(MissingNode.getInstance());
				        String clusterId = n.path("label_tsdAppClusterId").asText();
				        if (!Strings.isNullOrEmpty(clusterId)) {
					        neo4j.execCypher(
		                            "match (c:AppCluster {appClusterId:{appClusterId}}), (s:DockerService {serviceId:{serviceId}}) merge (c)-[r:CONTAINS]->(s)",
		                            "serviceId", serviceId, "appClusterId", clusterId);

				        }
			        } catch (RuntimeException e) {
				        logger.warn("problem scanning service", e);
			        }
		        });

	}

}
