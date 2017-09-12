package org.lendingclub.trident.swarm.aws;

import org.lendingclub.trident.provision.ProvisioningContext;
import org.lendingclub.trident.provision.NodeProvisioningDecorator;

public class AWSSwarmNodeProvisioningDecorator implements NodeProvisioningDecorator {

	@Override
	public ProvisioningContext apply(ProvisioningContext t) {
		
		// EC2 metadata will be passed as well-known variables
		// We will attempt to look up the istance/asg
		return t;
	}

	@Override
	public String apply(ProvisioningContext ctx, String script) {
		return script;
	}

}
