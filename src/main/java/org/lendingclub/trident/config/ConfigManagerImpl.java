package org.lendingclub.trident.config;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.crypto.CryptoService;
import org.lendingclub.trident.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class ConfigManagerImpl implements ConfigManager {

	Logger logger = LoggerFactory.getLogger(ConfigManagerImpl.class);
	
	@Autowired
	NeoRxClient neo4j;

	@Autowired
	CryptoService crypto;

	@Autowired
	Environment springEnv;

	AtomicReference<Map<String, Map<String, ObjectNode>>> configRef = new AtomicReference(Maps.newConcurrentMap());

	@Override
	public synchronized Optional<JsonNode> getConfig(String type, String name) {

		Map<String, ObjectNode> n = configRef.get().get(type);
		if (n == null) {
			return Optional.empty();
		}
		ObjectNode val = n.get(name);
		return Optional.ofNullable(val);

	}

	public void setValue(String type, String name, String key, String val, boolean encrypt) {
		ObjectNode props = JsonUtil.getObjectMapper().createObjectNode();
		props.remove("type");
		props.remove("name");
		if (encrypt && !crypto.isEncrypted(val)) {
			val = crypto.encrypt(val);
		}
		props.put(key, val);
		neo4j.execCypher("merge (a:Config {type:{type}, name:{name}}) set a+={props}, a.updateTs=timestamp()", "type",
				type, "name", name, "props", props);
	}

	public Map<String, JsonNode> getConfigOfType(String type) {
		Map<String, ObjectNode> m = configRef.get().get(type);
		if (m == null) {
			return Maps.newConcurrentMap();
		}

		return (Map<String, JsonNode>) Map.class.cast(m);
	}

	Map<String, String> loadNeo4jProperties() {
		Map<String, String> data = Maps.newConcurrentMap();
		neo4j.execCypher("match (c:Config) return c").forEach(it -> {
			String id = it.path("name").asText();
			String type = it.path("type").asText();
			if ((!Strings.isNullOrEmpty(id)) && (!Strings.isNullOrEmpty(type))) {
				String prefix = "config." + type + "." + id;
				it.fields().forEachRemaining(x -> {
					data.put(prefix + "." + x.getKey(), x.getValue().asText());
				});
			}

		});

		return data;
	}

	Map<String, String> loadSpringProperties() {
		Map<String, String> props = Maps.newConcurrentMap();
		MutablePropertySources propSrcs = ((AbstractEnvironment) springEnv).getPropertySources();
		StreamSupport.stream(propSrcs.spliterator(), false).filter(ps -> ps instanceof EnumerablePropertySource)
				.map(ps -> ((EnumerablePropertySource) ps).getPropertyNames()).flatMap(Arrays::<String>stream)
				.forEach(propName -> props.put(propName, springEnv.getProperty(propName)));
		return props;
	}

	Map<String, Map<String, ObjectNode>> loadConfig() {

		Map<String, String> props = Maps.newHashMap();

		Map<String, Map<String, ObjectNode>> data = Maps.newConcurrentMap();
		Pattern p = Pattern.compile("config\\.(.*?)\\.(.*?)\\.(.*)");
		props.putAll(loadNeo4jProperties());
		props.putAll(loadSpringProperties());
		props.forEach((k, v) -> {
			Matcher m = p.matcher(String.valueOf(k));
			if (m.matches()) {
				String type = m.group(1);
				String name = m.group(2);
				String key = m.group(3);
				Map<String, ObjectNode> configMap = data.get(type);
				if (configMap == null) {
					configMap = Maps.newConcurrentMap();
					data.put(type, configMap);
				}
				ObjectNode conifg = configMap.get(name);
				if (conifg == null) {
					conifg = JsonUtil.getObjectMapper().createObjectNode();
					configMap.put(name, conifg);
				}
				String value = v;
				if (crypto.isEncrypted(v)) {
					value = crypto.decryptString(v);
				}
				conifg.put(key, value);

			}
		});

		return data;
	}

	@PostConstruct
	public synchronized void reload() {

		try {
			this.configRef.set(ImmutableMap.copyOf(loadConfig()));
		} catch (Exception e) {
			// need to figure out if we should continue here or not
			logger.warn("",e);
		}

	}

}
