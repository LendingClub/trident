package org.lendingclub.trident.provision;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.crypto.CertificateAuthority;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.crypto.CryptoService;
import org.lendingclub.trident.extension.BasicInterceptorGroup;
import org.lendingclub.trident.extension.InterceptorGroup;
import org.lendingclub.trident.crypto.CertificateAuthority.Builder;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

@Component
public class SwarmNodeManager {

	static Logger logger = LoggerFactory.getLogger(SwarmNodeManager.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	CryptoService cryptoService;

	@Autowired
	CertificateAuthorityManager certificateAuthorityManager;

	@Autowired
	SwarmTemplateManager swarmTemplateManager;
	
	InterceptorGroup<SwarmNodeProvisionInterceptor> swarmNodeProvisionInterceptors = new BasicInterceptorGroup<>();

	
	public InterceptorGroup<SwarmNodeProvisionInterceptor> getSwarmNodeProvisionInterceptors() {
		return swarmNodeProvisionInterceptors;
	}
	public String generateScript(SwarmNodeProvisionContext r) {
		// First run the decorators on the provisioning context
		SwarmNodeProvisionContext outputContext = decorate(r);
		String script = outputContext.generateScript();

		// Now give them each a chance to mutate the output
		for (SwarmNodeProvisionInterceptor decorator : swarmNodeProvisionInterceptors.getInterceptors()) {
			script = decorator.apply(r, script);
		}
		return script;
	}

	

	public SwarmNodeProvisionContext decorate(SwarmNodeProvisionContext pc) {

		applySwarmTemplateToProvisioningContext(pc);
		String ONLY_ONCE = "__only_once__";
		SwarmNodeProvisionContext currentContext = pc;
		logger.info("decorate {}", currentContext);
		if (currentContext.getAttributes().get(ONLY_ONCE) != null) {
			logger.info("cutomize already executed");
			return currentContext;
		}
		currentContext.withAttribute(ONLY_ONCE, "true");

		List<SwarmNodeProvisionInterceptor> interceptors = swarmNodeProvisionInterceptors.getInterceptors();
		logger.info("decorators: {}", interceptors);

		for (SwarmNodeProvisionInterceptor d : interceptors) {

			Map<String, Object> before = ImmutableMap.copyOf(currentContext.getAttributes());
			logger.info("applying decorator: {}", d);

			currentContext = d.apply(currentContext);

			MapDifference<String, Object> x = Maps.difference(before, currentContext.getAttributes());
			x.entriesDiffering().forEach((k,v)-> {
				logger.info("context variable changed : {}: {} => {} by {}",k,sanitize(k,v.leftValue()),sanitize(k,v.rightValue()),d);
			});
			x.entriesOnlyOnLeft().forEach((k,v)-> {
				logger.info("context variable removed: {}: {} => REMOVED by {}",k,v.toString(),d);
			});
			x.entriesOnlyOnLeft().forEach((k,v)-> {
				logger.info("context varibale added  : {}: {} by {}",k,v.toString(),d);
			});

		}

		return currentContext;
	}

	private void applySwarmTemplateToProvisioningContext(SwarmNodeProvisionContext pc) {
		pc.getTridentClusterId().ifPresent(tridentClusterId->{
			
			JsonNode n = getNeoRxClient().execCypher("match (a:DockerSwarm {tridentClusterId:{tridentClusterId}}) return a","tridentClusterId",tridentClusterId).blockingFirst(MissingNode.getInstance());
			String template = n.path("templateName").asText();
			if (!Strings.isNullOrEmpty(template)) {
				swarmTemplateManager.getTemplate(template).ifPresent(t->{
					t.fields().forEachRemaining(x->{
						String key = Strings.nullToEmpty(x.getKey());
						if (key.equals("name")) {
							// overwriting name creates a problem, since "name" is used in DockerSwarm and DockerSwarmTemplate
							key = "templateName";
						}
						pc.getAttributes().put(key, x.getValue().asText());
					});
				});
			}
		});
	}

	static Object sanitize(String key, Object val) {
		try {
			if (key == null) {
				return val;
			}
			String lowerKey = key.toLowerCase();

			if (lowerKey.contains("password") || lowerKey.contains("token") || lowerKey.contains("secret")
					|| lowerKey.contains("swmtkn")) {
				return "******";
			} else {
				return val;
			}
		} catch (Exception e) {
			return "#######";
		}
	}

	static Map<String, Object> toSanitizedMap(Map<String, Object> input) {
		if (input == null) {
			return null;
		}
		Map<String, Object> out = Maps.newHashMap();
		input.entrySet().forEach(it -> {
			out.put(it.getKey(), sanitize(it.getKey(),it.getValue()));
		});
		return out;
	}

	public NeoRxClient getNeoRxClient() {
		Preconditions.checkNotNull(neo4j, "NeoRxClient cannot be null");
		return neo4j;
	}

	public void mergeTridentCluster(String id, String... pairs) {
		ObjectNode n = JsonUtil.getObjectMapper().createObjectNode();

		n.put("id", id);
		if (pairs != null) {
			Preconditions.checkArgument(pairs.length % 2 == 0, "key value pairs must be provided in pairs");

			for (int i = 0; i < pairs.length - 1; i += 2) {

				Preconditions.checkArgument(!Strings.isNullOrEmpty(pairs[i]));
				n.put(pairs[i], pairs[i + 1]);
			}
		}

		mergeTridentCluster(n);
	}

	private void mergeTridentCluster(JsonNode n) {

		String id = n.path("id").asText();
		Preconditions.checkArgument(!Strings.isNullOrEmpty(id));

		ObjectNode on = (ObjectNode) n.deepCopy();
		on.remove("id");

		if (!Strings.isNullOrEmpty(on.path("managerJoinToken").asText(null))) {
			String encryptedValue = cryptoService.encrypt(on.get("managerJoinToken").asText());
			on.put("managerJoinToken", encryptedValue);
		}

		if (!Strings.isNullOrEmpty(on.path("workerJoinToken").asText(null))) {
			on.put("workerJoinToken", cryptoService.encrypt(on.get("workerJoinToken").asText()));
		}
		String cypher = "merge (c:DockerSwarm {tridentClusterId:{id}}) "
				+ " on match set c+={props}, c.updateTs=timestamp() "
				+ " on create set c+={props}, c.updateTs=timestamp(), c.createTs=timestamp()";

		getNeoRxClient().execCypher(cypher, "id", id, "props", on);

	}

	private void assertEncrypted(String val, String message) {
		if (val == null) {
			return;
		}
		if (val.startsWith("QHsic")) {
			return;
		} else {
			throw new IllegalStateException(message);
		}
	}

	public Optional<JsonNode> findTridentCluster(String id) {

		String cypher = "match (c:DockerSwarm {tridentClusterId:{id}}) return c";

		ObjectNode n = (ObjectNode) getNeoRxClient().execCypher(cypher, "id", id).blockingFirst(null);
		if (n != null) {
			assertEncrypted(n.path("managerJoinToken").asText(null),
					"expcted TridentCluster.managerToken to be encrypted: " + n);
			assertEncrypted(n.path("workerJoinToken").asText(null),
					"expcted TridentCluster.workerToken to be encrypted:" + n);
			n = decrypt(n);
		}
		return Optional.ofNullable(n);

	}

	private ObjectNode decrypt(ObjectNode n) {
		ObjectNode decryptedObj = JsonUtil.getObjectMapper().createObjectNode();
		n.fields().forEachRemaining(it -> {
			JsonNode v = it.getValue();
			if (v.isTextual() && v.asText().startsWith("QHsic")) {

				String decrypted = cryptoService.decryptString(v.asText());

				decryptedObj.put(it.getKey(), decrypted);
			} else {
				decryptedObj.set(it.getKey(), it.getValue());
			}
		});
		return decryptedObj;
	}

	public void associateClusterId(String tridentClusterId, String swarmClusterId) {

		tridentClusterId = Strings.nullToEmpty(tridentClusterId).trim();
		swarmClusterId = Strings.nullToEmpty(swarmClusterId).trim();

		if (Strings.isNullOrEmpty(tridentClusterId)) {
			logger.warn("tridentClusterId was not set");
		} else if (Strings.isNullOrEmpty(swarmClusterId)) {
			logger.warn("swarmClusterId was not set");
		} else {
			neo4j.execCypher(
					"match (c:DockerSwarm {tridentClusterId:{tridentClusterId}}) set c.swarmClusterId={swarmClusterId}",
					"tridentClusterId", tridentClusterId, "swarmClusterId", swarmClusterId);
		}
	}

	public Builder createServerCert(String tridentClusterId) {
		CertificateAuthority ca = certificateAuthorityManager.getCertificateAuthority(tridentClusterId);
		return ca.createServerCert().withTridentClusterId(tridentClusterId);
	}

	/**
	 * Save state information about the node being registered. It may take some
	 * time to establish all necessary relationships.
	 */

	public void registerSwarmNodeInitialization(String tridentClusterId, JsonNode data) {
		String ipAddr = data.path("ipAddr").asText();
		logger.info("adding registration for tridentClusterId={} ipAddr={} - {}", tridentClusterId, ipAddr, data);
		String cypher = "merge (a:DockerRegistration {tridentClusterId:{tridentClusterId},ipAddr:{ipAddr}}) set a+={props}, a.createTs=timestamp()";
		neo4j.execCypher(cypher, "ipAddr", ipAddr, "tridentClusterId", tridentClusterId, "props", data);
	}
	
	@PostConstruct
	public void addDefaultInterceptors() {
		Preconditions.checkNotNull(swarmTemplateManager);
		getSwarmNodeProvisionInterceptors().addInterceptor(new SwarmTemplateInterceptor(swarmTemplateManager));
	}
}
