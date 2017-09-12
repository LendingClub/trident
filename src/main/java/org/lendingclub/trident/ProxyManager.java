package org.lendingclub.trident;

import java.util.Map;
import java.util.Optional;

public abstract class ProxyManager {



	public interface ProxyConfig {
	
		public String getNonProxyHosts();
		public String getHost();
		public int getPort();
	}
	
	public Optional<ProxyConfig> getDefaultProxyConfig() {
		return getProxyConfig("default");
	}
	
	public abstract Optional<ProxyConfig> getProxyConfig(String name);
	
	
}
