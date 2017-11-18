package org.lendingclub.trident.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import okhttp3.OkHttpClient;

public abstract class ProxyManager {



	public interface ProxyConfig {
	
		public String getNonProxyHosts();
		public String getHost();
		public int getPort();
		public Proxy getProxy();
	
	}
	
	public Optional<ProxyConfig> getDefaultProxyConfig() {
		return getProxyConfig("default");
	}
	
	public abstract Optional<ProxyConfig> getProxyConfig(String name);
	
	public abstract ProxySelector getProxySelector();
	public OkHttpClient.Builder applyProxy(OkHttpClient.Builder builder) {
	
		ProxySelector proxySelector = getProxySelector();
		if (proxySelector!=null) {
			builder = builder.proxySelector(getProxySelector());
		}
		return builder;
	}
	public void applyProxy(OkHttpClient.Builder builder, String name) {
		getProxyConfig(name).ifPresent(it->{
			applyProxy(builder, it);
		});
		
	}
	public void applyDefaultProxy(OkHttpClient.Builder builder) {
		getDefaultProxyConfig().ifPresent(it->{
			applyProxy(builder,it);
		});
	}
	public void applyProxy(OkHttpClient.Builder builder, ProxyConfig cfg) {
		if (cfg!=null) {
			Proxy p = cfg.getProxy();
			builder.proxy(p);
		}
	}
}
