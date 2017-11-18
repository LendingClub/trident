package org.lendingclub.trident.swarm;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.provision.SwarmTemplateManager;
import org.lendingclub.trident.util.JsonUtil;
import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

@Controller
public class CreateSwarmController {

    Logger logger = LoggerFactory.getLogger(org.lendingclub.trident.swarm.CreateSwarmController.class);

    @Autowired
    NeoRxClient neo4j;
    PrettyTime prettyTime = new PrettyTime();

    @Autowired
    SwarmTemplateManager swarmTemplateManager;

    @Autowired
    SwarmClusterManager swarmClusterManager;

    @Autowired
    CreateSwarmDataFormatting createSwarmDataFormatting;

    @RequestMapping(value = "/create-swarm-step-1", method = {RequestMethod.GET, RequestMethod.POST})
    public ModelAndView inputSwarmTemplates(HttpServletRequest request) {
        List<JsonNode> templateNames = swarmTemplateManager.findTemplates();
        Map<String, Object> data = Maps.newHashMap();

        data.put("templateNames", templateNames);

        data.put("step1", true);
        data.put("step2", false);
        data.put("step3", false);
        return new ModelAndView("create-swarm-wizard", data);
    }

    @RequestMapping(value = "/create-swarm-step-2", method = {RequestMethod.GET, RequestMethod.POST})
    public ModelAndView confirmSwarmInfo(HttpServletRequest request) {
        String swarmName = Strings.nullToEmpty(request.getParameter("swarm_name")).trim();
        String templateName = Strings.nullToEmpty(request.getParameter("templates")).trim();

        Optional<JsonNode> optionalTemplateInfo = swarmTemplateManager.getTemplate(templateName);
        JsonNode templateInfo = optionalTemplateInfo.get();


        Map<String, Object> data = Maps.newHashMap();

        List<JsonNode> dictionary = Lists.newArrayList();
        ObjectMapper mapper = new ObjectMapper();

        List<JsonNode> result = createSwarmDataFormatting.dataReformat(templateInfo);

        data.put("result", result);

        boolean isValid =validateInput(swarmName);

        if(!isValid)//if input is invalid, redirect user to main page
        {
            List<JsonNode> templateNames = swarmTemplateManager.findTemplates();
            data.put("templateNames", templateNames);
            data.put("Invalid", true);
            data.put("step1", true);
            data.put("step2", false);
            data.put("step3", false);
            return new ModelAndView("create-swarm-wizard", data);
        }
        data.put("swarmName", swarmName);
        data.put("templateName", templateName);
        data.put("templateRawData", prettyPrintJsonString(templateInfo));

        data.put("templateInfo", templateInfo);

        data.put("step1", false);
        data.put("step2", true);
        data.put("step3", false);

        return new ModelAndView("create-swarm-wizard", data);
    }

    @RequestMapping(value = "/create-swarm-step-3", method = {RequestMethod.GET, RequestMethod.POST})
    public ModelAndView executeSwarm(HttpServletRequest request) {
        String confirmedSwarmName = Strings.nullToEmpty(request.getParameter("hidden_sname")).trim();
        String confirmedTemplateName = Strings.nullToEmpty(request.getParameter("hidden_stemplate")).trim();

        ObjectNode node = new ObjectMapper().createObjectNode();

        node.put("name", confirmedSwarmName);
        node.put("template", confirmedTemplateName);

        JsonNode createSwarmInput = node;

        JsonNode createSwarmOutput = swarmClusterManager.createSwarm(createSwarmInput);

        String tridentClusterName = createSwarmOutput.path("tridentClusterName").asText();
        String tridentClusterId = createSwarmOutput.path("tridentClusterId").asText();

        Map<String, Object> data = Maps.newHashMap();
        JsonNode managerAutoscalingGroupName = createSwarmDataFormatting.getManagerAutoScalingGroup(createSwarmOutput);
        JsonNode workerAutoscalingGroupName = createSwarmDataFormatting.getWorkerAutoScalingGroup(createSwarmOutput);

        String message = "Swarm created. ASG's created: " + prettyPrintJsonString(managerAutoscalingGroupName) + ", "+ prettyPrintJsonString(workerAutoscalingGroupName);
        data.put("label", "success");
        data.put("message", message);
        data.put("displayMessage", "block");

        data.put("confirmedSwarmName", confirmedSwarmName);
        data.put("confirmedTemplateName", confirmedTemplateName);
        data.put("tridentClusterName", tridentClusterName);
        data.put("tridentClusterId", tridentClusterId);

        data.put("step1", false);
        data.put("step2", false);
        data.put("step3", true);

        return new ModelAndView("create-swarm-wizard", data);
    }
    public String prettyPrintJsonString(JsonNode jsonNode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object json = mapper.readValue(jsonNode.toString(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return "Sorry, pretty print didn't work";
        }
    }
    public boolean validateInput(String input) {
        if(input.length()==0){
            return false;
        }
        return input.matches("^[a-zA-Z0-9_-]*$");
    }
}