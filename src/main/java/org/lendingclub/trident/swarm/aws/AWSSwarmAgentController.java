package org.lendingclub.trident.swarm.aws;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AgentController provides a mechanism for an agent running on swarm nodes to
 * post information back to trident. In particular, local docker events do not
 * propagate up through the swarm managers. So local events (container
 * start/stop) bust be obtained from the local docker daaemon. Having Trident
 * monitor hundreds of worker nodes is not practical. So instead we allow for a
 * container running on the host to monitor and post information back to
 * Trident.
 * 
 *
 */

@Controller
@RequestMapping(value = "/api/trident/agent/aws")
public class AWSSwarmAgentController {

	Logger logger = LoggerFactory.getLogger(AWSSwarmAgentController.class);

	@RequestMapping( value = "/docker-info", method = {
			RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<JsonNode> awsDockerInfo(@RequestBody JsonNode data) {

		logger.info("received from agent: {}", data);
		return ResponseEntity.ok(JsonUtil.createObjectNode().put("status", "ok"));
	}
}
