package org.lendingclub.trident.haproxy;

public abstract class HAProxyResourceProvider {

	public class ResourceRequest {

		String env;
		String subEnv;
		String serviceGroup;
		String region;
		String resourceName;

		public ResourceRequest withEnv(String env) {
			this.env = env;
			return this;
		}

		public ResourceRequest withSubEnv(String subEnv) {
			this.subEnv = subEnv;
			return this;
		}

		public ResourceRequest withServiceGroup(String serviceGroup) {
			this.serviceGroup = serviceGroup;
			return this;
		}

		public ResourceRequest withRegion(String region) {
			this.region = region;
			return this;
		}

		public ResourceRequest withResourceName(String resourceName) {
			this.resourceName = resourceName;
			return this;
		}

		public String execute() {
			return HAProxyResourceProvider.this.execute(this);
		}
	}

	public ResourceRequest newRequest() {
		return new ResourceRequest();
	}


	abstract String execute(ResourceRequest resourceRequest);

}
