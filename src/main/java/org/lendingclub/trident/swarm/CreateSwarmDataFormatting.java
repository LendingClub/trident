package org.lendingclub.trident.swarm;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import springfox.documentation.spring.web.json.Json;
import java.util.HashMap;
import java.util.*;
import java.util.Arrays;
import java.util.List;

public class CreateSwarmDataFormatting {

    Logger logger = LoggerFactory.getLogger(org.lendingclub.trident.swarm.CreateSwarmDataFormatting.class);
    List<JsonNode> dictionary = Lists.newArrayList();
    ObjectMapper mapper = new ObjectMapper();

    void addDictionaryEntry(String key, String displayName) {
        dictionary.add(mapper.createObjectNode().put("key",key).put("displayName",displayName));
    }
    public CreateSwarmDataFormatting(){
        addDictionaryEntry("awsManagerInstanceProfile","AWS Manager Instance Profile");
        addDictionaryEntry("awsWorkerHostedZoneAccount","AWS Worker Hosted Zone Account");
        addDictionaryEntry("awsManagerInstanceType","AWS Manager Instance Type");
        addDictionaryEntry("awsWorkerImageId","AWS Worker Image ID");
        addDictionaryEntry("awsWorkerInstanceProfile","AWS Worker Instance Profile");
        addDictionaryEntry("awsManagerImageId","AWS Manager Image ID");
        addDictionaryEntry("awsManagerHostedZoneAccount","AWS Manager Hosted Zone Account");
        addDictionaryEntry("awsManagerHostedZone","AWS Manager Hosted Zone");
        addDictionaryEntry("managerDnsName","AWS Manager DNS Name");
        addDictionaryEntry("awsWorkerInstanceType","AWS Worker Instance Type");
        addDictionaryEntry("awsRegion","AWS Region");
        addDictionaryEntry("awsAccount","AWS Account");
        addDictionaryEntry("dockerPackages","Docker Packages");
        addDictionaryEntry("awsManagerSubnets","Manager Subnets");
        addDictionaryEntry("templateName","Template");
        addDictionaryEntry("awsWorkerSecurityGroups","AWS Worker Security Group");
        addDictionaryEntry("DOCKER_BRIDGE_IP","Docker Bridge IP");
        addDictionaryEntry("awsWorkerSubnets","AWS Worker Subnet");
        addDictionaryEntry("awsManagerSecurityGroups","AWS Manager Security Groups");
    }
    public List<JsonNode> dataReformat(JsonNode input) {
        List<JsonNode> answer = new ArrayList<JsonNode>();
        ObjectMapper mapper = new ObjectMapper();

        for (JsonNode n : dictionary ) {
            String dictionaryKey = n.get("key").asText();

            input.fieldNames().forEachRemaining(i -> {
                        String inputKey = i;

                        String inputValue = input.get(i).asText();

                        if(inputKey.equals(dictionaryKey)){
                            String newName = n.get("displayName").asText();
                            answer.add(mapper.createObjectNode().put("originalName", inputKey).put("displayName", newName).put("value", inputValue));
                        }
            });
        }
        return answer;
    }

    public JsonNode getManagerAutoScalingGroup(JsonNode input){
        return input.path("manager").path("autoScalingGroup").path("autoScalingGroupName");
    }

    public JsonNode getWorkerAutoScalingGroup(JsonNode input){
        return input.path("worker").path("autoScalingGroup").path("autoScalingGroupName");
    }

    public static String prettyPrintJsonString(JsonNode jsonNode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object json = mapper.readValue(jsonNode.toString(), Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return "Sorry, pretty print didn't work";
        }
    }
}