package org.lendingclub.trident.swarm.aws;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Controller
public class AWSController {

	static Logger logger = LoggerFactory.getLogger(AWSController.class);

	@Autowired
	AWSAccountManager awsClientManager;

	@Autowired
	AWSClusterManager awsClusterManager;

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	CertificateAuthorityManager certificateAuthorityManager;

	@Autowired
	TridentClusterManager tridentClusterManager;

	@RequestMapping(value = "/aws-accounts", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView awsAccounts() {

		Map<String, Object> data = Maps.newHashMap();
		List<JsonNode> awsConfig = Lists.newArrayList();

		awsClientManager.getSuppliers().forEach((k, v) -> {
			ObjectNode n = (ObjectNode) v.getConfig().deepCopy();
			awsConfig.add(n);
			enrich(n);
		});

		data.put("accounts", awsConfig);

		return new ModelAndView("aws-accounts", data);

	}

	protected void enrich(ObjectNode n) {
		try {
			String name = n.path("name").asText();

			n.put("account", awsClientManager.getSuppliers().get(name).getAccount().orElse(""));

			n.put("status", "OK");

		} catch (RuntimeException e) {
			n.put("status", e.toString());
		}
	}

	protected static List<String> extractList(JsonNode n) {
		List<String> list = Lists.newArrayList();

		if (n == null) {

		} else if (n.isTextual()) {
			Splitter.on(Pattern.compile("[,\\s]")).omitEmptyStrings().trimResults().splitToList(n.asText())
					.forEach(it -> {
						if (!Strings.isNullOrEmpty(it.trim())) {
							list.add(it.trim());
						}
					});
		} else if (n.isArray()) {
			ArrayNode.class.cast(n).forEach(it -> {
				list.addAll(extractList(it));
			});
		}
		return list;
	}

	public JsonNode createSwarm(JsonNode d) {
		String name = d.path("name").asText();

		Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name not set");
		Preconditions.checkArgument(
				neo4j.execCypherAsList("match (a:DockerSwarm {name:{name}}) return a", "name", name).size() == 0,
				"trident cluster with the name '" + name + "' already exists");

		SwarmASGBuilder managerBuilder = null;
		SwarmASGBuilder workerBuilder = null;
		try {
			ObjectNode data = (ObjectNode) d;

			ObjectNode result = JsonUtil.createObjectNode();
			ObjectNode managerNode = JsonUtil.createObjectNode();
			ObjectNode workerNode = JsonUtil.createObjectNode();

			String description = "description of " + name;
			if (Strings.isNullOrEmpty(name)) {
				throw new IllegalArgumentException("name not specified");
			}
			String template = d.path("template").asText(null);

			String id = Long.toHexString(new SecureRandom().nextLong());
			result.put("tridentClusterId", id);
			result.put("name", name);
			result.put("tridentClusterName", name);
			result.put("description", description);
			result.set("manager", managerNode);
			result.set("worker", workerNode);

			neo4j.execCypher(
					"merge (a:DockerSwarm {tridentClusterId:{id}}) set a.tridentOwnerId={ownerId}, "
							+ "a.name={name}, a.description={description}, a.templateName={template} return a",
					"id", id, "name", name, "description", description, "ownerId",
					tridentClusterManager.getTridentInstallationId(), "template", template);

			certificateAuthorityManager.createCertificateAuthority(id);

			ObjectNode dx = (ObjectNode) data;
			data.put("tridentClusterId", id);
			data.put("tridentClusterName", name);
			JsonUtil.logInfo(getClass(), "passed to builder", dx);
			managerBuilder = awsClusterManager.newManagerASGBuilder(dx);
			managerBuilder.execute();

			managerNode.set("launchConfig",
					JsonUtil.getObjectMapper().valueToTree(managerBuilder.describeLaunchConfig()));
			managerNode.set("autoScalingGroup",
					JsonUtil.getObjectMapper().valueToTree(managerBuilder.describeAutoScalingGroup()));

			workerBuilder = awsClusterManager.newWorkerASGBuilder(dx);
			workerBuilder.execute();

			workerNode.set("launchConfig",
					JsonUtil.getObjectMapper().valueToTree(workerBuilder.describeLaunchConfig()));
			workerNode.set("autoScalingGroup",
					JsonUtil.getObjectMapper().valueToTree(workerBuilder.describeAutoScalingGroup()));

			return result;

		} catch (RuntimeException e) {
			if (managerBuilder == null || managerBuilder.asgResult == null) {

				// Do NOT use detach delete in case a logic mistake above causes
				// us to delete a real
				neo4j.execCypher("match (s:DockerSwarm {name:{name}}) detach delete s", "name", name);
			}
			throw e;
		}
	}

	@RequestMapping(value = "/api/trident/aws/swarm/create", method = { RequestMethod.POST }, consumes = {
			"application/json" })
	public ResponseEntity<String> createSwarmPost(@RequestBody JsonNode d) {
		try {
			return ResponseEntity
					.ok(JsonUtil.getObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(createSwarm(d)));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}

	}

	private ResponseEntity<String> badRequest(String msg) {
		return ResponseEntity.badRequest()
				.body(JsonUtil.prettyFormat(JsonUtil.createObjectNode().put("status", "error").put("message", msg)));
	}
}
