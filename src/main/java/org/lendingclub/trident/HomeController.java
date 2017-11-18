package org.lendingclub.trident;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.lendingclub.neorx.NeoRxClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Maps;

@Controller
public class HomeController {

	@Autowired
	NeoRxClient neo4j;
	
	Supplier<Map<String,Object>> statsSupplier = Suppliers.memoizeWithExpiration(this::computeStats, 15, TimeUnit.SECONDS);
	Map<String,Object> computeStats() {
		Map<String,Object> data = Maps.newHashMap();
		data.put("swarmCount", neo4j.execCypher("match (a:DockerSwarm) return count(a) as cnt").blockingFirst().asLong());
		data.put("containerCount", neo4j.execCypher("match (n:DockerSwarm)--(s:DockerService)--(a:DockerTask) where a.state='running' return count(a) as cnt").blockingFirst().asLong());
		data.put("serviceCount", neo4j.execCypher("match (s:DockerSwarm)--(a:DockerService) return count(a) as cnt").blockingFirst().asLong());
		data.put("swarmNodeCount", neo4j.execCypher("match (s:DockerSwarm)--(a:DockerHost) return count(a) as cnt").blockingFirst().asLong());
		data.put("appsCount", neo4j.execCypher("match (a:AppCluster) return count(a) as cnt").blockingFirst().asLong());
		return data;
	}
 
	@RequestMapping(value = "/home", method = { RequestMethod.GET, RequestMethod.POST })
	public ModelAndView home() {
		
		Map<String,Object> data = Maps.newHashMap();
		
		data.putAll(computeStats());
		
		return new ModelAndView("home",data);
	}	
}
