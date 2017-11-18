package org.lendingclub.trident.provision;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentEndpoints;
import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.swarm.SwarmNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.TemplateLoader;
import com.samskivert.mustache.Template;

public class SwarmNodeProvisionContext {

	Logger logger = LoggerFactory.getLogger(SwarmNodeProvisionContext.class);
	Map<String, Object> data = Maps.newHashMap();

	private static final String EXPORT_SET_KEY = "__exportSet";
	public static final String TRIDENT_CLUSTER_ID_KEY = "id";
	public static final String IP_ADDR_KEY = "ipAddr";
	public static final String OS_KEY = "os";
	public static final String OPERATION_KEY = "operation";
	public static final String REQUEST_ID_KEY = "requestId";
	public static final String TRIDENT_BASE_URL_KEY = "tridentBaseUrl";
	public static final String TEMPLATE_NAME_KEY = "scriptTemplateName";
	public static final String EXPORTED_VARIABLES_BLOCK_KEY = "exportedVariablesBlock";

	public SwarmNodeProvisionContext() {
		data.put("tridentBaseUrl",Trident.getApplicationContext().getBean(TridentEndpoints.class).getAPIEndpoint());

		data.put("dockerRepoUrl", "https://download.docker.com/linux/centos/docker-ce.repo");
		data.put("dockerPackages", "docker-ce-17.06.2.ce container-selinux");
		data.put("os", "centos");
		data.put("DOCKER_BRIDGE_IP", "192.168.127.1/24");  // just a default
		data.put("DOCKER_GWBRIDGE_SUBNET", "192.168.128.0/24"); // just a default

		getExports().addAll(ImmutableSet.of("http_proxy", "https_proxy", "no_proxy", "HTTP_PROXY", "HTTPS_PROXY",
				"NO_PROXY", TRIDENT_CLUSTER_ID_KEY, IP_ADDR_KEY));
	}

	public SwarmNodeType getNodeType() {
		if (getString("swarmNodeType").orElse(getString("nodeType").orElse("UNKNOWN")).toUpperCase().equals(SwarmNodeType.MANAGER.toString())) {
			return SwarmNodeType.MANAGER;
		} else {
			return SwarmNodeType.WORKER;
		}
	}

	public SwarmNodeProvisionContext withScriptTemplateName(String name) {
		
		data.put(TEMPLATE_NAME_KEY, name);
		return this;
	}

