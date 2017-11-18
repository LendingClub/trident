package org.lendingclub.haproxy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Created by hasingh on 1/31/17.
 */

@Controller
@RequestMapping("/state")
public class ScramjetAPIController {

	ObjectMapper mapper = new ObjectMapper();

	public ScramjetAPIController()  {}

	@RequestMapping(value="/current", method= { RequestMethod.GET})
	@ResponseBody
	public ResponseEntity<JsonNode> getCurrentScramjet() throws IOException, InterruptedException {
		ObjectNode results = mapper.createObjectNode();
		List<String> lines = Lists.newArrayList();
		Process p = new ProcessBuilder().redirectErrorStream(true)
				.command("ps","-ef").start();
		try (InputStreamReader r = new InputStreamReader(p.getInputStream())) {
			lines = CharStreams.readLines(r);
		}
		int rc = p.waitFor();

		String commandResult = Joiner.on("\n").join(lines);
		results.put("startedAt", commandResult);
		return new ResponseEntity<JsonNode>(results, HttpStatus.OK);

	}

}
