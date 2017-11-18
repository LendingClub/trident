package org.lendingclub.trident.cli.command;

import org.lendingclub.okrest3.OkRestResponse;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Parameters(commandDescription = "Create swarm")
public class CreateSwarmCommand extends Command {

	@ParametersDelegate
	SwarmParametersDelegate swarmParam = new SwarmParametersDelegate();
	
	@Parameter(names = { "-t","--template" }, description = "template",required=true)
	private String template = null;
	
	@Override
	public void doParse() {
	
		super.doParse();
	}

	@Override
	public void doRun() {

		ObjectNode request = mapper.createObjectNode();
		request.put("name", swarmParam.getSwarmName().get());
		request.put("template", template);
		OkRestResponse r = getTridentRestTarget().path("/api/trident/swarm/create").post(request).execute();
		
		System.out.println(r.response().code());
	}



}
