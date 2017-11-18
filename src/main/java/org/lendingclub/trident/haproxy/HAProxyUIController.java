package org.lendingclub.trident.haproxy;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.util.JsonUtil;
import org.ocpsoft.prettytime.PrettyTime;
import org.rapidoid.u.U;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import io.reactivex.Observable;

@Controller
@Component
@RequestMapping(value = "/haproxy")
public class HAProxyUIController {

	@Autowired
	NeoRxClient neo4j;
	
	
	@RequestMapping(value = "/instances", method = { RequestMethod.GET, RequestMethod.POST },produces="text/html")
	public ModelAndView envoyInstances() {

		PrettyTime pt = new PrettyTime();
		String cypher = "match (a:HAProxyInstance) where abs(timestamp()-a.lastContactTs)< (60 *1000 * 60) return a  order by a.region, a.environment,a.subEnvironment, a.serviceGroup";

		List<JsonNode> list = neo4j.execCypher(cypher).filter(p->{
			
			ObjectNode n = ObjectNode.class.cast(p);
			
			n.put("lastContactPrettyTime",pt.format(new Date(n.path("lastContactTs").asLong(0))));
			
			return true;
		}).toList().blockingGet();
		
	

		return new ModelAndView("haproxy-instances", U.map("instances", list));

	}
	
	@RequestMapping(value = "/instances/{id}", method = { RequestMethod.GET, RequestMethod.POST },produces="text/html")
	public ModelAndView envoyInstance(@PathVariable String id) {
		
		String cypher = "match (a:HAProxyInstance {id:{id}})  return a";
		
		JsonNode instance = neo4j.execCypher(cypher, "id",id).blockingFirst(null);
		Map<String,Object> data = Maps.newHashMap();
		if (instance!=null) {
			ObjectNode dn = (ObjectNode) instance;
			data.put("instance", instance);
			String env = dn.path("environment").asText();
			String subenv = dn.path("subEnvironment").asText("default");
			String serviceGroup = dn.path("serviceGroup").asText();
			String node = dn.path("node").asText();
			String region = dn.path("region").asText();

			if(Strings.isNullOrEmpty(region)) {
				region="local";
			}

			List<String> appIdList = getDistinctServiceAttribute("label_tsdAppId");
			
			String configBundleUrl = String.format("/api/trident/haproxy/v1/config?"
					+ "serviceCluster=%s"
					+ "&serviceNode=%s"
					+ "&environment=%s"
					+ "&subEnvironment=%s"
					+ "&region=%s",serviceGroup,node,env,subenv, region);
					
			
			
			ArrayNode appIdArrayList = JsonUtil.createArrayNode();
			appIdList.forEach(it->{
				appIdArrayList.add(it);
			});
			ObjectNode instanceData = ObjectNode.class.cast(instance);
			instanceData.set("appIdList", appIdArrayList);
			instanceData.put("instanceId", id);
			instanceData.put("configBundleUrl", configBundleUrl);
			data.put("serviceGroup", serviceGroup);
			data.put("serviceNode", node);
			data.put("env", env);
			data.put("subEnv", subenv);
			data.put("region", region);
			
		}
		return new ModelAndView("haproxy-instance-details", data);
	
	}
	@RequestMapping(value = "/instances/{id}/hosts", method = { RequestMethod.GET, RequestMethod.POST },produces="text/html")
	public ModelAndView haproxyHosts(@PathVariable String id, HttpServletRequest servletRequest) {
		
		String cypher = "match (a:HAProxyInstance {id:{id}})  return a";
		
		JsonNode instance = neo4j.execCypher(cypher, "id",id).blockingFirst(null);
		Map<String,Object> data = Maps.newHashMap();
		String appId = servletRequest.getParameter("appId");
		if (instance!=null && !Strings.isNullOrEmpty(appId) && !appId.startsWith("Select")) {
			ObjectNode dn = (ObjectNode) instance;
			data.put("instance", instance);
			String env = dn.path("environment").asText();
			String subenv = dn.path("subEnvironment").asText("default");
			String serviceGroup = dn.path("serviceGroup").asText();
			String node = dn.path("node").asText();
			String region = dn.path("region").asText();

			if(Strings.isNullOrEmpty(region)) {
				region = "local";
			}

			// For now, just redirect to the discovery URL....we can change this to a rendered page
			String hostUrl = String.format("/api/trident/haproxy/v1/hosts?"
					+ "appId=%s"
					+ "&serviceCluster=%s"
					+ "&serviceNode=%s"
					+ "&environment=%s"
					+ "&subEnvironment=%s"
					+ "&region=%s",appId,serviceGroup,node,env,subenv, region);
					
			return new ModelAndView("redirect:"+hostUrl);
		
			
		}
		return new ModelAndView("haproxy-instance-details", data);
	
	}
	List<String> getDistinctServiceAttribute(String key) {
		return neo4j.execCypher("match (a:DockerService) return distinct a." + key + " as val")
				.filter(p -> p.isTextual()).map(it -> {
					return it.asText();
				}).flatMap(it -> {
					return Observable.fromIterable(Splitter.on(",").trimResults().omitEmptyStrings().split(it));
				}).filter(p -> (!Strings.isNullOrEmpty(p))).toList().blockingGet();
	}
}
