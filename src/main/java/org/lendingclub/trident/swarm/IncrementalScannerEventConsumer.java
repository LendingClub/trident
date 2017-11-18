package org.lendingclub.trident.swarm;

import java.util.Optional;

import org.lendingclub.mercator.docker.DockerScanner;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.base.Strings;

import io.reactivex.functions.Consumer;

@Component
public class IncrementalScannerEventConsumer implements Consumer<DockerEvent> {

	Logger logger = LoggerFactory.getLogger(IncrementalScannerEventConsumer.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	SwarmClusterManager swarmClusterManager;

	@Override
	public void accept(DockerEvent t) throws Exception {
		try {
			JsonNode n = t.getEnvelope();
			JsonUtil.logDebug(getClass(), "received event", n);
			

			String scope = n.path("data").path("scope").asText();
			String type = n.path("data").path("Type").asText();
			String action = n.path("data").path("Action").asText();

			if (scope.equals("swarm") && type.equals("service") && action.equals("create")) {
				onServiceCreate(t);
			} else if (scope.equals("swarm") && type.equals("service") && action.equals("update")) {
				onServiceUpdate(t);
			} else if (scope.equals("swarm") && type.equals("service") && action.equals("remove")) {
				onServiceRemove(t);
			} else if (scope.equals("swarm") && type.equals("node")) {
				onNodeUpdate(t);
			} else if (scope.equals("local") && type.equals("container")) {
				if (action.equals("start")) {
					onContainerStart(t);
				} else if (action.equals("kill")) {
					onContainerKill(t);
				} else if (action.equals("create")) {
					onContainerCreate(t);
				} else if (action.equals("die")) {
					onContainerDie(t);
				} else if (action.equals("update")) {
					onContainerUpdate(t);
				}
				else if (action.equals("stop")) {
					onContainerStop(t);
				}
			}
		} catch (Exception e) {
			logger.info("uncaught exception", e);
		}
	}

	public void onContainerKill(DockerEvent event) {
		rescanTask(event);
	}
	protected void onContainerStop(DockerEvent event) {
		rescanTask(event);
	}

	protected void onContainerDie(DockerEvent event) {
		rescanTask(event);
	}

	@Deprecated
	private void rescanCluster(DockerEvent event) {
		swarmClusterManager.getSwarmScanner(event.getSwarmClusterId()).scan();
	}
	
	protected void rescanTask(DockerEvent event) {
		String taskId = event.getData().path("Actor").path("Attributes").path("com.docker.swarm.task.id").asText();
	
		if (!Strings.isNullOrEmpty(taskId)) {
			logger.info("performing targeted scan of task: {}",taskId);
			swarmClusterManager.getSwarmScanner(event.getSwarmClusterId()).scanTask(taskId);
		}
		else {
			// just a local container start...we don't handle this yet
		}
	}
	protected void onContainerCreate(DockerEvent event) {
		rescanTask(event);
	}

	protected void onContainerStart(DockerEvent event) {
		rescanTask(event);
	}

	protected void onContainerUpdate(DockerEvent event) {
		rescanTask(event);
	}

	protected void onServiceCreate(DockerEvent event) {
		rescanService(event);
	}

	protected void rescanService(DockerEvent event) {
		String serviceId = event.getData().path("Actor").path("ID").asText();
		if (!Strings.isNullOrEmpty(serviceId)) {
			logger.info("scanning service={} in response to service update...", serviceId);

			lookupDockerScanner(event).scanService(serviceId);
		}
	
	}
	protected void onServiceUpdate(DockerEvent event) {
		rescanService(event);
	}

	protected void onServiceRemove(DockerEvent event) {
		rescanService(event);
	}

	protected void rescanNode(DockerEvent event) {
		logger.info("rescanning cluster in response to node update...");
		JsonUtil.logInfo(getClass(), "NODE_EVENT",event.getData());
		// need to look at the event structure for this
	
	}
	protected void onNodeUpdate(DockerEvent event) {
		
		rescanNode(event);
	}

	private DockerScanner lookupDockerScanner(DockerEvent event) {
		return swarmClusterManager.getSwarmScanner(event.getSwarmClusterId());
	}

}
