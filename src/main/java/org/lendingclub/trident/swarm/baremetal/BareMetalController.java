package org.lendingclub.trident.swarm.baremetal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.crypto.CertificateAuthority;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.SecureRandom;

/**
 * Created by hasingh on 8/1/17.
 */
@Controller
public class BareMetalController {

	ObjectMapper mapper = new ObjectMapper();

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	CertificateAuthorityManager certificateAuthorityManager;

	@RequestMapping(value = "/api/trident/swarms/create", method = { RequestMethod.POST }, consumes = {
			"application/json" })
	public ResponseEntity createSwarmPost(@RequestBody JsonNode d) {

		return ResponseEntity.ok(createSwarm(d));
	

	}

	public JsonNode createSwarm(JsonNode d) {
		String name = d.path("name").asText("");
		String description = "description of " + name;
		String template = d.path("template").asText();

		Preconditions.checkArgument(!Strings.isNullOrEmpty(template),"template must be set");
		Preconditions.checkArgument(!Strings.isNullOrEmpty(name),"swarm name not set");
		
		String id = Long.toHexString(new SecureRandom().nextLong());
		String dockerSwarmCreationQuery = "merge(a: DockerSwarm {tridentClusterId:{id}}) "
				+ "set a.name={name}, a.description={description}, a.templateName={template} return a";
		neo4j.execCypher(dockerSwarmCreationQuery, "id", id, "name", name, "description", description, "template",
				template);
		certificateAuthorityManager.createCertificateAuthority(id);
		ObjectNode result = mapper.createObjectNode();

		result.put("tridentClusterId", id);
		result.put("name", name);
		result.put("tridentClusterName", name);
		result.put("description", description);

		return result;

	}
}
