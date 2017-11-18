package org.lendingclub.trident.loadbalancer;

import java.security.SecureRandom;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.TridentIdentifier;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;

public class LoadBalancerManager {
	Logger logger = LoggerFactory.getLogger(LoadBalancerManager.class);
	
	ObjectMapper mapper = new ObjectMapper();
	
	List<LoadBalancerCommandInterceptor> loadBalancerCommandInterceptors = Lists.newCopyOnWriteArrayList();
	
	@Autowired
	NeoRxClient neo4j;
	
	@Autowired
	SwarmClusterManager swarmClusterManager;
	
	public static enum LoadBalancerType {
		HAPROXY, ENVOY;
	}
	
	public CreateLoadBalancerCommand createLoadBalancerCommand() {
		return new CreateLoadBalancerCommand();
	}
	
	public abstract class LoadBalancerCommand { 
		String loadBalancerName;
		String loadBalancerId;
		String swarmClusterId;
		String image;
		Integer publishedPort;
		ArrayNode args = mapper.createArrayNode(); 
		ArrayNode envVars = mapper.createArrayNode();
		LoadBalancerType loadBalancerType;
		TridentIdentifier identifier;
		String swarmServiceId;
		
		
		public <T extends LoadBalancerCommand> T withLoadBalancerId(String loadBalancerId) { 
			this.loadBalancerId = loadBalancerId;
			return (T) this;
		}
		
		public <T extends LoadBalancerCommand> T withLoadBalancerName(String loadBalancerName) { 
			this.loadBalancerName = loadBalancerName;
			return (T) this;
		}
		
		public <T extends LoadBalancerCommand> T withSwarmClusterId(String swarmClusterId) { 
			this.swarmClusterId = swarmClusterId;
			return (T) this;
		}
		
		public <T extends LoadBalancerCommand> T withLoadBalancerType(LoadBalancerType loadBalancerType) { 
			this.loadBalancerType = loadBalancerType;
			return (T) this;
		}
		
		public <T extends LoadBalancerCommand> T withTridentIdentifier(TridentIdentifier identifier) { 
			this.identifier = identifier;
			return (T) this;
		}

		public <T extends LoadBalancerCommand> T withImage(String image) { 
			this.image = image;
			return (T) this;
		}
		
		public <T extends LoadBalancerCommand> T withPublishedPort(Integer publishedPort) { 
			this.publishedPort = publishedPort;
			return (T) this;
		}
		
		public <T extends LoadBalancerCommand> T withArgs(String... args) { 
			for (String a : args) { 
				this.args.add(a);
			}
			return (T) this;
		}
		
		public <T extends LoadBalancerCommand> T withEnvVars(String... vars) { 
			for (String v : vars) { 
				this.envVars.add(v);
			}
			return (T) this;
		}
		
		public <T extends LoadBalancerCommand> T withSwarmServiceId(String swarmServiceId) { 
			this.swarmServiceId = swarmServiceId;
			return (T) this;
		}
		
		public Optional<String> getLoadBalancerName() {
			return Optional.ofNullable(Strings.emptyToNull(loadBalancerName));
		}
		
		public Optional<String> getLoadBalancerId() {
			return Optional.ofNullable(Strings.emptyToNull(loadBalancerId));
		}
		
		public Optional<String> getSwarmClusterId() {
			return Optional.ofNullable(Strings.emptyToNull(swarmClusterId));
		}
		
		public Optional<String> getImage() {
			return Optional.ofNullable(Strings.emptyToNull(image));
		}
		
		public Optional<Integer> getPublishedPort() { 
			return Optional.ofNullable(publishedPort);
		}
		
		public ArrayNode getArgs() {
			return args;
		}
		
		public ArrayNode getEnvVars() { 
			return envVars;
		}

		public Optional<LoadBalancerType> getLoadBalancerType() {
			return Optional.ofNullable(loadBalancerType);
		}

		public Optional<TridentIdentifier> getTridentIdentifier() {
			return Optional.ofNullable(identifier);
		}
		
		public Optional<String> getSwarmServiceId() { 
			return Optional.ofNullable(Strings.emptyToNull(swarmServiceId));
		}
		
		public WebTarget getWebTarget() { 
			Preconditions.checkArgument(getSwarmClusterId().isPresent(), "swarmClusterId must be set");
			return swarmClusterManager.getSwarm(getSwarmClusterId().get()).getManagerWebTarget();
		}
		
