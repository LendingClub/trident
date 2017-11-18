package org.lendingclub.trident.swarm.auth;

import java.util.function.Consumer;

public interface DockerPluginAuthorizationVoter  {

	public void authorize(DockerPluginAuthorizationContext ctx);
	
}
