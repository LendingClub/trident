package org.lendingclub.trident.swarm.auth;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.swarm.auth.DockerPluginAuthorizationContext.DockerPluginRequestAuthorizationContext;
import org.lendingclub.trident.swarm.auth.DockerPluginAuthorizationContext.DockerPluginResponseAuthorizationContext;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Extension point for performing fine-grained authorization of actions performed by docker daemons.
 * @author rschoening
 *
 */
@Controller
@Component
@RequestMapping(value = "/api/trident/docker-authz-plugin")
public class DockerPluginAuthorizationApiController {

	private Logger logger = LoggerFactory.getLogger(DockerPluginAuthorizationApiController.class);
	private ObjectMapper mapper = new ObjectMapper();
	
	@Autowired
	DockerPluginAuthorizationManager authManager;
	
	@RequestMapping(value = "/AuthZPlugin.AuthZReq", method= {RequestMethod.POST}, produces="application/json")
	public ResponseEntity<JsonNode> authorizeRequest(@RequestBody JsonNode request, HttpServletRequest servletRequest) throws IOException {	
	
		logger.info("AuthZPlugin.AuthZReq : {}",request);
		
		DockerPluginRequestAuthorizationContext ctx = new DockerPluginRequestAuthorizationContext(request);
	
		return ResponseEntity.ok(authManager.authorize(ctx));
	
	}
	
	@RequestMapping(value = "/AuthZPlugin.AuthZRes", method= {RequestMethod.POST}, produces="application/json")
	public ResponseEntity<JsonNode> authorizeResponse(@RequestBody JsonNode request, HttpServletRequest servletRequest) throws IOException {	
		
		logger.info("AuthZPlugin.AuthZRes : {}",request);
		DockerPluginResponseAuthorizationContext ctx = new DockerPluginResponseAuthorizationContext(request);
		
		return ResponseEntity.ok(authManager.authorize(ctx));
	}
	
	ResponseEntity<JsonNode> allow() {
		return ResponseEntity.ok( mapper.createObjectNode().put("Allow", true).put("Msg", "").put("Err", ""));
	}
}
