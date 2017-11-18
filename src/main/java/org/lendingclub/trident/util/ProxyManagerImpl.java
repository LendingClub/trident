package org.lendingclub.trident.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.lendingclub.trident.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class ProxyManagerImpl extends ProxyManager {

	Cache<String, List<Proxy>> proxyCache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();
	Logger logger = LoggerFactory.getLogger(ProxyManagerImpl.class);
	@Autowired
	ConfigManager configManager;

	class ProxyConfigImpl implements ProxyConfig {
		JsonNode data;

		ProxyConfigImpl(JsonNode n) {
			this.data = n;
		}

		@Override
		public String getHost() {
			return data.path("host").asText();
		}

		@Override
		public int getPort() {
			return data.path("port").asInt(8080);
		}

		@Override
		public String getNonProxyHosts() {
			return data.path("nonProxyHosts").asText();
		}

		public Proxy getProxy() {
			return new Proxy(Type.HTTP, new InetSocketAddress(getHost(), getPort()));
		}
	}

	@Override
	public Optional<ProxyConfig> getProxyConfig(String name) {
		Optional<JsonNode> n = configManager.getConfig("proxy", name);
		if (!n.isPresent()) {
			return Optional.empty();
		}

		return Optional.of(new ProxyConfigImpl(n.get()));

	}

	@Override
	public ProxySelector getProxySelector() {

		ProxySelector ps = new ProxySelector() {

			@Override
			public List<Proxy> select(URI uri) {
				try {
					if (uri == null) {
						return ImmutableList.of();
					}
					String host = uri.getHost();
					if (host == null) {
						return ImmutableList.of();
					}

					List<Proxy> proxyList = proxyCache.getIfPresent(host);
					if (proxyList != null) {
						return proxyList;
					}

					Optional<ProxyConfig> pc = getProxyConfig("default");
					if (!pc.isPresent()) {
						return ImmutableList.of();
					}

					host = host.toLowerCase();
					if (host.contains("169.254.169.254")) {
						// AWS metadata should never use proxy
						proxyList = ImmutableList.of();
					} else if (host.startsWith("localhost")) {
						proxyList = ImmutableList.of();
					} else if (host.startsWith("127.0.0.1")) {
						proxyList = ImmutableList.of();
					} else if (host.startsWith("192.168.")) {
						proxyList = ImmutableList.of();
					} else if (host.startsWith("10.")) {
						proxyList = ImmutableList.of();
					} else if (host.startsWith("172.")) {
						for (int i = 16; i <= 31; i++) {
							String rfc1918 = "172." + i + ".";
							if (host.startsWith(rfc1918)) {
								proxyList = ImmutableList.of();
							}
						}
					} else {
						InetAddress[] addrList = InetAddress.getAllByName(host);
						if (addrList == null) {
							proxyList = ImmutableList.of();
						}
						for (InetAddress addr : addrList) {
							String ip = addr.getHostAddress();
							logger.info("{} => {}", host, ip);
							if (ip.startsWith("192.168.")) {
								proxyList = ImmutableList.of();
							}
							if (ip.startsWith("10.")) {
								proxyList = ImmutableList.of();
							}
							if (host.startsWith("127.0.0.1")) {
								proxyList = ImmutableList.of();
							}
							if (host.startsWith("172.")) {
								for (int i = 16; i <= 31; i++) {
									String rfc1918 = "172." + i + ".";
									if (host.startsWith(rfc1918)) {
										proxyList = ImmutableList.of();
									}
								}
							}
						}
						if (proxyList == null) {

							proxyList = ImmutableList.of(pc.get().getProxy());
						}

					}
					if (proxyList==null) {
						proxyList=ImmutableList.of();
					}
					if (proxyList != null) {
						proxyCache.put(host, proxyList);
						logger.info("chose proxy for {} => {}", uri, proxyList);
					}
					return proxyList;
				} catch (UnknownHostException e) {
					return ImmutableList.of();
				}
			}

			@Override
			public void connectFailed(URI uri, SocketAddress sa, IOException e) {
				logger.warn("connect failed uri={} socketAddress={} exception={}", uri, sa, e);

			}
		};
		return ps;
	}

}
