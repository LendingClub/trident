package org.lendingclub.trident;

import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.config.ConfigManager;
import org.lendingclub.trident.crypto.CryptoService;
import org.lendingclub.trident.crypto.TridentCryptoKeyStoreManager;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

public class ConfigManagerTest extends TridentIntegrationTest {

	@Autowired
	ConfigManager configManager;
	
	@Autowired
	NeoRxClient neo4j;
	
	@Autowired
	CryptoService crypto;
	
	@Test
	public void testIt() {
		
		neo4j.execCypher("match (a:Config) where a.type=~'junit.*' delete a");
		
		configManager.reload();
		
		Assertions.assertThat(configManager.getConfigOfType("junit")).isEmpty();
		Assertions.assertThat(configManager.getConfigOfType("junit1")).isEmpty();
		neo4j.execCypher("merge (a:Config {type:{type},name:{name}}) set a.fizz='buzz' return a","type","junit","name","foo");
		neo4j.execCypher("merge (a:Config {type:{type},name:{name}}) set a.fizz='buzz2' return a","type","junit","name","foo2");
		
		
		String encryptedVal = crypto.encrypt("ding");
	
		neo4j.execCypher("merge (a:Config {type:{type},name:{name}}) set a.fizz={fizz} return a","type","junit1","name","foo","fizz",encryptedVal);

		configManager.reload();
		
	
		System.out.println(configManager);
		
		Assertions.assertThat(configManager.getConfigOfType("junit").keySet()).hasSize(2).contains("foo","foo2");
		Assertions.assertThat(configManager.getConfigOfType("junit").get("foo")).isSameAs(configManager.getConfig("junit", "foo").get());
		Assertions.assertThat(configManager.getConfig("junit", "foo").get().path("name").asText()).isEqualTo("foo");
		Assertions.assertThat(configManager.getConfig("junit", "foo").get().path("type").asText()).isEqualTo("junit");
		Assertions.assertThat(configManager.getConfig("junit", "foo").get().path("fizz").asText()).isEqualTo("buzz");
		
		Assertions.assertThat(configManager.getConfig("junit1", "foo").get().path("name").asText()).isEqualTo("foo");
		Assertions.assertThat(configManager.getConfig("junit1", "foo").get().path("type").asText()).isEqualTo("junit1");
		Assertions.assertThat(configManager.getConfig("junit1", "foo").get().path("fizz").asText()).isEqualTo("ding");
		
		Assertions.assertThat(configManager.getConfigOfType(UUID.randomUUID().toString())).isEmpty();
		Assertions.assertThat(configManager.getConfig("junit", UUID.randomUUID().toString()).isPresent()).isFalse();
	}
	
	@Test
	public void testEncrypt() {
		configManager.setValue("junitx", "a", "z", "26", true);
		
	
		JsonNode n = neo4j.execCypher("match (c:Config) where c.name={name} and c.type={type} return c","name","a","type","junitx").blockingFirst();
		Assertions.assertThat(n.path("z").asText()).startsWith("QHsic");
		configManager.reload();
		
		Assertions.assertThat(configManager.getConfig("junitx", "a").get().path("z").asText()).isEqualTo("26");
	}
}
