package org.lendingclub.trident.swarm.platform;

import java.util.List;
import java.util.Optional;

import org.lendingclub.trident.extension.BasicInterceptorGroup;
import org.lendingclub.trident.extension.InterceptorGroup;
import org.lendingclub.trident.swarm.DockerServiceClient;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public abstract class AppClusterManager {

	Logger logger = org.slf4j.LoggerFactory.getLogger(AppClusterManager.class);

	InterceptorGroup<AppClusterCommandInterceptor> platformCommandInterceptors = new BasicInterceptorGroup<>();

	public abstract class AppClusterCommand {

		private static final String APP_ID = "appId";
		private static final String REGION = "region";
		private static final String BLUE_GREEN_STATE = "blueGreenState";
		private static final String ENVIRONMENT = "env";
		private static final String SUB_ENVIRONMENT = "subEnv";
		private static final String SERVICE_GROUP = "serviceGroup";
		private static final String SWARM = "swarm";
		private static final String PORT = "port";
		private static final String PATH = "path";
		private static final String SOURCE_REVISION = "sourceRevision";
		private static final String SOURCE_BRANCH = "sourceBranch";
		private static final String SOURCE_VERSION = "sourceVersion";
		private static final String BINARY_HASH = "binaryHash";
		private static final String BINARY_VERSION = "binaryVersion";
		private static final String SWARM_SERVICE_ID = "swarmServiceId";
		private static final String SWARM_SERVICE_NAME = "swarmServiceName";
		private static final String APP_CLUSTER_ID = "serviceCluster";
		private static final String PROTOCOL = "prorotol";
		private static final String REPLICAS = "replicas";

		List<SwarmRequestInterceptor> swarmRequestInterceptors = Lists.newArrayList();

		ObjectNode data = JsonUtil.createObjectNode();

		AppClusterCommand() {
			ObjectNode addLabels = JsonUtil.createObjectNode();
			ObjectNode removeLabels = JsonUtil.createObjectNode();
			data.set("labelsToAdd", addLabels);
			data.set("labelsToRemove", removeLabels);
			data.put("command", getClass().getName());
			data.put("replicas", 1);
		}

		public ObjectNode getCommandData() {
			return data;
		}

		public <T extends AppClusterCommand> T addSwarmRequestInterceptor(SwarmRequestInterceptor interceptor) {
			this.swarmRequestInterceptors.add(interceptor);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withAppClusterId(String id) {
			data.put(APP_CLUSTER_ID, id);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withAppId(String id) {

			data.put(APP_ID, id);
			return (T) this;
		}

		public <T extends AppClusterCommand> T withAttribute(String key, String val) {
			data.put(key, val);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withProtocol(Protocol protocol) {
			Preconditions.checkArgument(protocol != null, "protocol cannot be null");
			data.put(PROTOCOL, protocol.toString());
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withBlueGreenState(BlueGreenState state) {
			Preconditions.checkArgument(state != null, "state cannot be null");
			data.put(BLUE_GREEN_STATE, state.toString());
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withSourceRevision(String val) {
			data.put(SOURCE_REVISION, val);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withSourceBranch(String val) {
			data.put(SOURCE_BRANCH, val);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withSourceVersion(String val) {
			data.put(SOURCE_VERSION, val);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withBinaryHash(String val) {
			data.put(BINARY_HASH, val);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withBinaryVersion(String val) {
			data.put(BINARY_VERSION, val);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withEnvironment(String env) {

			data.put(ENVIRONMENT, env);
			return (T) this;
		}

		protected <T extends AppClusterCommand> T withSubEnvironment(String subEnv) {

			data.put(SUB_ENVIRONMENT, subEnv);
			return (T) this;
		}

		public <T extends AppClusterCommand> T withServiceGroup(String serviceGroup) {
			data.put(SERVICE_GROUP, serviceGroup);
			return (T) this;
		}

		public <T extends AppClusterCommand> T withRegion(String region) {

			data.put(REGION, region);
			return (T) this;
		}

		public <T extends AppClusterCommand> T withSwarm(String swarm) {

			data.put(SWARM, swarm);
			return (T) this;
		}

		public <T extends AppClusterCommand> T withPort(int port) {

			data.put(PORT, new Integer(port).toString());
			return (T) this;
		}

		public <T extends AppClusterCommand> T withPort(String port) {
			if (!Strings.isNullOrEmpty(port)) {
				data.put(PORT, port);
			}
			return (T) this;
		}

		public <T extends AppClusterCommand> T withImage(String image) {
			data.put("image", image);
			return (T) this;
		}

		public <T extends AppClusterCommand> T withPath(String path) {

			data.put(PATH, path);
			return (T) this;
		}

		public <T extends AppClusterCommand> T addLabel(String key, String val) {
			ObjectNode.class.cast(data.path("labelsToAdd")).put(key, val);
			return (T) this;
		}

		public <T extends AppClusterCommand> T withLabel(String key, String val) {
			return addLabel(key, val);
		}

		public <T extends AppClusterCommand> T removeLabel(String key) {
			ObjectNode.class.cast(data.path("labelsToRemove")).put(key, "REMOVE");
			return (T) this;
		}

		public <T extends AppClusterCommand> T withReplicas(String replicas) {
			return editReplicas(replicas);
		}

		public <T extends AppClusterCommand> T editReplicas(String replicas) {
			if (!Strings.isNullOrEmpty(replicas)) {
				ObjectNode.class.cast(data.put(REPLICAS, Integer.parseInt(replicas)));
			}
			return (T) this;
		}

		public Optional<String> getReplicas() {
			if (Strings.isNullOrEmpty(getString(REPLICAS).orElse(null))) {
				return Optional.empty();
			}
			return Optional.ofNullable(getString(REPLICAS).get());
		}

		public <T extends AppClusterCommand> T withSwarmServiceId(String swarmService) {

			data.put(SWARM_SERVICE_ID, swarmService);
			return (T) this;
		}

		public <T extends AppClusterCommand> T withSwarmServiceName(String swarmService) {

			data.put(SWARM_SERVICE_NAME, swarmService);
			return (T) this;
		}

		protected void rescanService() {
			AppClusterManager.this.rescanService(this);
		}

		public <T extends AppClusterCommand> T withDockerServiceClient(DockerServiceClient client) {
			return withSwarm(client.getSwarmId()).withSwarmServiceName(client.getServiceId());
		}

		public Optional<String> getString(String key) {
			return java.util.Optional.ofNullable(Strings.emptyToNull(data.path(key).asText(null)));
		}

		public Optional<Integer> getInt(String key) {
			if (!data.has(key)) {
				return Optional.empty();
			}
			int val = data.path(key).asInt(Integer.MIN_VALUE);
			if (val == Integer.MIN_VALUE) {
				return Optional.empty();
			}
			return java.util.Optional.ofNullable(new Integer(val));
		}

		public Optional<String> getAppClusterId() {
			return getString(APP_CLUSTER_ID);
		}

		public Optional<String> getSwarm() {
			return getString(SWARM);
		}

		public Optional<String> getRegion() {
			return getString(REGION);
		}

		public Optional<String> getEnvironment() {
			return getString(ENVIRONMENT);
		}

		public Optional<String> getSubEnvironment() {
			return getString(SUB_ENVIRONMENT);
		}

		public Optional<String> getAppId() {
			return getString(APP_ID);
		}

		public Optional<String> getSwarmServiceId() {
			return getString(SWARM_SERVICE_ID);
		}

		public Optional<String> getSwarmServiceName() {
			return getString(SWARM_SERVICE_NAME);
		}

		public Optional<String> getServiceGroup() {
			return getString(SERVICE_GROUP);
		}

		public Optional<org.lendingclub.trident.swarm.platform.Protocol> getProtocol() {
			if (Strings.isNullOrEmpty(getString(PROTOCOL).orElse(null))) {
				return Optional.empty();
			}
			return Optional.ofNullable(
			        org.lendingclub.trident.swarm.platform.Protocol.valueOf(getString(PROTOCOL).get().toUpperCase()));
		}

		public Optional<BlueGreenState> getBlueGreenState() {
			if (Strings.isNullOrEmpty(getString(BLUE_GREEN_STATE).orElse(null))) {
				return Optional.empty();
			}
			return Optional.ofNullable(BlueGreenState.valueOf(getString(BLUE_GREEN_STATE).get().toUpperCase()));
		}

		public Optional<String> getSourceBranch() {
			return getString(SOURCE_BRANCH);
		}

		public Optional<String> getBinaryHash() {
			return getString(BINARY_HASH);
		}

		public Optional<String> getSourceRevision() {
			return getString(SOURCE_REVISION);
		}

		public Optional<String> getSourceVersion() {
			return getString(SOURCE_VERSION);
		}

		public Optional<Integer> getPort() {
			return getInt(PORT);
		}

		public Optional<String> getPath() {
			return getString(PATH);
		}

		public Optional<String> getImage() {
			return getString("image");
		}

		public DockerServiceClient getDockerServiceClient() {
			if (getSwarmServiceId().isPresent()) {
				return AppClusterManager.this.getDockerServiceClient(getSwarmServiceId().get());
			} else if (getSwarmServiceName().isPresent()) {
				return AppClusterManager.this.getDockerServiceClient(getSwarmServiceName().get());
			} else {
				throw new IllegalStateException("service not specified");
			}
		}

		public abstract <T extends AppClusterCommand> T execute();
	}

	public abstract class CreateClusterCommand extends AppClusterCommand {

		@Override
		public CreateClusterCommand withAppClusterId(String id) {

			return super.withAppClusterId(id);
		}

		@Override
		public CreateClusterCommand withAppId(String id) {

			return super.withAppId(id);
		}

		@Override
		public CreateClusterCommand withEnvironment(String env) {

			return super.withEnvironment(env);
		}

		@Override
		public CreateClusterCommand withSubEnvironment(String subEnv) {

			return super.withSubEnvironment(subEnv);
		}

		@Override
		public CreateClusterCommand withRegion(String region) {

			return super.withRegion(region);
		}

	}
	
	public abstract class DeleteClusterCommand extends AppClusterCommand {

		@Override
		public DeleteClusterCommand withAppClusterId(String id) {

			return super.withAppClusterId(id);
		}

		@Override
		public DeleteClusterCommand withAppId(String id) {

			return super.withAppId(id);
		}

		@Override
		public DeleteClusterCommand withEnvironment(String env) {

			return super.withEnvironment(env);
		}

		@Override
		public DeleteClusterCommand withSubEnvironment(String subEnv) {

			return super.withSubEnvironment(subEnv);
		}

		@Override
		public DeleteClusterCommand withRegion(String region) {

			return super.withRegion(region);
		}

	}
	
	public abstract class DeleteServiceCommand extends AppClusterCommand {
		
		@Override
		public DeleteServiceCommand withSwarmServiceId(String id) {
			return super.withSwarmServiceId(id);
		}
		
		@Override
		public DeleteServiceCommand withSwarm(String swarmName) {
			return super.withSwarm(swarmName);
		}
	}


	public abstract class DeployCommand extends AppClusterCommand {

		DeployCommand() {
			data.set("serviceArgs", JsonUtil.createArrayNode());
			data.set("serviceEnv", JsonUtil.createArrayNode());

		}

		public DeployCommand withEnvVar(String... args) {
			ArrayNode n = JsonUtil.createArrayNode();
			if (args != null) {
				for (String arg : args) {
					n.add(arg);
				}
			}
			data.set("containerSpecEnv", n);
			return this;
		}

		public DeployCommand withArgs(String... args) {
			ArrayNode n = JsonUtil.createArrayNode();
			if (args != null) {
				for (String arg : args) {
					n.add(arg);
				}
			}
			data.set("containerSpecArgs", n);
			return this;
		}

		@Override
		public DeployCommand withEnvironment(String env) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withEnvironment(env);
		}

		@Override
		public DeployCommand withSubEnvironment(String subEnv) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withSubEnvironment(subEnv);
		}

		@Override
		public DeployCommand withServiceGroup(String serviceGroup) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withServiceGroup(serviceGroup);
		}

		@Override
		public DeployCommand withSwarm(String swarm) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withSwarm(swarm);
		}

		@Override
		public DeployCommand withPort(int port) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withPort(port);
		}

		@Override
		public DeployCommand withPort(String port) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withPort(port);
		}

		@Override
		public DeployCommand withProtocol(Protocol protocol) {
			return (DeployCommand) super.withProtocol(protocol);

		}

		@Override
		public DeployCommand withImage(String image) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withImage(image);
		}

		@Override
		public DeployCommand withPath(String path) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withPath(path);
		}

		@Override
		public DeployCommand withSwarmServiceName(String swarmService) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withSwarmServiceName(swarmService);
		}

		@Override
		public DeployCommand withDockerServiceClient(DockerServiceClient client) {
			// TODO Auto-generated method stub
			return (DeployCommand) super.withDockerServiceClient(client);
		}

		@Override
		public DeployCommand withAppClusterId(String id) {

			return super.withAppClusterId(id);
		}

		@Override
		public DeployCommand withSourceRevision(String val) {

			return super.withSourceRevision(val);
		}

		@Override
		public DeployCommand withSourceBranch(String val) {
			// TODO Auto-generated method stub
			return super.withSourceBranch(val);
		}

		@Override
		public DeployCommand withBlueGreenState(BlueGreenState state) {
			return super.withBlueGreenState(state);
		}

		@Override
		public DeployCommand withSourceVersion(String val) {
			// TODO Auto-generated method stub
			return super.withSourceVersion(val);
		}

		@Override
		public DeployCommand withBinaryHash(String val) {
			// TODO Auto-generated method stub
			return super.withBinaryHash(val);
		}

		@Override
		public DeployCommand withBinaryVersion(String val) {
			// TODO Auto-generated method stub
			return super.withBinaryVersion(val);
		}

	}

	public abstract class BlueGreenCommand extends AppClusterCommand {

		@Override
		public BlueGreenCommand withBlueGreenState(BlueGreenState state) {
			return (BlueGreenCommand) super.withBlueGreenState(state);
		}
	}

	public abstract class ApplyLabelsCommand extends AppClusterCommand {

		@Override
		public ApplyLabelsCommand addLabel(String key, String val) {

			return super.addLabel(key, val);
		}

		@Override
		public ApplyLabelsCommand withLabel(String key, String val) {

			return super.withLabel(key, val);
		}

		@Override
		public ApplyLabelsCommand removeLabel(String key) {

			return super.removeLabel(key);
		}

		@Override
		public ApplyLabelsCommand withDockerServiceClient(DockerServiceClient client) {

			return (ApplyLabelsCommand) super.withDockerServiceClient(client);
		}

	}

	public abstract class ScaleServiceCommand extends AppClusterCommand {

		@Override
		public ScaleServiceCommand withReplicas(String replicas) {
			return super.withReplicas(replicas);
		}

		@Override
		public ScaleServiceCommand withDockerServiceClient(DockerServiceClient client) {

			return (ScaleServiceCommand) super.withDockerServiceClient(client);
		}
	}

	public abstract CreateClusterCommand createClusterCommand();

	public abstract BlueGreenCommand blueGreenCommand();

	public abstract ApplyLabelsCommand labelCommand();

	public abstract DeployCommand deployCommand();

	public abstract ScaleServiceCommand scaleServiceCommand();
	
	public abstract DeleteClusterCommand deleteClusterCommand();
	
	public abstract DeleteServiceCommand deleteServiceCommand();

	protected abstract void rescanService(AppClusterCommand command);


	public InterceptorGroup<AppClusterCommandInterceptor> getAppClusterCommandInterceptors() {
		return this.platformCommandInterceptors;
	}


	/**
	 * Given a service name/id, find out which swarm is hosting the service and
	 * return a DockerServiceClient to it.
	 *
	 * @param id
	 * @return
	 */
	public abstract DockerServiceClient getDockerServiceClient(String id);

}
