package org.lendingclub.trident.swarm;

import org.lendingclub.trident.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

@Controller
public class SwarmApiController {

	@Autowired
	SwarmClusterManager swarmClusterManager;

	@RequestMapping(value = "/api/trident/swarm/create", method = { RequestMethod.POST }, consumes = {
			"application/json" })
	public ResponseEntity<String> createSwarm(@RequestBody JsonNode d) {
		try {
			return ResponseEntity
					.ok(JsonUtil.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(swarmClusterManager.createSwarm(d)));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

}
