package org.lendingclub.trident.provision;

public interface SwarmNodeProvisionInterceptor  {

	
	SwarmNodeProvisionContext apply(SwarmNodeProvisionContext ctx);
	String apply(SwarmNodeProvisionContext ctx, String script);
}
