package org.lendingclub.trident.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.Files;

public class ConfigManager {

	static ObjectMapper mapper = new ObjectMapper();

	JsonNode config;

	public File getDotTridentDir() {
		File dotTridentDir = new File(System.getProperty("user.home"), ".trident");
		return dotTridentDir;
	}

	public void initConfig() throws IOException {

		if (!getDotTridentDir().isDirectory()) {
			getDotTridentDir().mkdirs();
		}
		File configFile = new File(getDotTridentDir(), "config");

		if (!configFile.exists()) {
			ObjectNode n = mapper.createObjectNode();
			Files.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(n), configFile, Charsets.UTF_8);

		}

	}

	public Optional<String> getProperty(String n) {
		return Optional.ofNullable(getConfig().path(n).asText(null));

	}

	public Properties getMacGyverConfig() {
		Properties p = new Properties();
		try {
			File config = new File(System.getProperty("user.home"), ".lc2/config");
			if (config.exists()) {

				try (FileInputStream fis = new FileInputStream(config)) {
					p.load(fis);
					
				}
				

			}
		} catch (IOException e) {
			throw new CLIFatalException(e);
		}
		return p;
	}

	public JsonNode getConfig() {

		try {

			File config = new File(getDotTridentDir(), "config");
			if (config.exists()) {
				Properties p = getMacGyverConfig();
				String val = p.getProperty("macgyver.token");
				
				ObjectNode n = (ObjectNode) mapper.readTree(config);
				if (!Strings.isNullOrEmpty(val)) {
					n.put("macgyver.token", val);
				}
				return n;
			} else {
				return MissingNode.getInstance();
			}

		} catch (IOException e) {
			throw new CLIException(e);
		}

	}
}
