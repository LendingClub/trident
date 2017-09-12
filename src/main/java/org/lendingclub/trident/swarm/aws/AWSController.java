package org.lendingclub.trident.swarm.aws;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.swarm.aws.event.ClusterCreatedEvent;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

	@RequestMapping(value = "/api/trident/aws/swarm/create", method = { RequestMethod.POST }, consumes = {
			"application/json" })
	public @ResponseBody String createSwarm(@RequestBody JsonNode d) {

		ObjectNode data = (ObjectNode) d;

		ObjectNode result = JsonUtil.createObjectNode();
		ObjectNode managerNode = JsonUtil.createObjectNode();
		ObjectNode workerNode = JsonUtil.createObjectNode();

		String name = data.path("name").asText();
		String description = "description of " + name;
		if (Strings.isNullOrEmpty(name)) {
			throw new IllegalArgumentException("name not specified");
		}

		checkName(name);

		String id = Long.toHexString(new SecureRandom().nextLong());
		result.put("tridentClusterId", id);
		result.put("name", name);
		result.put("tridentClusterName", name);
		result.put("description", description);
		result.set("manager", managerNode);
		result.set("worker", workerNode);

		neo4j.execCypher(
				"merge (a:DockerSwarm {tridentClusterId:{id}}) set a.tridentOwnerId={ownerId}, "
						+ "a.name={name}, a.description={description} return a",
				"id", id, "name", name, "description", description, "ownerId",
				tridentClusterManager.getTridentInstallationId());

		certificateAuthorityManager.createCertificateAuthority(id);

		ObjectNode dx = (ObjectNode) data;
		data.put("tridentClusterId", id);
		data.put("tridentClusterName", name);
		SwarmASGBuilder builder = awsClusterManager.newManagerASGBuilder(dx);
		builder.execute();

		managerNode.set("launchConfig", JsonUtil.getObjectMapper().valueToTree(builder.describeLaunchConfig()));
		managerNode.set("autoScalingGroup", JsonUtil.getObjectMapper().valueToTree(builder.describeAutoScalingGroup()));

		SwarmASGBuilder workerBuilder = awsClusterManager.newWorkerASGBuilder(dx);
		workerBuilder.execute();

		workerNode.set("launchConfig", JsonUtil.getObjectMapper().valueToTree(workerBuilder.describeLaunchConfig()));
		workerNode.set("autoScalingGroup",
				JsonUtil.getObjectMapper().valueToTree(workerBuilder.describeAutoScalingGroup()));

		new ClusterCreatedEvent().withTridentClusterId(id).withTridentClusterName(name).send();
		return JsonUtil.prettyFormat(result);
	}

	private void checkName(String name) {
		if (Strings.isNullOrEmpty(name)) {
			throw new TridentException("name cannot be null or empty");
		}

		if (neo4j.execCypherAsList("match (a:DockerSwarm {name:{name}}) return a", "name", name).size() > 0) {
			throw new TridentException("trident cluster with the name '" + name + "' already exists");
		}
	}
}
