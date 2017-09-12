package org.lendingclub.trident.agent;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TridentAgentTest {

	static ObjectMapper mapper = new ObjectMapper();
	
	public static ObjectNode getMockDockerInfo() throws JsonProcessingException, IOException {
		
		return (ObjectNode) mapper.readTree(TridentAgentTest.class.getClassLoader().getResourceAsStream("docker-info-response.json"));
	}
}
