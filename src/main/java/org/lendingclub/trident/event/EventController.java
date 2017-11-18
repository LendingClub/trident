package org.lendingclub.trident.event;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.util.JsonUtil;
import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;

@Controller
public class EventController {
    Logger logger = LoggerFactory.getLogger(EventController.class);
    
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm a zzz");
    
    @Autowired
    NeoRxClient neo4j;
    
    PrettyTime prettyTime = new PrettyTime();

    @RequestMapping(value = "/events", method = { RequestMethod.GET })
    public ModelAndView eventDetail() {
        List<ObjectNode> allEvents = returnEvents();
        Map<String, Object> data = Maps.newHashMap();
        data.put("events", allEvents);
        return new ModelAndView("events", data);
    }

    public List<ObjectNode> returnEvents() {
        return neo4j.execCypher("match (x:TridentEventLog) where exists (x.eventDate) return x order by x.eventTs desc limit 1000").map(it->{
                        	ObjectNode n = ObjectNode.class.cast(it);
                        	n.put("eventType", n.path("eventType").asText().replace("trident.",""));
                        	n.put("eventTime", DATETIME_FORMATTER.print(n.path("eventTs").asLong()));
                        	return n;
                        }).toList().blockingGet();
    }
    
    @RequestMapping(value = "/events/{eventId}", method = { RequestMethod.GET, RequestMethod.POST })
    public ModelAndView eventDetail(@PathVariable("eventId") String eventId) {

        String eventQuery = "match(x:TridentEventLog {eventId: {eventId}}) "
                    + "return x";
        Map<String,Object> data = new HashMap<>();
        JsonNode n = neo4j.execCypher(eventQuery, "eventId", eventId).blockingFirst(MissingNode.getInstance());
        ((ObjectNode)n).put("eventTime", DATETIME_FORMATTER.print(n.path("eventTs").asLong()));
        
        String prettyPrintedRawData = "";
        try {
        	prettyPrintedRawData = JsonUtil.prettyFormat(JsonUtil.getObjectMapper().readTree(n.path("eventRawData").asText(""))); 
        
        	 data.put("eventRawData", prettyPrintedRawData);
        }
        catch (IOException e) {
        	logger.warn("problem formatting",e);
        }
        data.put("event", n);
       
        return new ModelAndView("events-details", data);
    }
}