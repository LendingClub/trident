package org.lendingclub.trident.swarm;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

/**
 * AgentController provides a mechanism for an agent running on swarm nodes to post information back to trident.  In particular, local docker events do not 
 * propagate up through the swarm managers.  So local events (container start/stop) bust be obtained from the local docker daaemon.  Having Trident monitor
 * hundreds of worker nodes is not practical.  So instead we allow for a container running on the host to monitor and post information back to Trident.
 * 
 *
 */

@Controller
@Component
@RequestMapping(value="/api/trident/agent")
public class SwarmAgentController {

	@Autowired
	EventSystem eventSystem;
	
	Logger logger = LoggerFactory.getLogger(SwarmAgentController.class);
	@RequestMapping(value = "/docker-event", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> dockerEvent(@RequestBody JsonNode data) {
		logger.info("docker-event received from agent: {}",JsonUtil.prettyFormat(data));
	
		DockerEvent de = new DockerEvent();

		de.withPayload((ObjectNode)data.path("dockerEvent"));
		String swarmClusterId = data.path("dockerInfo").path("Swarm").path("Cluster").path("ID").asText();
		

		if (Strings.isNullOrEmpty(swarmClusterId)) {
			return ResponseEntity.badRequest().body(JsonUtil.createObjectNode().put("status", "failure").put("message", "misisng swarmClusterId"));
			
		}
		else {
			de.withEnvelopeAttribute("swarmClusterId", swarmClusterId);
			eventSystem.post(de);
			return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
		}
	
		
		
	}
	@RequestMapping(value = "/docker-info", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> dockerInfo(@RequestBody JsonNode data) {
		
		logger.info("docker-info received from agent: {}",JsonUtil.prettyFormat(data));
		return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
	}
}
