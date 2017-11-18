package org.lendingclub.trident.provision;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.crypto.CertificateAuthority;
import org.lendingclub.trident.crypto.CryptoService;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmNodeType;
import org.lendingclub.trident.swarm.SwarmToken;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.BaseEncoding;

@Controller
@RequestMapping(value = "/api/trident/provision")
public class SwarmNodeProvisionApiController {

	Logger logger = LoggerFactory.getLogger(SwarmNodeProvisionApiController.class);

	@Autowired
	SwarmNodeManager provisioningManager;

	@Autowired
	CryptoService cryptoService;

	@Autowired
	SwarmClusterManager swarmClusterManager;
	
	public static final String SWARM_NODE_TYPE="swarmNodeType";
	CryptoService getCryptoService() {
		return cryptoService;
	}

	public SwarmNodeManager getProvisioningManager() {
		return provisioningManager;
	}

	SwarmNodeProvisionContext createContext(HttpServletRequest request, String script) {
		
		String nodeType = request.getParameter("swarmNodeType");
		if (Strings.isNullOrEmpty(nodeType)) {
			nodeType = request.getParameter("nodeType");
		}
		String tridentClusterId = request.getParameter("tridentClusterId");
		if (Strings.isNullOrEmpty(tridentClusterId)) {
			tridentClusterId = request.getParameter("id");
		}
		return createContext(request, script,tridentClusterId,nodeType);
	}
	SwarmNodeProvisionContext createContext(HttpServletRequest request, String script, String tridentClusterId, String swarmNodeType) {
		final SwarmNodeProvisionContext gen = new SwarmNodeProvisionContext();
		String baseUrl = Splitter.on("/api/trident/").splitToList(request.getRequestURL().toString()).get(0);
		gen.withBaseUrl(baseUrl);
		gen.withServletRequest(request);
		gen.withScriptTemplateName(script);
		if (!Strings.isNullOrEmpty(tridentClusterId)) {
			gen.data.put("tridentClusterId", tridentClusterId);
			gen.data.put("id", tridentClusterId);
		}
		if (!Strings.isNullOrEmpty(swarmNodeType)) {
			gen.data.put(SWARM_NODE_TYPE, swarmNodeType);
		}
		else {
			String nt = request.getParameter("swarmNodeType");
			if (Strings.isNullOrEmpty(nt)) {
				nt = request.getParameter("nodeType");
			}
			if (!Strings.isNullOrEmpty(nt)) {
				gen.data.put(SWARM_NODE_TYPE, nt);
			}
		}

		gen.withAttribute(SwarmNodeProvisionContext.REQUEST_ID_KEY,
				"request-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString());
		if (provisioningManager != null) {
			gen.data.put(SwarmNodeManager.class.getName(), provisioningManager);
		}
		request.getParameterMap().forEach((k, v) -> {
			gen.withAttribute(k, v[0]);
		});

		if (!gen.getString(SWARM_NODE_TYPE).isPresent()) {
			gen.data.put(SWARM_NODE_TYPE, SwarmNodeType.WORKER.toString());
		}

		return gen;
	}

	@RequestMapping(value = "/server-certs", method = { RequestMethod.POST })
	public HttpEntity<byte[]> downloadCerts(HttpServletRequest request) throws IOException, GeneralSecurityException {

		String remoteIp = request.getParameter("ipAddr");
		if (Strings.isNullOrEmpty(remoteIp)) {
			// This value is useless if we are going through a L4 proxy. But we
			// can try.
			remoteIp = request.getRemoteAddr();
		}
		logger.info("ip of caller: " + remoteIp);
		String tridentClusterId = request.getParameter("tridentClusterId");

		// We will put 127.0.0.1 and the actual remoteIp on the cert. If there
		// was an intent
		List<String> subjectAlternativeNames = Lists.newArrayList("127.0.0.1", remoteIp, "localhost");
		getProvisioningManager().getNeoRxClient()
				.execCypher("match (d:DockerSwarm {tridentClusterId:{id}}) return d", "id", tridentClusterId)
				.forEach(it -> {
					String desired = it.path("managerSubjectAlternativeNames").asText();
					if (!Strings.isNullOrEmpty(desired)) {
						Splitter.on(",").omitEmptyStrings().trimResults().splitToList(desired).forEach(san -> {
							subjectAlternativeNames.add(san);
						});

					}
				});

		CertificateAuthority.CertDetail cert = provisioningManager.createServerCert(tridentClusterId)
				.withCN(tridentClusterId).withSubjectAlternateNames(subjectAlternativeNames).build();
		HttpHeaders header = new HttpHeaders();
		header.setContentType(MediaType.parseMediaTypes("application/zip").get(0));
		header.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=certs.zip");

		Path p = Files.createTempFile("cert", "zip");

		cert.writeCertBundle(p.toFile(), null);
		byte[] data = Files.readAllBytes(p);
		header.setContentLength(data.length);

		return new HttpEntity<byte[]>(data, header);
	}

	@RequestMapping(value= "/node-init/{tridentClusterId}/{swarmNodeType}", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> nodeInit(HttpServletRequest request, @PathVariable("tridentClusterId")
	String tridentClusterId, @PathVariable("swarmNodeType") String swarmNodeType) {
		SwarmNodeProvisionContext ctx = createContext(request, "node-init",tridentClusterId,swarmNodeType);
		
		if (!Strings.isNullOrEmpty(swarmNodeType)) {
			ctx.data.put("swarmNodeType", swarmNodeType);
			try {
			SwarmNodeType.valueOf(swarmNodeType);
			}
			catch (IllegalArgumentException e) {
				return badRequest("swarmNodeType must be WORKER or MANAGER");
			}
		}
		if (!Strings.isNullOrEmpty(tridentClusterId)) {
			ctx.data.put("tridentClusterId", tridentClusterId);
		}
		
		
		Optional<String> tridentId = ctx.getTridentClusterId();

		if (tridentId.isPresent()) {
			// user supplied a clusteriId...check to see that we know what it is
			Optional<JsonNode> n = provisioningManager.findTridentCluster(tridentId.get());
			if (n.isPresent()) {
				logger.info("located trident cluster: {}", tridentId.get());

			} else {

				return execute(ctx.withScriptTemplateName("wait").withOperation("node-init"));
			

			}
		}
	

		if (tridentId.isPresent() == false) {
			// If there is no trident id given, then give the customizers a
			// chance to find it.
			// For instance, we may be able to look up the AMI or IP and resolve
			// it to a trident cluster.
			ctx = decorate(ctx);
		} else {
			// a trident id was passed
		}

		tridentId = ctx.getTridentClusterId();
		if (tridentId.isPresent() == false) {
			return execute(ctx.withScriptTemplateName("wait").withOperation("node-init"));
			
		}
		
		
		return execute(ctx);
		
	}
	
	@RequestMapping(value = "/node-init", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> nodeInit(HttpServletRequest request) {
		return nodeInit(request,null,null);
	}

	@RequestMapping(value = "/docker-install", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.TEXT_PLAIN_VALUE)

	public ResponseEntity<String> dockerInstall(HttpServletRequest request) {

		SwarmNodeProvisionContext ctx = createContext(request, "docker-install");

		if (!ctx.getTridentClusterId().isPresent()) {
			return ResponseEntity.status(400).body("# response");
		}
		if (!provisioningManager.findTridentCluster(ctx.getTridentClusterId().get()).isPresent()) {
			return ResponseEntity.status(404).body("# tridentClusterId missing");
		}
		return execute(createContext(request, "docker-install"));

	}

	ResponseEntity<String> badRequest(String s) {
		logger.warn("bad request: {}", s);
		return ResponseEntity.badRequest().body("# " + s);
	}

	@RequestMapping(value = "/swarm-join", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<String> swarmJoin(HttpServletRequest request) {

		SwarmNodeProvisionContext ctx = createContext(request, "swarm-init");
		Optional<String> clusterId = ctx.getTridentClusterId();
		if (!clusterId.isPresent()) {
			return badRequest("missing tridentClusterId");
		}

		Optional<JsonNode> n = provisioningManager.findTridentCluster(clusterId.get());
		if (!n.isPresent()) {
			return badRequest("tridentClusterId not found (" + clusterId.get() + ")");
		}
		
		SwarmNodeType nodeType = ctx.getNodeType();
		SwarmToken token = null;
		boolean isSwarmEstablished = false;
		try {
			token = swarmClusterManager.getJoinToken(clusterId.get(), nodeType);
			isSwarmEstablished=true;
		}
		catch (RuntimeException e) {
			// If we couldn't get tokens but the swarm has been operational, then we should abort to avoid split-brain partition
			if (n.get().has("lastContactTs")) {
				return badRequest("could not get join tokens");
			}
			else {
				// the swarm has simply not been created
				isSwarmEstablished=false;
			}
			
		}
	

		if (nodeType == SwarmNodeType.MANAGER) {
			if (isSwarmEstablished) {
				logger.info("manager join token present...joining existing swarm as manager...");
				String joinCommand = "docker swarm join --availability drain --advertise-addr $(netstat -rn | grep ^0.0.0.0 | head -1 | awk {'print $NF'}) --token " + token.getToken() + " " + token.getAddress();
				ctx.withAttribute("joinCommand", joinCommand);
				ctx.withScriptTemplateName("swarm-join");
				return execute(ctx);
			}
			else {
				logger.info("no manager join token found...going ahead and becoming the lead manager");
				ctx.withScriptTemplateName("swarm-init");
				return execute(ctx);
			}
		} else if (nodeType == SwarmNodeType.WORKER) {

			if (isSwarmEstablished) {
				logger.info("processing join");
				String joinCommand = "docker swarm join --advertise-addr $(netstat -rn | grep ^0.0.0.0 | head -1 | awk {'print $NF'}) --token " + token.getToken() + " " + token.getAddress();
				ctx.withAttribute("joinCommand", joinCommand);
				ctx.withScriptTemplateName("swarm-join");
				return execute(ctx);
			} else {
				// the cluster may be in the process of being set up and we have
				// race condition, so drop into loop
				ctx.withOperation("swarm-join");
				ctx.withScriptTemplateName("wait");
				logger.info("cluster does not (yet) have a worker token");
				return execute(ctx);

			}
		} else {
			return badRequest("#invalid node type: " + nodeType);
		}

	}



	Optional<SwarmToken> extractJoinToken(String output) {

		if (!output.contains("-")) {
			output = new String(BaseEncoding.base64().decode(output.trim()));
		}
		SwarmNodeType nodeType = output.contains("add a manager to") ? SwarmNodeType.MANAGER : SwarmNodeType.WORKER;
		Pattern p = Pattern.compile(".*--token\\s+(SWMTKN\\S+).*?(\\S+:\\d+).*?", Pattern.DOTALL | Pattern.MULTILINE);
		Matcher m = p.matcher(output);
		if (m.matches()) {
			SwarmToken si = new SwarmToken(nodeType, m.group(1), m.group(2));

			return Optional.of(si);
		}
		return Optional.empty();
	}

	@RequestMapping(value = "/swarm-initialized", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.TEXT_PLAIN_VALUE)
	@ResponseBody
	public ResponseEntity<String> swarmInit(HttpServletRequest request) {

		final SwarmNodeProvisionContext gen = createContext(request, "ready");
		String workerTokenOutput = request.getParameter("workerTokenOutput");
		if (Strings.isNullOrEmpty(workerTokenOutput)) {
			return ResponseEntity.badRequest().body("# workerTokenOutput missing");
		}
		Optional<SwarmToken> workerToken = extractJoinToken(workerTokenOutput);

		String managerTokenOutput = request.getParameter("managerTokenOutput");
		if (Strings.isNullOrEmpty(managerTokenOutput)) {
			return ResponseEntity.badRequest().body("# managerTokenOutput missing");
		}
		Optional<SwarmToken> managerToken = extractJoinToken(managerTokenOutput);

		if (workerToken.isPresent()) {
			provisioningManager.mergeTridentCluster(gen.getTridentClusterId().get(), "workerJoinToken",
					workerToken.get().getToken());
		} else {
			return ResponseEntity.badRequest().body("# could not extract worker token");
		}

		if (managerToken.isPresent()) {
			provisioningManager.mergeTridentCluster(gen.getTridentClusterId().get(), "managerJoinToken",
					managerToken.get().getToken(), "managerAddress", managerToken.get().getAddress());

		} else {
			return ResponseEntity.badRequest().body("# could not extract manager token");
		}

		registerInitialization(request, gen);

		return execute(gen);

	}

	@VisibleForTesting
	protected void registerInitialization(HttpServletRequest request, SwarmNodeProvisionContext gen) {

		String tridentClusterId = request.getParameter("id");
		String swarmClusterId = request.getParameter("swarmClusterId");
		provisioningManager.associateClusterId(tridentClusterId, swarmClusterId);

		// Now things get tricky. The node is fully in the swarm as far as
		// Docker is concerned, but this
		// does not guarantee that Trident knows about it, much less having a
		// record of the corresponding AWS
		// entity. What de do now is register the data we have and store it in
		// neo4j. Trident will then
		// work to associate things together.
		ObjectNode data = JsonUtil.createObjectNode();
		request.getParameterMap().keySet().forEach(it -> {

			if (it.toLowerCase().contains("token")) {
				// suppress
			} else {
				String val = request.getParameter(it);
				data.put(it, val);
			}
		});
		provisioningManager.registerSwarmNodeInitialization(gen.getTridentClusterId().get(), data);

	}

	@RequestMapping(value = "/ready", method = { RequestMethod.GET,
			RequestMethod.POST }, produces = MediaType.TEXT_PLAIN_VALUE)
	@ResponseBody
	public ResponseEntity<String> nodeReady(HttpServletRequest request) {
		final SwarmNodeProvisionContext gen = createContext(request, "ready");

		registerInitialization(request, gen);

		return execute(gen);
	}

	SwarmNodeProvisionContext decorate(SwarmNodeProvisionContext ctx) {
		return provisioningManager.decorate(ctx);
	}

	ResponseEntity<String> execute(SwarmNodeProvisionContext ctx) {
		String script = provisioningManager.generateScript(ctx);

		return ResponseEntity.ok(script);
	}
}
