package org.lendingclub.trident.cluster;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.annotation.PostConstruct;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.layout.NavigationManager;
import org.lendingclub.trident.util.JsonUtil;
import org.ocpsoft.prettytime.PrettyTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

@Controller
public class TridentClusterController {

	@Autowired
	TridentClusterManager tridentClusterManager;

	@Autowired
	NavigationManager navigationManager;

	@Autowired
	NeoRxClient neo4j;

	@RequestMapping(value = "/trident-cluster", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView tridentCLuster() {
		PrettyTime pt = new PrettyTime();

		List<JsonNode> data = neo4j.execCypher("match (x:TridentClusterState) return x order by x.leader desc")
				.map(it -> {
					ObjectNode x = ObjectNode.class.cast(it);
					if (!x.path("leader").asBoolean(false)) {
						x.remove("leader");
					}
					if (tridentClusterManager.getInstanceId().equals(it.path("instanceId").asText())) {
						x.put("self", true);
					}
					if (!x.path("eligibleLeader").asBoolean(false)) {
						x.remove("eligibleLeader");
					}
					x.put("electionPrettyTime", pt.format(new Date(it.path("electionTs").asLong())));
					x.put("instanceStartPrettyTime", pt.format(new Date(it.path("instanceStartTs").asLong())));
					return (JsonNode) x;
							
				}).toList()

				.blockingGet();

		return new ModelAndView("trident-cluster", ImmutableMap.of("nodes", data));

	}

	@PostConstruct
	public void setupNav() {
		navigationManager.addSidebarItemDecorator("admin", "Cluster", "/trident-cluster");
	}
}
