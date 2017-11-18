package org.lendingclub.trident.haproxy;

import net.lingala.zip4j.core.ZipFile;
import javax.servlet.http.HttpServletRequest;

public class HAProxyConfigBundleDiscoveryContext extends HAProxyBootstrapConfigContext {
	String environment;
	String subEnvironment;
	String serviceNode;
	String serviceCluster;
	String serviceZone;

	protected ZipFile bundle;

	public HAProxyConfigBundleDiscoveryContext withConfigZipBundle(ZipFile zipFile) {
		this.bundle = zipFile;
		return this;
	}

	public HAProxyConfigBundleDiscoveryContext() {
		super();
	}

	public HAProxyConfigBundleDiscoveryContext(String d) {
		super(d);
	}

	HAProxyConfigBundleDiscoveryContext withSkeleton(HttpServletRequest request) {
		withServletRequest(request);
		this.config = "<%\n" + "    def hostInfo = { def hostsLBConfig = \"\";\n"
				+ "                     new org.lendingclub.haproxy.HostInfoUtils().getHostInfoForAppId(it).eachWithIndex { host, index ->\n"
				+ "                        def hostConfig= \"server \"+it+\"_\"+index+\" \"+host.getHostName()+\":\"+host.getPort()+\" weight \"+host.getPriority();\n"
				+ "                        hostsLBConfig = hostsLBConfig + hostConfig;\n" + "                     }\n"
				+ "                     hostsLBConfig;\n" + "    }\n" + "%>\n" + "\n" + "\n" + "global\n"
				+ "    tune.ssl.default-dh-param 2048\n" + "\n" + "defaults\n" + "    mode http\n"
				+ "    option httplog\n" + "    log 127.0.0.1 local0 info\n" + "    timeout client  4s\n"
				+ "    timeout connect 4s\n" + "    timeout server  4s\n" + "\n" + "listen stats\n" + "    bind :8100\n"
				+ "    mode http\n" + "    stats enable\n" + "    stats hide-version\n"
				+ "    stats realm Haproxy\\\\ Statistics\n" + "    stats uri /haproxy-stats\n"
				+ "    stats auth user:pass\n" + "\n" + "frontend http-in\n" + "    mode http\n"
				+ "    log 127.0.0.1 local0\n" + "    bind *:8003";
		return this;
	}

}