		public String getFullImageName() { 
			Preconditions.checkArgument(getImage().isPresent(), "image must be set");
			Preconditions.checkArgument(getSwarmClusterId().isPresent(), "swarmClusterId must be set");
			
			String image = getImage().get();
			
			if (image.contains("@")) {
				return image;
			} else { 
				JsonNode response = getWebTarget().path("distribution").path(getImage().get()).path("json")
						.request()
						.get(JsonNode.class);
				return String.format("%s@%s", image, response.path("Descriptor").path("digest").asText());
			}
		}
		
		public abstract <T extends LoadBalancerCommand> T execute();
	}
	
	public class CreateLoadBalancerCommand extends LoadBalancerCommand {
		
		private void checkLoadBalancerExists() { 
			if (loadBalancerExists(loadBalancerType, identifier, swarmClusterId)) { 
				throw new TridentException(String.format("Trident %s load balancer in %s-%s-%s-%s swarmClusterId=%s already exists",
						loadBalancerType, identifier.getServiceGroup().get(), identifier.getEnvironment().get(), identifier.getSubEnvironment().get(), identifier.getRegion().get(), swarmClusterId));
			}
		}
		
		private String generateLoadBalancerId() { 
			byte[] b = new byte[8];
			new SecureRandom().nextBytes(b);
			
			return BaseEncoding.base32().encode(b).replaceAll("=", "");
		}
		
		private String generateLoadBalancerName() { 
			Preconditions.checkArgument(getLoadBalancerId().isPresent(), "load balancer ID not set");
			if (identifier.getServiceGroup().orElse("").endsWith("-d")) { 
				return String.format("load-balancer-d-%s", getLoadBalancerId().get());
			}
			return String.format("load-balancer-%s", getLoadBalancerId().get());
		}
		
