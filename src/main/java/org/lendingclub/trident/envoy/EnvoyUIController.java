package org.lendingclub.trident.envoy;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.lendingclub.neorx.NeoRxClient;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Controller
@Component
@RequestMapping(value = "/envoy")
public class EnvoyUIController {

	
	@Autowired
	NeoRxClient neo4j;
	
	@RequestMapping(value = "/instances/{id}", method = { RequestMethod.GET, RequestMethod.POST },produces="text/html")
	public ModelAndView envoyInstance(@PathVariable String id) {
		
		String cypher = "match (a:EnvoyInstance {id:{id}})  return a";
		
		JsonNode instance = neo4j.execCypher(cypher, "id",id).blockingFirst(null);
		Map<String,Object> data = Maps.newHashMap();
		if (instance!=null) {
			ObjectNode dn = (ObjectNode) instance;
			data.put("instance", instance);
			String env = dn.path("environment").asText();
			String subenv = dn.path("subEnvironment").asText("default");
			String serviceGroup = dn.path("serviceGroup").asText();
			String node = dn.path("node").asText();
			String ldsUrl = String.format("/api/trident/envoy/v1/listeners/%s--%s--%s--%s/%s",instance.path("region").asText(),env,subenv,serviceGroup,node);
			String cdsUrl = String.format("/api/trident/envoy/v1/clusters/%s--%s--%s--%s/%s",instance.path("region").asText(),env,subenv,serviceGroup,node);
			String configUrl = String.format("/api/trident/envoy/config/%s--%s--%s--%s/%s",instance.path("region").asText(),env,subenv,serviceGroup,node);

			String configUnifiedUrl = String.format("/api/trident/envoy/config-unified/%s--%s--%s--%s/%s",instance.path("region").asText(),env,subenv,serviceGroup,node);

			String configBundleUrl = String.format("/api/trident/envoy/config-bundle/%s--%s--%s--%s/%s",instance.path("region").asText(),env,subenv,serviceGroup,node);

			dn.put("cdsUrl", cdsUrl);
			dn.put("ldsUrl", ldsUrl);
			dn.put("configUrl", configUrl);
			dn.put("configBundleUrl", configBundleUrl);
			dn.put("configUnifiedUrl", configUnifiedUrl);
			
		}
		return new ModelAndView("envoy-instance-details", data);
	
	}
	
	@RequestMapping(value = "/instances", method = { RequestMethod.GET, RequestMethod.POST },produces="text/html")
	public ModelAndView envoyInstances() {

		PrettyTime pt = new PrettyTime();
		String cypher = "match (a:EnvoyInstance) where abs(timestamp()-a.lastContactTs)< (60 *1000 * 60) return a  order by a.region, a.environment,a.subEnvironment, a.serviceGroup";

		List<JsonNode> list = neo4j.execCypher(cypher).filter(p->{
			
			ObjectNode n = ObjectNode.class.cast(p);
			
			n.put("lastContactPrettyTime",pt.format(new Date(n.path("lastContactTs").asLong(0))));
			
			return true;
		}).toList().blockingGet();
		
	

		return new ModelAndView("envoy-instances", U.map("instances", list));

	}
}
