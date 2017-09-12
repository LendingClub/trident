package org.lendingclub.trident.swarm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.lendingclub.mercator.core.SchemaManager;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.crypto.CertificateAuthority;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.ocpsoft.prettytime.PrettyTime;
import org.rapidoid.u.U;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;


@Controller
public class SwarmClusterController {

	Logger logger = LoggerFactory.getLogger(SwarmClusterManager.class);
	@Autowired
	NeoRxClient neo4j;

	@Autowired
	CertificateAuthorityManager certificateAuthorityManager;

	PrettyTime prettyTime = new PrettyTime();

	@RequestMapping(value = "/snoop" ,method = { RequestMethod.GET},produces={"text/plain"})
	public @ResponseBody String snoop(HttpServletRequest request) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		
		String remoteUser = request.getRemoteUser();
		pw.println("remoteUser: "+remoteUser);
		
		
		pw.println("authType: "+request.getAuthType());
		pw.println();
		Enumeration t = request.getAttributeNames();
		while (t.hasMoreElements()) {
			Object key = t.nextElement();
			pw.println(key+"="+request.getAttribute(key.toString()));
		}
		pw.println();
		pw.println("headers");
		t = request.getHeaderNames();
		while (t.hasMoreElements()) {
			Object key = t.nextElement();
			pw.println(key+"="+request.getHeader(key.toString()));
		}
		
		pw.println();
		pw.println("auth");
		
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		pw.println("principal: "+auth.getPrincipal());
		pw.println("name: "+auth.getName());
		
