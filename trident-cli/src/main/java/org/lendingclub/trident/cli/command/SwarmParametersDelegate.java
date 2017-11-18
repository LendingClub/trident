package org.lendingclub.trident.cli.command;

import java.util.Optional;

import com.beust.jcommander.Parameter;

public class SwarmParametersDelegate {

	public SwarmParametersDelegate() {
	
	}

	@Parameter(names = { "-s","--swarm" }, description = "swarm name", required=true)
	private String swarmName = null;
	
	
	public Optional<String> getSwarmName() {
		return Optional.ofNullable(swarmName);
	}
}