	public Optional<String> getString(String key) {
		Object val = data.get(key);
		if (val == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(val.toString());
	}

	public SwarmNodeProvisionContext withServletRequest(HttpServletRequest request) {
		data.put(ServletRequest.class.getName(), request);
		return this;
	}

	public Set<String> getExports() {
		Set<String> set = (Set<String>) data.get(EXPORT_SET_KEY);
		if (set == null) {
			set = Sets.newConcurrentHashSet();
			data.put(EXPORT_SET_KEY, set);
		}
		return set;
	}

	public SwarmNodeProvisionContext withExport(String export) {
		getExports().add(export);
		return this;
	}

	public Optional<String> getTridentClusterId() {
		Optional<String> id = getString(TRIDENT_CLUSTER_ID_KEY);
		if (!id.isPresent()) {
			
			id = getString("tridentClusterId");
			if (id.isPresent()) {
				
			}
			return id;
		}
		
		String val = id.get().trim();
		if (Strings.isNullOrEmpty(val)) {
			data.put("tridentClusterId", val); 
			return Optional.empty();
		}
		
		
		return id;
	}

	public HttpServletRequest getServletRequest() {
		return (HttpServletRequest) data.get(ServletRequest.class.getName());
	}

	public Map<String, Object> getAttributes() {
		return data;
	}

	public SwarmNodeProvisionContext withAttribute(String key, String val) {
		this.data.put(key, val);
		return this;
	}

	public SwarmNodeProvisionContext withBaseUrl(String url) {
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return withAttribute(TRIDENT_BASE_URL_KEY, url);
	}

	public SwarmNodeProvisionContext withNoProxy(String... noProxyHosts) {

		String noProxy = Joiner.on(",").skipNulls().join(noProxyHosts);
		return withAttribute("no_proxy", noProxy).withAttribute("NO_PROXY", noProxy);

	}

	public SwarmNodeProvisionContext withHttpProxy(String proxyUrl) {
		return withAttribute("HTTP_PROXY", proxyUrl).withAttribute("http_proxy", proxyUrl)
				.withAttribute("HTTPS_PROXY", proxyUrl).withAttribute("https_proxy", proxyUrl);
	}

	public SwarmNodeManager getProvisioningManager() {
		return (SwarmNodeManager) data.get(SwarmNodeManager.class.getName());
	}

	public SwarmNodeProvisionContext withOS(String os) {
		return withAttribute(OS_KEY, os);
	}

	public SwarmNodeProvisionContext withOperation(String name) {
		return withAttribute(OPERATION_KEY, name);
	}


	public String getScriptTemplateName() {
		Object val = getAttributes().get(TEMPLATE_NAME_KEY);
		if (val != null) {
			return val.toString();
		} else {
			return null;
		}
	}

	boolean isExported(String key, Object v) {

		if (key == null) {
			return false;
		}
		if (key.startsWith("TRIDENT") || key.startsWith("DOCKER")) {
			return true;
		}
		Set<String> exports = getExports();
		if (!exports.contains(key)) {
			return false;
		}
		if (v == null || !(v instanceof String)) {
			return false;
		}
		if (key.contains(".") || key.contains(";") || key.contains("\'")) {
			return false;
		}
		String val = v.toString();
		if (key.contains(".") || key.contains(";") || val.contains("\'")) {
			return false;
		}

		if (key.equals("swarmJoinTokenManaagerOutput") || key.contains("swarmInitOutput")) {
			// some special-casing...we can smarten this up so that a
			// whitelist/blacklist can be set
			return false;
		}
		return true;
	}

	public String createExportEnvVarBlock(Map<String, Object> copy) {
		StringWriter sw = new StringWriter();
		try (PrintWriter pw = new PrintWriter(sw)) {

			pw.println("");
			pw.println("# Variables exported by " + getClass().getName());
			copy.forEach((k, v) -> {
				if (v != null && v instanceof String && isExported(k, v)) {
					String val = (v == null) ? "" : v.toString();

					pw.println("export " + k + "=\'" + val + "\'");

				}
			});
			pw.println("# End of variable export");
			pw.println();
		}
		String exportBlock = sw.toString();
		return exportBlock;
	}

	String generateScript() {
		Preconditions.checkState(!Strings.isNullOrEmpty(getScriptTemplateName()));
		try {
			String templateName = getScriptTemplateName();
			logger.info("scriptTemplateName: {}", templateName);
			TemplateLoader tl = new TemplateLoader() {

				@Override
				public Reader getTemplate(String name) throws Exception {
					 name = "templates/provision/" + getAttributes().get("os") + "/" + name+".hbs";
					 
					 File f = new File("./src/main/resources",name);
					 if (f.exists()) {
						 return new FileReader(f);
					 }
					 
					 InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
					 if (is!=null) {
						 return new InputStreamReader(is);
					 }
					 throw new TridentException("not found: "+name);
				}
			};

			Reader r = tl.getTemplate(templateName);
			Map<String, Object> copy = Maps.newHashMap(data);
			Template template = Mustache.compiler().defaultValue("").escapeHTML(false).withLoader(tl).compile(r);

			logger.info("data passed to template: {}", SwarmNodeManager.toSanitizedMap(copy));
			copy.put(EXPORTED_VARIABLES_BLOCK_KEY, createExportEnvVarBlock(copy));

			return template.execute(copy);
		} catch (Exception e) {
			throw new TridentException(e);
		}

	}
}
