package org.lendingclub.trident.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
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
import java.util.*;
import java.util.stream.Collectors;


@Controller
public class TridentSchedulerHistory {

    Logger logger = LoggerFactory.getLogger(TridentSchedulerHistory.class);
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



    //read in neo4j tables
    public List<JsonNode> returnScheduledTasks() {
        return neo4j.execCypherAsList("match (x:TridentTask) return x.task as task," +
                "x.pattern as pattern," +
                "x.submitTs as submitTs," +
                "x.startTs as startTs," +
                "x.tridentTaskId as tridentTaskId," +
                "x.enabled as enabled order by x.submitTs desc");
    }

}
