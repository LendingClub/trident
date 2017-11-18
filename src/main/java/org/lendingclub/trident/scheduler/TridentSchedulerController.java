package org.lendingclub.trident.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.util.JsonUtil;
import org.ocpsoft.prettytime.PrettyTime;
import org.rapidoid.u.U;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TridentSchedulerController {
	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm a zzz");

	Logger logger = LoggerFactory.getLogger(TridentSchedulerController.class);
	@Autowired
	NeoRxClient neo4j;

	PrettyTime prettyTime = new PrettyTime();

	@RequestMapping(value = "/scheduler-history", method = { RequestMethod.GET })
	public ModelAndView TridentSchedulerDetail() {
		List<JsonNode> allExecutedTasks = returnScheduledTasks();
		Map<String, Object> data = Maps.newHashMap();
		data.put("tasks", allExecutedTasks);
		return new ModelAndView("scheduler-history", data);
	}

	// read in neo4j tables
	public List<JsonNode> returnScheduledTasks() {
		List<JsonNode> queryResult = neo4j.execCypher("match (x:TridentTask) return x order by x.startTs desc")
				.map(it -> {
					ObjectNode item = ObjectNode.class.cast(it);
					
					long startTs = it.path("startTs").asLong(0);
					long endTs = it.path("endTs").asLong(0);
					
					item.put("startDisplayTime", DATETIME_FORMATTER.print(it.path("startTs").asLong(0)));
					
				
					List<String> nameParts = Splitter.on(".").splitToList(it.path("taskClass").asText());
					item.put("shortTaskClass", nameParts.get(nameParts.size()-1));
					if (endTs>0) {
						item.put("endTs", DATETIME_FORMATTER.print(it.path("endTs").asLong(0)));
					}
					long executionTimeMillis = endTs>0 ? (endTs - startTs) : (System.currentTimeMillis()-startTs);
					
					item.put("executionTimeMillis", executionTimeMillis);
	
					return it;
				}).toList().blockingGet();

	

		return queryResult;
	}

}
