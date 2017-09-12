package org.lendingclub.trident.provision;

public interface NodeProvisioningDecorator  {

	
	ProvisioningContext apply(ProvisioningContext ctx);
	String apply(ProvisioningContext ctx, String script);
}