		private ObjectNode formulateCreateLoadBalancerJson() { 
			ObjectNode data = mapper.createObjectNode()
					.put("Name", getLoadBalancerName().get());
			
			// Labels node
			ObjectNode labels = mapper.createObjectNode()
					.put("tsdLoadBalancerType", loadBalancerType.toString())
					.put("tsdServiceGroup", identifier.getServiceGroup().get())
					.put("tsdEnv", identifier.getEnvironment().get())
					.put("tsdSubEnv", identifier.getSubEnvironment().get())
					.put("tsdRegion", identifier.getRegion().get())
					.put("tsdTargetPort", identifier.getPort().get().toString())
					.put("tsdPublishedPort", getPublishedPort().get().toString())
					.put("tsdLoadBalancerId", getLoadBalancerId().get());
			data.set("Labels", labels);
			
			//TaskTemplate node
			ObjectNode taskTemplate = mapper.createObjectNode();
			
			ObjectNode containerSpec = mapper.createObjectNode()
					.put("Image", getFullImageName());
			containerSpec.set("Args", getArgs());
			containerSpec.set("Env", getEnvVars());
			taskTemplate.set("ContainerSpec", containerSpec);
			
			ObjectNode platforms = mapper.createObjectNode()
					.put("Architecture",  "amd64")
					.put("OS",  "linux");
			ObjectNode placement = (ObjectNode) mapper.createObjectNode()
					.set("Platforms", mapper.createArrayNode().add(platforms));
			taskTemplate.set("Placement", placement);
			taskTemplate.put("ForceUpdate", 0);
			data.set("TaskTemplate", taskTemplate);
			
			//EndpointSpec node
			ObjectNode endpointSpec = mapper.createObjectNode()
					.put("Mode", "vip");
			ArrayNode ports = mapper.createArrayNode().add(
					mapper.createObjectNode()
							.put("Protocol", "tcp")
							.put("TargetPort", identifier.getPort().get())
							.put("PublishedPort", getPublishedPort().get())
							.put("PublishMode", "host"));
			endpointSpec.set("Ports", ports);
			data.set("EndpointSpec", endpointSpec);
			
			return data;
		}

		
		@Override
		public <T extends LoadBalancerCommand> T execute() {
			Preconditions.checkNotNull(identifier, "trident identifier cannot be null");
			Preconditions.checkArgument(!Strings.isNullOrEmpty(swarmClusterId), "swarmClusterId cannot be null or empty");
			Preconditions.checkArgument(!Strings.isNullOrEmpty(image), "image cannot be null or empty");
			Preconditions.checkArgument(identifier.getEnvironment().isPresent(), "environment cannot be null or empty");
			Preconditions.checkArgument(identifier.getSubEnvironment().isPresent(), "subEnvironment cannot be null or empty");
			Preconditions.checkArgument(identifier.getServiceGroup().isPresent(), "serviceGroup cannot be null or empty");
			Preconditions.checkArgument(identifier.getRegion().isPresent(), "region cannot be null or empty");
			Preconditions.checkArgument(loadBalancerType == LoadBalancerType.ENVOY || loadBalancerType == LoadBalancerType.HAPROXY, "invalid LoadBalancerType");
			
			String serviceGroup = identifier.getServiceGroup().get();
			String environment = identifier.getEnvironment().get();
			String subEnvironment = identifier.getSubEnvironment().get();
			String region = identifier.getRegion().get();
			
			checkLoadBalancerExists();

			//Set publishedPort, targetPort, loadBalancerId and loadBalancerName
			
			//This will set/override the published port regardless of whether it was set by the caller using withPublishedPort().
			//We could allow user to pick the published port, but it complicates the getLoadBalancerPublishedPort() method.
			withPublishedPort(getLoadBalancerPublishedPort(loadBalancerType, serviceGroup, environment, subEnvironment));
			
			//Sets target port to 8003 if not already set
			if (!identifier.getPort().isPresent()) { 
				identifier.withPort(8003);
			}
			
			//Again, this will set/override loadBalancerId regardless of whether it was previously set
			withLoadBalancerId(generateLoadBalancerId());
			
			//Set loadBalancerName if not already set
			if (getLoadBalancerName().isPresent()) { 
				if (!getLoadBalancerServiceByName(getLoadBalancerName().get()).isEmpty()) {
					throw new TridentException("Trident load balancer with name=" + getLoadBalancerName().get() + " already exists");
				}
			} else { 
				withLoadBalancerName(generateLoadBalancerName());
			}
			
			loadBalancerCommandInterceptors.forEach(it -> {
				logger.info("applying LoadBalancerCommandInterceptor {}", it.getClass());
				it.accept(this);
			});
			
			Preconditions.checkArgument(identifier.getPort().isPresent(), "target port cannot be null");
			Preconditions.checkArgument(getPublishedPort().isPresent(), String.format("could not determine published port for Trident {} load balancer {}-{}-{} in swarmClusterId={}",
					loadBalancerType, serviceGroup, environment, subEnvironment, swarmClusterId));
			Preconditions.checkArgument(getLoadBalancerId().isPresent(), "loadBalancerId cannot be null or empty");
			Preconditions.checkArgument(getLoadBalancerName().isPresent(), "loadBalancerName cannot be null or empty");
			
			//formulate json to send to docker to create service
			ObjectNode data = formulateCreateLoadBalancerJson();
			
			//create the container
			JsonUtil.logInfo(getClass(), "request to swarm", data);
			
			JsonNode response = getWebTarget().path("services").path("create")
					.request(MediaType.APPLICATION_JSON)
					.buildPost(Entity.entity(data, MediaType.APPLICATION_JSON))
					.invoke(JsonNode.class);
			
			JsonUtil.logInfo(getClass(), "create load balancer service response from swarm", response);
			
			withSwarmServiceId(response.path("ID").asText(null));
			
			//add the load balancer in neo4j
			neo4j.execCypher(
					"create (x:TridentLoadBalancer "
					+ "{loadBalancerId:{id}, loadBalancerName:{lbName}, loadBalancerType:{type}, serviceGroup:{svc}, "
					+ "environment:{env}, subEnvironment:{subEnv}, region:{region}, swarmClusterId:{swarm}, swarmServiceId:{svcId}, "
					+ "publishedPort:{publishedPort}, targetPort:{targetPort}, image:{img}, createTs:timestamp(), updateTs:timestamp()})", 
					"id", generateLoadBalancerId(), 
					"lbName", loadBalancerName,
					"type", loadBalancerType.toString(), 
					"svc", serviceGroup, 
					"env", environment, 
					"subEnv", subEnvironment, 
					"region", region,
					"swarm", swarmClusterId, 
					"svcId", getSwarmServiceId().get(),
					"publishedPort", getPublishedPort().get(),
					"targetPort", identifier.getPort().get(), 
					"img", image);
			
			//scan swarm
			swarmClusterManager.getSwarm(swarmClusterId).getSwarmScanner().scanService(swarmServiceId);
		
			return (T) this;
		} 
	}
	
	protected Integer getLoadBalancerPublishedPort(LoadBalancerType loadBalancerType, String serviceGroup, String environment, String subEnvironment) { 
		try {
			// we assign published port according to loadBalancerType-serviceGroup-env-subEnv tuple
			
			// check if tuple already exists in Neo4j
			List<JsonNode> loadBalancers = neo4j.execCypher(
					"match (x:TridentLoadBalancer {loadBalancerType:{type}, serviceGroup:{svc}, environment:{env}, subEnvironment:{subEnv}}) return x order by x.createTs desc;",
					"type", loadBalancerType.toString(), "svc", serviceGroup, "env",environment, "subEnv",subEnvironment).toList().blockingGet();
			if (!loadBalancers.isEmpty()) { 
				//if this tuple already exists in Neo4j, return the same port
				return loadBalancers.get(0).path("publishedPort").asInt();
			} else { 
				//if this tuple doesn't already exist in Neo4j, get the highest port of all 
				//existing load balancers, increment by 1 and return. LoadBalancer ports start at 10000
				return neo4j.execCypher(
						"match (x:TridentLoadBalancer) return x.publishedPort order by x.publishedPort desc limit 1")
						.map(n -> n.asInt()).blockingFirst(9999) + 1;
			}
		} catch (RuntimeException e) { 
			logger.warn("error fetching port for {} load balancer {}-{}-{}", 
					loadBalancerType.toString(), serviceGroup, environment,subEnvironment, e);
			return null;
		}
	}
	