		auth.getAuthorities().forEach(it-> {
			pw.println("authority: "+it);
		});
		pw.close();
		return sw.toString();
	}
	
	@RequestMapping(value = "/swarm-clusters", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView swarmClusters() {

		String cypher = "match (a:DockerSwarm) return a order by a.name";

		List<JsonNode> list = Lists.newArrayList();
		neo4j.execCypher(cypher).forEach(it -> {
			ObjectNode n = (ObjectNode) it;

			String tridentClusterId = it.path("tridentClusterId").asText();
			String name = it.path("name").asText();
			
			if (Strings.isNullOrEmpty(tridentClusterId) || Strings.isNullOrEmpty(name)) {
				// skip orphanned entries
			}
			else {
				n.put("tridentClusterId", tridentClusterId);
				n.put("name", name);
				n.put("description", it.path("description").asText());
				list.add(it);
			}
		
		});

		return new ModelAndView("swarm-clusters", U.map("clusters", list));

	}

	/**
	 * Temporary static method to share.  We will work this into a java API enventually.
	 * @param n
	 * @return
	 */
	public static String getManagerApi(JsonNode n) {
		String managerApiUrl = n.path("managerApiUrl").asText(null);
		if (Strings.isNullOrEmpty(managerApiUrl)) {
			String managerAddress = n.path("managerAddress").asText(null);
			if (Strings.isNullOrEmpty(managerAddress)) {
				return null;
			}
			return "tcp://"+Splitter.on(":").trimResults().splitToList(managerAddress).get(0)+":2376";
		}
		return managerApiUrl;
	}

	public static String getManagerEndpoint(JsonNode n) {
		String managerApiEndpoint = n.path("managerApiUrl").asText(null);
		if (Strings.isNullOrEmpty(managerApiEndpoint)) {
			String managerAddress = n.path("managerAddress").asText(null);
			if (Strings.isNullOrEmpty(managerAddress)) {
				return null;
			}
			return Splitter.on(":").trimResults().splitToList(managerAddress).get(0)+":2377";
		}
		return managerApiEndpoint;
	}

	@RequestMapping(value = "/swarm-clusters/{id}/download-client-certs", method = { RequestMethod.POST,
			RequestMethod.GET })
	public HttpEntity<byte[]> downloadCerts(@PathVariable("id") String tridentClusterId)
			throws IOException, GeneralSecurityException {

		Preconditions.checkArgument(!Strings.isNullOrEmpty(tridentClusterId), "swarm cluster id not found in request");

		CertificateAuthority.CertDetail cert = certificateAuthorityManager.getCertificateAuthority(tridentClusterId)
				.createClientCert().withValidityDays(1).withCN("someuser-" + UUID.randomUUID().toString()).build();
		HttpHeaders header = new HttpHeaders();
		header.setContentType(MediaType.parseMediaTypes("application/zip").get(0));
		String filename = "certs.zip";
		header.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);

		Path p = Files.createTempFile(UUID.randomUUID().toString(), "tmp");

		try {
	
			JsonNode n = neo4j
					.execCypher("match (s:DockerSwarm {tridentClusterId:{id}}) return s", "id", tridentClusterId)
					.blockingFirst(NullNode.instance);
			String dockerHost = getManagerApi(n);
			cert.writeCertBundle(p.toFile(),dockerHost);
			byte[] data = Files.readAllBytes(p);

			header.setContentLength(data.length);

			return new HttpEntity<byte[]>(data, header);
		} finally {
			if (p != null) {
				p.toFile().delete();
			}
		}
	}

	@RequestMapping(value = "/swarm-clusters/{id}", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView swarmClusterDetail(@PathVariable("id") String id) {
		logger.info("getting details for {}", id);

		String detailsQuery = "match(d: DockerSwarm {tridentClusterId: {id}}) "
				+ "return d.swarmClusterId as swarmId, d.name as name, "
				+ "d.description as description, d.managerAddress as managerAddress,d.managerApiUrl as managerApiUrl, d.tridentClusterId as id";
		JsonNode details = neo4j.execCypher(detailsQuery, "id", id).blockingFirst(null);
		if (details==null) {
			return new ModelAndView("redirect:/swarm-clusters/");
		}
		String managerApiUrl = getManagerApi(details);
		String managerEndpoint = getManagerEndpoint(details);
		
		if (details.isObject()) {
			ObjectNode swarmNode = (ObjectNode) details;
			swarmNode.put("managerApiUrl",managerApiUrl);
			swarmNode.put("managerEndpoint", managerEndpoint);
		}

		String getManagersQuery = "match(d: DockerSwarm {tridentClusterId: {id}})--(e: DockerHost) "
				+ "where e.role='manager' "
				+ "return e.swarmNodeId as id, e.hostname as host, e.addr as addr, e.engineVersion as engineVersion, "
				+ "e.leader as leader, e.availability as availability, e.state as state, e.updateTs as updateTs";

		Date date = new Date();
		List<JsonNode> managers = neo4j.execCypherAsList(getManagersQuery, "id", id);
		managers = managers.stream().map(manager -> {
			date.setTime( Long.parseLong(manager.path("updateTs").asText("")) );
			manager = ((ObjectNode) manager).put("updateTs",
					prettyTime.format(date));

			if(manager.path("leader").asBoolean()) {
				manager = ((ObjectNode) manager).put("leader", "leader");
			}
			else {
				manager = ((ObjectNode) manager).put("leader","");
			}

			return manager;
		}).collect(Collectors.toList());


		String getWorkersQuery = "match(d: DockerSwarm {tridentClusterId: {id}})--(e: DockerHost) "
				+ "where e.role='worker' "
				+ "return e.swarmNodeId as id, e.hostname as host, e.addr as addr, e.engineVersion as engineVersion, "
				+ "e.leader as leader, e.availability as availability, e.state as state, e.updateTs as updateTs";

		List<JsonNode> workers = neo4j.execCypherAsList(getWorkersQuery, "id", id);

		workers = workers.stream().map(worker -> {
			date.setTime( Long.parseLong( worker.path("updateTs").asText("") ) );
			worker = ((ObjectNode) worker).put("updateTs",
					prettyTime.format(date));
			return worker;
		}).collect(Collectors.toList());

		Map<String, Object> swarmClusterDetails = new HashMap<>();
		swarmClusterDetails.put("masterDetails", details);
		swarmClusterDetails.put("managers", managers);
		swarmClusterDetails.put("workers", workers);
		return new ModelAndView("swarm-cluster-details", swarmClusterDetails);
	}

	@RequestMapping(value = "/create-swarm-cluster", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView createCluster() {

		return new ModelAndView("create-swarm-cluster");
	}

	@PostConstruct
	public void applyConstraints() {
		if (neo4j.checkConnection()) {
			SchemaManager sm = new SchemaManager(neo4j);
			sm.applyUniqueConstraint("DockerSwarm", "tridentClusterId");
			sm.applyUniqueConstraint("DockerSwarm", "swarmId");
			sm.applyUniqueConstraint("DockerSwarm", "name");

		}
	}
}
