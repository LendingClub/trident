package org.lendingclub.trident.swarm.aws;

import org.lendingclub.trident.provision.SwarmNodeProvisionContext;
import org.lendingclub.trident.provision.SwarmNodeProvisionInterceptor;

public class AWSSwarmNodeProvisioningInterceptor implements SwarmNodeProvisionInterceptor {

	@Override
	public SwarmNodeProvisionContext apply(SwarmNodeProvisionContext t) {
		
		// EC2 metadata will be passed as well-known variables
		// We will attempt to look up the istance/asg
		return t;
	}

	@Override
	public String apply(SwarmNodeProvisionContext ctx, String script) {
		return script;
	}

}

