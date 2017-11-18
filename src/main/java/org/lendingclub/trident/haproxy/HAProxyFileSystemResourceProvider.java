package org.lendingclub.trident.haproxy;

public class HAProxyFileSystemResourceProvider extends HAProxyResourceProvider {

	@Override public ResourceRequest newRequest() {
		return null;
	}

	@Override String execute(ResourceRequest resourceRequest) {
		return "";
	}
}
