package org.lendingclub.trident.swarm;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import org.lendingclub.mercator.core.SchemaManager;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.crypto.CertificateAuthority;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.platform.AppClusterManager;
import org.lendingclub.trident.swarm.platform.BlueGreenState;
import org.ocpsoft.prettytime.PrettyTime;
import org.rapidoid.u.U;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

	@Autowired
	AppClusterManager appClusterManager;
	
	@Autowired
	AWSClusterManager awsClusterManager;

	PrettyTime prettyTime = new PrettyTime();

	@RequestMapping(value = "/snoop", method = { RequestMethod.GET }, produces = { "text/plain" })
	public @ResponseBody String snoop(HttpServletRequest request) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);

		String remoteUser = request.getRemoteUser();
		pw.println("remoteUser: " + remoteUser);

		pw.println("authType: " + request.getAuthType());
		pw.println();
		Enumeration t = request.getAttributeNames();
		while (t.hasMoreElements()) {
			Object key = t.nextElement();
			pw.println(key + "=" + request.getAttribute(key.toString()));
		}
		pw.println();
		pw.println("headers");
		t = request.getHeaderNames();
		while (t.hasMoreElements()) {
			Object key = t.nextElement();
			pw.println(key + "=" + request.getHeader(key.toString()));
		}

		pw.println();
		pw.println("auth");

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		pw.println("principal: " + auth.getPrincipal());
		pw.println("name: " + auth.getName());

		auth.getAuthorities().forEach(it -> {
			pw.println("authority: " + it);
		});
		pw.close();
		return sw.toString();
	}

	@RequestMapping(value = "/swarm-services", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView swarmServices() {

		String cypher = "match (a:DockerSwarm)--(s:DockerService) return "
		        + "a.name as swarmName,"
		        + "a.swarmClusterId as swarmClusterId,"
		        + "s.name as serviceName,"
		        + "s.replicas as replicas,"
		        + "s.serviceId as serviceId,"
		        + "s.taskImage as taskImage "
		        + " order by s.name";
		List<JsonNode> list = neo4j.execCypher(cypher).filter(it -> {
			ObjectNode n = ObjectNode.class.cast(it);
			List<String> imageList = Splitter.on("@").splitToList(n.path("taskImage").asText());
			String imageDescription = imageList.get(0);
			String hash = imageList.size() > 1 ? imageList.get(1).replace("sha256:", "") : "";

			n.put("imageDescription", imageDescription);
			return true;
		}).toList().blockingGet();
		return new ModelAndView("swarm-services", U.map("services", list));

	}

	@RequestMapping(value = "/swarm-services/{serviceId}", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView serviceDetail(@PathVariable("serviceId") String serviceId) {

		String serviceDetailsQuery = "match(x:DockerService {serviceId: {serviceId}}) "
		        + "return x";

		List<JsonNode> serviceDetailsQueryList = neo4j.execCypherAsList(serviceDetailsQuery, "serviceId", serviceId);

		Map<String, List<JsonNode>> data = new HashMap<>();
		data.put("services", serviceDetailsQueryList);
		return new ModelAndView("swarm-services-details", data);
	}
	
	@RequestMapping(value="/swarm-services/{serviceId}/deleterelease", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView deleteRelease(@PathVariable String serviceId, HttpServletRequest request, RedirectAttributes redirect ) {
		String appClusterId = Strings.nullToEmpty(request.getParameter("appClusterId")).trim();
		JsonNode dockerService = neo4j
		        .execCypher("MATCH (y:DockerService { serviceId:{id} }) return y", "id", serviceId).blockingFirst(NullNode.instance);
		String message = "";
		if(dockerService != null) {
			String swarmId = dockerService.path("swarmClusterId").asText();
			logger.info("Deleting {} service for the {} App cluster in {} Swarm", serviceId, appClusterId, swarmId);
			appClusterManager.deleteServiceCommand().withSwarmServiceId(serviceId).withSwarm(swarmId).execute();
			redirect.addFlashAttribute("label", "success");

		} else {
			message = "No docker service found with Id " + serviceId;
			logger.error(message);
			redirect.addFlashAttribute("label", "warning");
		}
		
		redirect.addFlashAttribute("displayMessage", "none");
		if (!Strings.isNullOrEmpty(message)) {
			redirect.addFlashAttribute("message", message);
			redirect.addFlashAttribute("displayMessage", "block");
		}
		
		return new ModelAndView("redirect:/app-clusters/" + appClusterId);

	}

	@RequestMapping(value = "/swarm-services/{serviceId}/golive", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView serviceGoLive(@PathVariable("serviceId") String serviceId, HttpServletRequest request,
	        RedirectAttributes redirect) {
		String appClusterId = Strings.nullToEmpty(request.getParameter("appClusterId")).trim();
		String serviceName = Strings.nullToEmpty(request.getParameter("serviceName")).trim();
		// Need logic to see if any other service is live for given cluster
		// before making it live
		String cypher = "match (y:DockerService { label_tsdAppClusterId:{id}, label_tsdBlueGreenState:'live' }) return y;";
		List<JsonNode> dockerServices = neo4j.execCypherAsList(cypher, "id", appClusterId);
		for (JsonNode service : dockerServices) {
			String id = service.path("serviceId").asText();
			String status = service.path("label_tsdBlueGreenState").asText();
			if (!Strings.isNullOrEmpty(id) && status.equalsIgnoreCase(BlueGreenState.LIVE.toString())) {
				try {
					logger.info("Trying to drain service {} for App cluster {}", serviceId, appClusterId);
					appClusterManager.blueGreenCommand().withBlueGreenState(BlueGreenState.DRAIN).withSwarmServiceId(id)
					        .execute();
				} catch (Exception e) {
					String message = "Failed to drain service " + id + " with error " + e.getLocalizedMessage();
					logger.error(message);
					redirect.addFlashAttribute("message", message);
					redirect.addFlashAttribute("displayMessage", "block");
					redirect.addFlashAttribute("label", "warning");
					return new ModelAndView("redirect:/app-clusters/" + appClusterId);
				}
			}
		}
		appClusterManager.blueGreenCommand().withBlueGreenState(BlueGreenState.LIVE).withSwarmServiceId(serviceId)
		        .execute();
		String message = "Making " + serviceName + " live ";
		logger.info(message);
		redirect.addFlashAttribute("message", message);
		redirect.addFlashAttribute("displayMessage", "block");
		redirect.addFlashAttribute("label", "success");
		return new ModelAndView("redirect:/app-clusters/" + appClusterId);
	}

	@RequestMapping(value = "/swarm-services/{serviceId}/scale", method = { RequestMethod.GET,
	        RequestMethod.POST })
	public ResponseEntity scaleService(@RequestParam(value = "value") String replicas,
	        @PathVariable("serviceId") String serviceId,
	        HttpServletRequest request,
	        RedirectAttributes redirect) {
		logger.info("Requested for scaling {} to {} ", serviceId, replicas);
		int statusCode = 200;
		boolean valueChanged = false;
		String message = "";
		Map<String, Object> map = new HashMap<>();
		try {
		    appClusterManager.scaleServiceCommand().withSwarmServiceId(serviceId).withReplicas(replicas.trim()).execute();
		    message = "Successfully scaled to " + replicas + " for service Id: " + serviceId;
		    valueChanged = true;
		}catch(Exception e) {
			message = "Error while trying to scale the instances of service " + serviceId + ". Failed with error " + e.getMessage();
			logger.error(message);
			statusCode = HttpStatus.BAD_REQUEST.value();
		}
        map.put("statusCode", statusCode);
        map.put("statusMessage", message);
        
        if(!valueChanged)
            return ResponseEntity.badRequest().body(map);
        else
        	return ResponseEntity.ok(map);
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
			} else {
				n.put("tridentClusterId", tridentClusterId);
				n.put("name", name);
				n.put("description", it.path("description").asText());
				list.add(it);
			}

		});

		return new ModelAndView("swarm-clusters", U.map("clusters", list));

	}

	/**
	 * Temporary static method to share. We will work this into a java API
	 * enventually.
	 *
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
			return "tcp://" + Splitter.on(":").trimResults().splitToList(managerAddress).get(0) + ":2376";
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
			return Splitter.on(":").trimResults().splitToList(managerAddress).get(0) + ":2377";
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
			cert.writeCertBundle(p.toFile(), dockerHost);
			byte[] data = Files.readAllBytes(p);

			header.setContentLength(data.length);

			return new HttpEntity<byte[]>(data, header);
		} finally {
			if (p != null) {
				p.toFile().delete();
			}
		}
	}
	
	@RequestMapping(value = "/swarm-clusters/{id}/delete", method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE })
	public ModelAndView deleteSwarm(@PathVariable("id") String tridentClusterId, RedirectAttributes redirect) { 
		Preconditions.checkArgument(!Strings.isNullOrEmpty(tridentClusterId), "trident swarm cluster id cannot be null or empty");
		logger.info("User {} attempting deletion of trident swarm id={}", 		
				SecurityContextHolder.getContext().getAuthentication().getName(), tridentClusterId);
		try { 
			awsClusterManager.destroyCluster(tridentClusterId);
			redirect.addFlashAttribute("label","success");
			redirect.addFlashAttribute("displayType","block");
			redirect.addFlashAttribute("message","Deleted Trident swarm cluster " + tridentClusterId +  " and all associated components.");
			return new ModelAndView("redirect:/swarm-clusters");
		} catch (RuntimeException e) { 
			logger.warn("problem destroying trident swarm cluster id={}", tridentClusterId, e);
			redirect.addFlashAttribute("label","warning");
			redirect.addFlashAttribute("displayType","block");
			redirect.addFlashAttribute("message","Failed to delete Trident swarm cluster " + tridentClusterId + ": " + e.getMessage());
			return new ModelAndView("redirect:/swarm-clusters/" + tridentClusterId);
		}
	}

	@RequestMapping(value = "/swarm-clusters/{id}", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView swarmClusterDetail(@PathVariable("id") String id) {
		logger.info("getting details for {}", id);

		String detailsQuery = "match(d: DockerSwarm {tridentClusterId: {id}}) "
		        + "return d.swarmClusterId as swarmId, d.name as name, "
		        + "d.description as description, d.managerAddress as managerAddress,d.managerApiUrl as managerApiUrl, d.tridentClusterId as id";
		JsonNode details = neo4j.execCypher(detailsQuery, "id", id).blockingFirst(null);
		if (details == null) {
			return new ModelAndView("redirect:/swarm-clusters/");
		}
		String managerApiUrl = getManagerApi(details);
		String managerEndpoint = getManagerEndpoint(details);

		if (details.isObject()) {
			ObjectNode swarmNode = (ObjectNode) details;
			swarmNode.put("managerApiUrl", managerApiUrl);
			swarmNode.put("managerEndpoint", managerEndpoint);
		}

		String getManagerASGQuery = "match(d: DockerSwarm {tridentClusterId: {id}})--(a:AwsAsg)--(y:AwsLaunchConfig) "
		        + "where a.aws_tag_swarmNodeType='MANAGER' "
		        + "return a.aws_autoScalingGroupName as asgName, a.aws_launchConfigurationName as launchConfigName, "
		        + "a.aws_minSize as minSize, a.aws_maxSize as maxSize, a.aws_desiredCapacity as desiredSize, "
		        + "y.aws_imageId as imageId, y.aws_instanceType as instanceType";

		List<JsonNode> managerASG = neo4j.execCypherAsList(getManagerASGQuery, "id", id);
		managerASG = managerASG.stream().map(asg -> {
			return asg;
		}).collect(Collectors.toList());

		String getManagersQuery = "match(d: DockerSwarm {tridentClusterId: {id}})--(e: DockerHost) "
		        + "where e.role='manager' "
		        + "return d.tridentClusterId as tridentClusterId, e.swarmNodeId as swarmNodeId, e.hostname as host, e.addr as addr, e.engineVersion as engineVersion, "
		        + "e.leader as leader, e.availability as availability, e.state as state, e.updateTs as updateTs";

		Date date = new Date();
		List<JsonNode> managers = neo4j.execCypherAsList(getManagersQuery, "id", id);
		managers = managers.stream().map(manager -> {
			date.setTime(Long.parseLong(manager.path("updateTs").asText("")));
			manager = ((ObjectNode) manager).put("updateTs", prettyTime.format(date));

			if (manager.path("leader").asBoolean()) {
				manager = ((ObjectNode) manager).put("leader", "leader");
			} else {
				manager = ((ObjectNode) manager).put("leader", "");
			}

			return manager;
		}).collect(Collectors.toList());

		String getWorkerASGQuery = "match(d: DockerSwarm {tridentClusterId: {id}})--(a:AwsAsg)--(y:AwsLaunchConfig) "
		        + "where a.aws_tag_swarmNodeType='WORKER' "
		        + "return a.aws_autoScalingGroupName as asgName, a.aws_launchConfigurationName as launchConfigName, "
		        + "a.aws_minSize as minSize, a.aws_maxSize as maxSize, a.aws_desiredCapacity as desiredSize, "
		        + "y.aws_imageId as imageId, y.aws_instanceType as instanceType";

		List<JsonNode> workerASG = neo4j.execCypherAsList(getWorkerASGQuery, "id", id);
		workerASG = workerASG.stream().map(asg -> {
			return asg;
		}).collect(Collectors.toList());

		String getWorkersQuery = "match(d: DockerSwarm {tridentClusterId: {id}})--(e: DockerHost) "
		        + "where e.role='worker' "
		        + "return d.tridentClusterId as tridentClusterId, e.swarmNodeId as swarmNodeId, e.hostname as host, e.addr as addr, e.engineVersion as engineVersion, "
		        + "e.leader as leader, e.availability as availability, e.state as state, e.updateTs as updateTs";

		List<JsonNode> workers = neo4j.execCypherAsList(getWorkersQuery, "id", id);

		workers = workers.stream().map(worker -> {
			date.setTime(Long.parseLong(worker.path("updateTs").asText("")));
			worker = ((ObjectNode) worker).put("updateTs", prettyTime.format(date));
			return worker;
		}).collect(Collectors.toList());

		Map<String, Object> swarmClusterDetails = new HashMap<>();
		swarmClusterDetails.put("masterDetails", details);
		swarmClusterDetails.put("managerASG", managerASG);
		swarmClusterDetails.put("managers", managers);
		swarmClusterDetails.put("workerASG", workerASG);
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