	public List<JsonNode> getLoadBalancerServiceByName(String loadBalancerName) { 
		String cypher = "match (x:DockerService {name:{name}}) return x";
		return neo4j.execCypher(cypher, "name", loadBalancerName).toList().blockingGet();
	}
	
	public List<JsonNode> getLoadBalancerById(String loadBalancerId) { 
		String cypher = "match (x:TridentLoadBalancer {loadBalancerId:{id}}) return x";
		return neo4j.execCypher(cypher, "id", loadBalancerId).toList().blockingGet();
	}	
	
	public String getSwarmClusterIdFromName(String swarmName) throws NoSuchElementException { 
		String cypher = "match (x:DockerSwarm {name:{name}}) return x.swarmClusterId";
		return neo4j.execCypher(cypher, "name",swarmName).blockingFirst().asText();
	}
	
	public boolean loadBalancerExists(LoadBalancerType loadBalancerType, TridentIdentifier identifier, String swarmClusterId) { 
		Preconditions.checkNotNull(identifier, "identifier cannot be null");
		Preconditions.checkArgument(identifier.getEnvironment().isPresent(), "environment cannot be null or empty");
		Preconditions.checkArgument(identifier.getSubEnvironment().isPresent(), "subEnvironment cannot be null or empty");
		Preconditions.checkArgument(identifier.getRegion().isPresent(), "region cannot be null or empty");
		Preconditions.checkArgument(identifier.getServiceGroup().isPresent(), "serviceGroup cannot be null or empty");
		Preconditions.checkArgument(loadBalancerType == LoadBalancerType.ENVOY || loadBalancerType == LoadBalancerType.HAPROXY, "load balancer type not supported");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(swarmClusterId), "swarmClusterId cannot be null or empty");
		
		String cypher = "match (x:TridentLoadBalancer "
				+ "{loadBalancerType:{type}, serviceGroup:{svc}, environment:{env}, subEnvironment:{subEnv}, region:{region}, swarmClusterId:{id}})--(y:DockerService) "
				+ "return x;";
		List<JsonNode> results = neo4j.execCypher(
				cypher, 
				"type", loadBalancerType.toString(), 
				"svc", identifier.getServiceGroup().get(), 
				"env", identifier.getEnvironment().get(), 
				"subEnv", identifier.getSubEnvironment().get(), 
				"region", identifier.getRegion().get(), 
				"id", swarmClusterId)
				.toList().blockingGet();
		return !results.isEmpty();
	}
	
	public List<LoadBalancerCommandInterceptor> getLoadBalancerCommandInterceptors() { 
		return loadBalancerCommandInterceptors;
	}
	
	public void addInterceptor(LoadBalancerCommandInterceptor interceptor) { 
		logger.info("registering LoadBalancerCommandInterceptor: {}", interceptor);
		loadBalancerCommandInterceptors.add(interceptor);
	}
	
	/** 
	 * Returns the swarm name that a load balancer resides in given the load balancer's
	 * environment and region
	 */
	public String getLoadBalancerSwarmId(TridentIdentifier identifier) {
		Preconditions.checkNotNull(identifier, "identifier cannot be null");
		Preconditions.checkArgument(identifier.getEnvironment().isPresent(), "environment cannot be null or empty");
		Preconditions.checkArgument(identifier.getRegion().isPresent(), "region cannot be null or empty");
		
		String environment = identifier.getEnvironment().get();
		String swarmName = String.format("%s-lb", identifier.getRegion().get());
		if (environment.contains("prod")) { 
			swarmName += "-prod";
		} else if (environment.contains("inf")) {
			swarmName += "-inf";
		} else {
			swarmName += "-nprd";
		}
		
		try { 
			return getSwarmClusterIdFromName(swarmName);
		} catch (NoSuchElementException e) { 
			/**
			 * makes last effort to get "local" cluster, 
			 * which will exist on localhost, otherwise
			 * throws NoSuchElementException 
			 */
			return getSwarmClusterIdFromName("local");
		}
	}
}