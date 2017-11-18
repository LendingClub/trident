package org.lendingclub.trident.settings;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.chatops.HipChatProvider;
import org.lendingclub.trident.config.ConfigManager;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

@Controller
public class SettingsController {
	
	Logger logger = LoggerFactory.getLogger(SettingsController.class);

	@Autowired
	ConfigManager configManager;
	
	@Autowired
	NeoRxClient neo4j;
	
	@RequestMapping(value = "/settings", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView settings(RedirectAttributes redirect) {
		Map<String,Object> data = initializeSettingsPage();
		data.put("tab_config", "active in");
		return new ModelAndView("settings", data);
	}
	
	@RequestMapping(value = "/settings/config/create", method= { RequestMethod.GET, RequestMethod.POST}) 
	public ModelAndView addConfig(HttpServletRequest request, RedirectAttributes direct) {
		Map<String, String> map = RequestValidator.getParametersMap(request);
		String name = map.get("name");
		String type = map.get("type");
		map.remove("name");
		map.remove("type");
		
		// remove empty key, value coming from the template. Need to fix this.
		map.remove("key");
		map.remove("value");
		map.remove("encrypt");
		
		// remove encrypt array from map as well
		Map<String, Boolean> encryptionMap = new HashMap<String, Boolean>();
		for(int i =0; i< map.size(); i++) {
			String fieldName = "encrypt[" + i + "]";
			
			if ( map.containsKey(fieldName)) {
				encryptionMap.put(fieldName, true);
				
				// remove from regular map
				map.remove(fieldName);
			}
		}
		
		for(int i =0; i< map.size() / 2; i++) {
			
			String encryptFieldName = "encrypt[" + i + "]";
			boolean encryptValue = false;
			if (encryptionMap.containsKey(encryptFieldName))
				encryptValue = true;
			configManager.setValue(type, name, map.get("key[" + i + "]"), map.get("value[" + i + "]"), encryptValue);
		}
		Map<String,Object> data = initializeSettingsPage();
        data.put("tab_config", "active in");

		return new ModelAndView("settings", data);
	}
	
	@RequestMapping(value = "/settings/channels/{channelType}", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView channelSettings(@PathVariable String channelType, HttpServletRequest request,
	        RedirectAttributes redirect) {
		
		Map<String,Object> data = initializeSettingsPage();
        data.put(channelType.toLowerCase() + "-div-block-dis", "block");
        data.put("tab_notifications", "active in");
		return new ModelAndView("settings", data);
	}
	
	@RequestMapping(value = "/settings/channels/{channelType}/create", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView createChannel(@PathVariable String channelType, HttpServletRequest request,
	        RedirectAttributes redirect) {
		List<String> emptyParams = RequestValidator.validateParameters(request);
		String message = "";
		if (emptyParams.size() == 0) {
			// Save those configurations in neo4j based on channel
			if ( NotificationChannels.isValidChannel(channelType)) {
				message = configureChannelSettings(channelType, RequestValidator.getParametersMap(request));				
				redirect.addFlashAttribute("label", "success");
			} else {
				message =  channelType + " is invalid. Please provide a proper channel";
				redirect.addFlashAttribute("label", "warning");
			}
			
		}else {
			message = "Please enter values for fields " + Arrays.toString(emptyParams.toArray());
			redirect.addFlashAttribute("label", "warning");
		}
		if (!Strings.isNullOrEmpty(message)) {
			redirect.addFlashAttribute("message", message);
			redirect.addFlashAttribute("displayMessage", "block");
		}
		Map<String,Object> data = initializeSettingsPage();
        data.put("tab_notifications", "active in");

		return new ModelAndView("settings", data);
	}
	
	@RequestMapping(value = "/settings/channel/delete", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView deleteChannel(HttpServletRequest request,
	        RedirectAttributes redirect) {
		String message = "";
		String type = request.getParameter("_type");
		String name = request.getParameter("_name");

		// check if any policies attached to this channel in future before deleting
        String cypher = "match ( x:Config { name:{nm}, type:{t}}) detach delete x;";
        neo4j.execCypher(cypher, "nm", name, "t", type);
		message = "Deleted channel of type  " + type + " with name " + name;
		redirect.addFlashAttribute("label", "success");
  
		if (!Strings.isNullOrEmpty(message)) {
			redirect.addFlashAttribute("message", message);
			redirect.addFlashAttribute("displayMessage", "block");
		}
		Map<String,Object> data = initializeSettingsPage();
        data.put("tab_notifications", "active in");

		return new ModelAndView("settings", data);
	}
	
	private String configureChannelSettings(String channelType, Map<String, String> channelData) {
		String message = "";
		if (channelType.equalsIgnoreCase(NotificationChannels.HIPCHAT.getChannelName())) {
			// hipchat channel
			String roomName = channelData.get("roomName");
			configManager.setValue("chatops", "default", "defaultRoom", roomName, false);
			configManager.setValue("chatops", "default", "provider", HipChatProvider.class.getName(), false);
			configManager.setValue("chatops", "default", "token", channelData.get("adminToken"), true);
			configManager.setValue("chatops", "default", "category", "notificationChannel", false);
			configManager.setValue("chatops", "default", "proxy", "default", false);
			message = channelType.toUpperCase() + " is added with room name " + roomName;
		} else if (channelType.equalsIgnoreCase(NotificationChannels.EMAIL.getChannelName())) {
			// email channel with category type notificationChannel			
			configManager.setValue("email", "default", "category", "notificationChannel", false);
			//configManager.setValue("email", "default", "defaultRoom", channelData.get("email"), false);
			//configManager.setValue("email", "default", "provider", EmailProvider.class.getName(), false);
			//configManager.setValue("email", "default", "token", channelData.get("token"), true);
		} else if (channelType.equalsIgnoreCase(NotificationChannels.PAGERDUTY.getChannelName())) {
			// pagerduty channel with category type notificationChannel
			configManager.setValue("pagerduty", "default", "category", "notificationChannel", false);
			//configManager.setValue("pagerduty", "default", "defaultRoom", channelData.get("serviceName"), false);
			//configManager.setValue("pagerduty", "default", "provider", PagerDuty.class.getName(), false);
			//configManager.setValue("pagerduty", "default", "token", channelData.get("token"), true);
		}
		return message;
	}
	
	private Map<String,Object> initializeSettingsPage() {
		Map<String,Object> data = Maps.newHashMap();
		
		List<String> notificationChannels = NotificationChannels.getChannelValues();
		data.put("notificationChannels", notificationChannels);
		
		// get config types
		List<String> configTypes = ConfigTypes.getConfigTypeValues();
		data.put("configTypes", configTypes);
		
		// get channels data
		for(int i=0; i < notificationChannels.size(); i++) {
			String nc = notificationChannels.get(i).toLowerCase();
			data.put( nc+ "-div-block-dis", "none");
			data.put( nc + "-action", "/settings/channels/"+ nc + "/create");
		}
		String cypher = "MATCH (x:Config { category:'notificationChannel' } ) return x.name as name, x.type as type, x.updateTs as updateTs;";
		List<JsonNode> nc = neo4j.execCypher(cypher).toList().blockingGet().stream().map(n -> { 
			long timestamp = n.path("updateTs").asLong(0);
			ObjectNode node = ((ObjectNode) n).put("date", DockerDateFormatter.prettyFormat(timestamp));
			return node;
		}).collect(Collectors.toList());
		
		data.put("notificationChannelsData", nc);
		
		// Get config data
		cypher = "MATCH (x:Config) return x ORDER BY x.type;";
		List<JsonNode> configs = neo4j.execCypher(cypher).toList().blockingGet().stream().map( c-> {
			ObjectNode configsNode = JsonUtil.createObjectNode();
			ArrayNode array = JsonUtil.createArrayNode();
			Iterator<Map.Entry<String, JsonNode>> it = c.fields();
			while(it.hasNext()) {
				ObjectNode node = JsonUtil.createObjectNode();
				Map.Entry<String, JsonNode> entry = it.next();
				String key = entry.getKey();
				String value = entry.getValue().asText();
				
				if (key.equals("updateTs")) {
					node.put("key", "Last Updated");
					node.put("value", DockerDateFormatter.prettyFormat(Long.parseLong(value)));
				} else {
					node.put("key", getKeyString(key));
				    node.put("value", value);
				}
				array.add(node);
			}
			configsNode.put("keysList", array);
			return configsNode;
		}).collect(Collectors.toList());
		
		data.put("configs", configs);
        return data;
	}
	
	private String getKeyString(String text) {
		StringBuilder builder = new StringBuilder();
		for (char c : text.toCharArray()) {
		  if (Character.isUpperCase(c) && builder.length() > 0) builder.append(' ');
		  builder.append(c);
		}
		return builder.toString();
	}
}
