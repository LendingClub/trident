package org.lendingclub.trident.swarm.platform;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.util.DockerDateFormatter;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.reactivex.Observable;

@Controller
public class AppClusterUIController {

	Logger logger = LoggerFactory.getLogger(AppClusterUIController.class);

	@Autowired
	NeoRxClient neo4j;

	@Autowired
	SwarmClusterManager swarmClusterManager;

	@Autowired
	AppClusterManager appClusterManager;

	@Autowired
	Catalog catalog;
	
	@RequestMapping(value = "/app-clusters", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView swarmClusters(RedirectAttributes redirect) {
		Map<String, Object> newReleasePage = new HashMap<>();
		String cypher = "match (a:AppCluster) where exists(a.appClusterId) return a order by a.region, a.environment, a.subEnvironment, a.appId";
		List<JsonNode> appClusters = neo4j.execCypher(cypher).toList().blockingGet();
		newReleasePage.put("clusters", appClusters);

		cypher = "match (a:DockerSwarm) return a order by a.name";
		List<JsonNode> swarmsList = neo4j.execCypher(cypher).toList().blockingGet();
		newReleasePage.put("swarms", swarmsList);
		
		newReleasePage.put("appIdList",catalog.getAppIds());
		newReleasePage.put("envList",catalog.getEnvironmentNames());
		newReleasePage.put("subEnvList",catalog.getSubEnvironmentNames());
		newReleasePage.put("regionList", catalog.getRegionNames());
		return new ModelAndView("app-clusters", newReleasePage);

	}

	@RequestMapping(value = "/app-cluster/create", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView createAppCluster(HttpServletRequest request, RedirectAttributes redirect) {
		List<String> emptyParams = RequestValidator.validateParameters(request);
		String message = "";
		if (emptyParams.size() == 0) {
			String appId = Strings.nullToEmpty(request.getParameter("appId")).trim();
			String env = Strings.nullToEmpty(request.getParameter("environment")).trim();
			String subEnv = Strings.nullToEmpty(request.getParameter("subEnvironment")).trim();
			String region = Strings.nullToEmpty(request.getParameter("region")).trim();
			String swarm = Strings.nullToEmpty(request.getParameter("swarm")).trim();
			String serviceGroup = catalog.getServiceCatalogEntry(appId).get().path("endpoint")
			        .asText();
			logger.info("Service Group:" + serviceGroup);

			String appClusterId = appClusterManager.createClusterCommand().withAppId(appId).withEnvironment(env)
			        .withSubEnvironment(subEnv)
			        .withRegion(region).withSwarm(swarm).withServiceGroup(serviceGroup).execute().getAppClusterId().get();
			message = " created application cluster for " + appId + " in " + env + "--" + subEnv + " with Id "
			        + appClusterId;
			redirect.addFlashAttribute("label", "success");
		} else {
			message = "Please enter values for fields " + Arrays.toString(emptyParams.toArray());
			redirect.addFlashAttribute("label", "warning");
		}
		redirect.addFlashAttribute("displayMessage", "none");
		if (!Strings.isNullOrEmpty(message)) {
			redirect.addFlashAttribute("message", message);
			redirect.addFlashAttribute("displayMessage", "block");
		}

		return new ModelAndView("redirect:/app-clusters");
	}

	@RequestMapping(value = "/app-clusters/{id}", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView appClusterDetails(@PathVariable String id, HttpServletRequest request,
	        RedirectAttributes redirect) {
		logger.info("getting details for app cluster {}", id);
		String cypher = "match (a:AppCluster {appClusterId: {id}}) return a;";
		JsonNode appCluster = neo4j.execCypher(cypher, "id", id).blockingFirst();
		if (appCluster == null || appCluster.size() == 0) {
			return new ModelAndView("redirect:/app-clusters");
		}
		cypher = "MATCH (y:DockerService { label_tsdAppClusterId:{id} }) return y ORDER BY y.label_tsdBlueGreenState DESC;";
		List<JsonNode> dockerServiceList = neo4j.execCypherAsList(cypher, "id", id);
		List<JsonNode> enhancedDockerServices = new ArrayList<JsonNode>();
		for (JsonNode n : dockerServiceList) {
			ObjectNode object = ObjectNode.class.cast(n);
			String serviceId = n.path("serviceId").asText();
			List<String> imageList = Splitter.on("@").splitToList(n.path("taskImage").asText());
			String imageDescription = imageList.get(0);
			object.put("imageDescription", imageDescription);
			String status = n.path("label_tsdBlueGreenState").asText();
			String port = n.path("label_tsdPort").asText();
			if (status.equals(BlueGreenState.LIVE.toString())) {
				object.put("flag", "success");
				object.put("liveActionDiv", "none");
			} else {
				if (status.equalsIgnoreCase(BlueGreenState.DRAIN.toString()))
					object.put("flag", "warning");
				else
					object.put("flag", "default");
				object.put("liveActionDiv", "block");
			}
			object.put("appClusterId", id);
			object.put("swarm", appCluster.path("swarm").asText(""));

			// Get all corresponding tasks
			cypher = "match (y:DockerTask { serviceId:{id} }) return y;";
			List<JsonNode> dockerServiceTasksList = neo4j.execCypherAsList(cypher, "id", serviceId);
			ArrayNode dockerServiceTasksArray = JsonUtil.createArrayNode();
			dockerServiceTasksList.forEach(t -> {
				ObjectNode tObject = (ObjectNode) t;
				String containerPort = t.path("hostTcpPortMap_" + port ).asText("");
				tObject.put("containerPort", containerPort);
				dockerServiceTasksArray.add(tObject);
			});
			object.set("serviceTasks", dockerServiceTasksArray);
			enhancedDockerServices.add(object);
		}
		Map<String, Object> appClusterDetails = new HashMap<>();
		appClusterDetails.put("cluster", appCluster);
		appClusterDetails.put("dockerServices", enhancedDockerServices);
		return new ModelAndView("app-cluster-details", appClusterDetails);
	}

	@RequestMapping(value = "/app-clusters/{appClusterId}/createnewrelease", method = { RequestMethod.GET,
	        RequestMethod.POST })
	public ModelAndView createNewRelease(@PathVariable String appClusterId, HttpServletRequest request) {

		Map<String, Object> newReleasePage = new HashMap<>();
		newReleasePage.put("appClusterId", appClusterId);

		// Get parameters from the live release if exists
		String liveServiceCypher = "MATCH ( x:DockerService { label_tsdAppClusterId:{clusterId},label_tsdBlueGreenState:'live'} ) return x;";
		List<JsonNode> dockerServiceList = neo4j.execCypherAsList(liveServiceCypher, "clusterId", appClusterId);

		if (dockerServiceList != null && dockerServiceList.size() > 0) {
			JsonNode n = dockerServiceList.get(0);
			newReleasePage.put("path", n.path("label_tsdPath").asText("/"));
			newReleasePage.put("port", n.path("label_tsdPort").asText());
			JsonNode args = n.path("taskArgs");
			TypeReference<List<String>> typeRef = new TypeReference<List<String>>() {
			};
			List<String> argsList = new ArrayList<String>();
			StringBuilder sb = new StringBuilder();
			try {
				argsList = JsonUtil.getObjectMapper().readValue(args.traverse(), typeRef);
				
				argsList.forEach(arg-> {
					sb.append(arg).append(" ");
				});
			} catch (IOException e) {
				logger.warn("Failed to fetch task args for live service of App cluster {}", appClusterId);
			}

			if (sb.length() > 0)
				newReleasePage.put("args", sb.toString());

			String image = Splitter.on("@").splitToList(n.path("taskImage").asText()).get(0);
			newReleasePage.put("image", image);
		}

		return new ModelAndView("deploy-release", newReleasePage);
	}
	
	@RequestMapping(value = "/app-clusters/{appClusterId}/delete", method = { RequestMethod.GET,
	        RequestMethod.POST })
	public ModelAndView deleteAppCluster(@PathVariable String appClusterId, RedirectAttributes redirect) {
		logger.info("Request for deleting cluster {}", appClusterId);
		String message = "";
		if (!Strings.isNullOrEmpty(appClusterId)) {
			appClusterManager.deleteClusterCommand().withAppClusterId(appClusterId).execute();
			redirect.addFlashAttribute("label", "success");
		} else {
			message = "App cluster is not specified to delete";
			redirect.addFlashAttribute("label", "warning");
		}
		redirect.addFlashAttribute("displayMessage", "none");
		if (!Strings.isNullOrEmpty(message)) {
			redirect.addFlashAttribute("message", message);
			redirect.addFlashAttribute("displayMessage", "block");
		}
		return new ModelAndView("redirect:/app-clusters");
	}

	@RequestMapping(value = "/app-clusters/{appClusterId}/createrelease", method = { RequestMethod.GET,
	        RequestMethod.POST })
	public ModelAndView createRelease(@PathVariable String appClusterId, HttpServletRequest request,
	        RedirectAttributes redirect) {
		boolean creationSuccessful = false;
		String message = "";
		String image = Strings.nullToEmpty(request.getParameter("image")).trim();
		String port = Strings.nullToEmpty(request.getParameter("port")).trim();
		String path = Strings.nullToEmpty(request.getParameter("path")).trim();
		String args = Strings.nullToEmpty(request.getParameter("args")).trim();
		String branch = Strings.nullToEmpty(request.getParameter("branch")).trim();
		String revision = Strings.nullToEmpty(request.getParameter("revision")).trim();
		try {
			String serviceId = appClusterManager.deployCommand().withImage(image)
			        .withBlueGreenState(BlueGreenState.DARK)
			        .withAppClusterId(appClusterId).withPort(port).withPath(path)
			        .withArgs(args).withSourceBranch(branch).withSourceRevision(revision)
			        .execute()
			        .getSwarmServiceId().get();
			message = " creating release with Id " + serviceId + " for " + appClusterId
			        + ". Please refresh page in few minutes to see details about it.";
			creationSuccessful = true;
		} catch (Exception e) {
			message = "Failed to create release for " + appClusterId + " with error " + e.getMessage();
			logger.error(message, e);
		}

		redirect.addFlashAttribute("displayMessage", "none");
		if (!Strings.isNullOrEmpty(message)) {
			redirect.addFlashAttribute("message", message);
			redirect.addFlashAttribute("displayMessage", "block");
			if (creationSuccessful)
				redirect.addFlashAttribute("label", "success");
			else {
				redirect.addFlashAttribute("label", "warning");
				return new ModelAndView("redirect:/app-clusters/" + appClusterId + "/createnewrelease");
			}
		}
		return new ModelAndView("redirect:/app-clusters/" + appClusterId);
	}

	List<String> getDistinctServiceAttribute(String key) {
		return neo4j.execCypher("match (a:DockerService) return distinct a." + key + " as val")
		        .filter(p -> p.isTextual()).map(it -> {
			        return it.asText();
		        }).flatMap(it -> {
			        return Observable.fromIterable(Splitter.on(",").trimResults().omitEmptyStrings().split(it));
		        }).filter(p -> (!Strings.isNullOrEmpty(p))).toList().blockingGet();
	}

	@RequestMapping(value = "/swarm-discovery-search", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView swarmDiscoverySearch(HttpServletRequest request) {

		String env = Strings.nullToEmpty(request.getParameter("env")).trim();
		String subEnv = Strings.nullToEmpty(request.getParameter("subEnv")).trim();
		String region = Strings.nullToEmpty(request.getParameter("region")).trim();
		String serviceGroup = Strings.nullToEmpty(request.getParameter("serviceGroup")).trim();

		List<String> envList = getDistinctServiceAttribute("label_tsdEnv");
		List<String> subEnvList = getDistinctServiceAttribute("label_tsdSubEnv");
		List<String> serviceGroupList = getDistinctServiceAttribute("label_tsdServiceGroup");
		List<String> regionList = getDistinctServiceAttribute("label_tsdRegion");

		regionList.addAll(catalog.getRegionNames());
		serviceGroupList.addAll(catalog.getServiceGroupNames());
		subEnvList.addAll(catalog.getSubEnvironmentNames());
		envList.addAll(catalog.getEnvironmentNames());
		
		regionList = regionList.stream().distinct().collect(Collectors.toList());
		envList = envList.stream().distinct().collect(Collectors.toList());
		subEnvList = subEnvList.stream().distinct().collect(Collectors.toList());
		serviceGroupList = serviceGroupList.stream().distinct().collect(Collectors.toList());
		if (regionList.isEmpty()) {
			regionList.add("local");
		}
		subEnvList.remove("default");
		subEnvList.add(0, "default");
		Map<String, Object> data = Maps.newHashMap();
		data.put("envList", envList);
		data.put("subEnvList", subEnvList);
		data.put("serviceGroupList", serviceGroupList);
		data.put("regionList", regionList);
		data.put("env", env);
		data.put("subEnv", subEnv);
		data.put("region", region);
		data.put("serviceGroup", serviceGroup);

		List<JsonNode> list = Lists.newArrayList();
		if (!(Strings.isNullOrEmpty(env) || Strings.isNullOrEmpty(subEnv) || Strings.isNullOrEmpty(region)
		        || Strings.isNullOrEmpty(serviceGroup))) {
			swarmClusterManager.newServiceDiscoverySearch().withEnvironment(env).withSubEnvironment(subEnv)
			        .withRegion(region).withServiceGroup(serviceGroup).search().forEach(it -> {
				        list.add(it.getData());
				        JsonUtil.logInfo("", it.getData());
				        ObjectNode n = (ObjectNode) it.getData().path("s");
				        n.put("taskImageName", Splitter.on("@").splitToList(n.path("taskImage").asText()).get(0));

				        n.put("createdAtPrettyTime", DockerDateFormatter.prettyFormat(n.path("createdAt").asText()));
				        StringBuffer taskArgs = new StringBuffer();
				        n.path("taskArgs").forEach(x -> {
				            taskArgs.append(x.asText());
				            taskArgs.append(" ");
			            });

				        n.put("args", taskArgs.toString());
			        });
			data.put("results", ImmutableMap.of("services", list));
		}

		return new ModelAndView("swarm-discovery-search", data);

	}

	

}
