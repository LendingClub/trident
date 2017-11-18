package org.lendingclub.trident.scheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.lendingclub.neorx.NeoRxClient;
import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;

import it.sauronsoftware.cron4j.SchedulingPattern;

@Controller
public class TaskScheduleController {
    Logger logger = LoggerFactory.getLogger(TaskScheduleController.class);
    @Autowired
    NeoRxClient neo4j;

    PrettyTime prettyTime = new PrettyTime();

    @RequestMapping(value = "/task-schedule", method = { RequestMethod.GET })
    public ModelAndView TridentSchedulerDetail(RedirectAttributes redirect) {
        List<JsonNode> allExecutedTasks = returnScheduledTasks();
        Map<String, Object> data = Maps.newHashMap();
        data.put("tasks", allExecutedTasks);
        return new ModelAndView("task-schedule", data);
    }

    @RequestMapping(value = "/task-schedule/{scheduleId}/modify", method = { RequestMethod.GET, RequestMethod.POST })
    public ResponseEntity modifySchedule(@RequestParam(value = "value") String schedule, 
    		@PathVariable("scheduleId") String scheduleId,
    		HttpServletRequest request,
	        RedirectAttributes redirect) {
		logger.info("Request received for modifying task schedule of {} with new value {}", scheduleId, schedule);
		schedule = schedule.trim();
		boolean isValid = validateCronExpression(schedule);
		String message = "";
		int statusCode = 200;
		boolean valueChanged = false;
		Map<String, Object> map = new HashMap<>();
		if(isValid && !Strings.isNullOrEmpty(schedule)) {
	        String cypher = "match (x:TridentTaskSchedule { scheduleId:{id} } ) set x.cronExpression={schedule} return x;";
	        neo4j.execCypher(cypher, "scheduleId", scheduleId, "schedule", schedule);
			message = "Updated the cron expression for task id " + scheduleId + " with new value " + schedule;
			statusCode = HttpStatus.OK.value();
			valueChanged = true;
		}else {
			message = schedule + " is invalid. Please enter proper cron value for " + scheduleId;
			statusCode = HttpStatus.BAD_REQUEST.value();
		}
		logger.info(message);
        map.put("statusCode", statusCode);
        map.put("statusMessage", message);
        
        if(!valueChanged)
            return ResponseEntity.badRequest().body(map);
        else
        	return ResponseEntity.ok(map);
    }

    public List<JsonNode> returnScheduledTasks() {
        return neo4j.execCypher("match (x:TridentTaskSchedule) return x order by x.taskClass desc").map(
                		n-> (JsonNode) ObjectNode.class.cast(n).put("enabledStatus", n.path("enabled").asBoolean(false) ? "enabled" : "disabled")).toList().blockingGet();
    }

    private boolean validateCronExpression(String exp) {
    	return SchedulingPattern.validate(exp);
    }
}