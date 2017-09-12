package org.lendingclub.trident.config;

import java.security.Security;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.ignite.configuration.IgniteConfiguration;
import org.lendingclub.mercator.core.Projector;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.ExecuteScriptOnStart;
import org.lendingclub.trident.HomeController;
import org.lendingclub.trident.Main;
import org.lendingclub.trident.ProxyManager;
import org.lendingclub.trident.ProxyManagerImpl;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentEndpoints;
import org.lendingclub.trident.TridentSchemaManager;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.crypto.CertificateAuthorityManagerImpl;
import org.lendingclub.trident.crypto.TridentCryptoKeyStoreManager;
import org.lendingclub.trident.crypto.TridentCryptoProvider;
import org.lendingclub.trident.dashboard.DashboardController;
import org.lendingclub.trident.envoy.EnvoyBootstrapController;
import org.lendingclub.trident.envoy.EnvoyClusterDiscoveryController;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryController;
import org.lendingclub.trident.envoy.EnvoyRouteDiscoveryController;
import org.lendingclub.trident.envoy.EnvoyServiceDiscoveryController;
import org.lendingclub.trident.envoy.swarm.SwarmClusterDiscoveryDecorator;
import org.lendingclub.trident.envoy.swarm.SwarmListenerDiscoveryDecorator;
import org.lendingclub.trident.envoy.swarm.SwarmRouteDiscoveryDecorator;
import org.lendingclub.trident.envoy.swarm.SwarmServiceDiscoveryDecorator;
import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.event.Neo4jEventLogWriter;
import org.lendingclub.trident.event.Slf4jEventWriter;
import org.lendingclub.trident.provision.ProvisioningApiController;
import org.lendingclub.trident.scheduler.DistributedTaskScheduler;
import org.lendingclub.trident.swarm.SwarmAgentController;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.aws.AWSAccountManager;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.aws.AWSController;
import org.lendingclub.trident.swarm.aws.AWSEventManager;
import org.lendingclub.trident.swarm.aws.AWSMetadataSync;
import org.lendingclub.trident.swarm.aws.AWSSwarmAgentController;
import org.lendingclub.trident.swarm.baremetal.DCController;
import org.lendingclub.trident.swarm.local.LocalSwarmManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.google.common.base.Strings;

import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
@ComponentScan(basePackageClasses = { SwarmClusterManager.class,Main.class, LocalSwarmManager.class, DashboardController.class,
		EnvoyBootstrapController.class, HomeController.class, ProvisioningApiController.class, AWSController.class,
		DCController.class, SwarmAgentController.class })
public class TridentConfig {

	static Logger logger = LoggerFactory.getLogger(TridentConfig.class);
	@Value(value = "${neo4j.url:bolt://localhost:7687}")
	String neo4jUrl;

	@Value(value = "${neo4j.username:}")
	String neo4jUsername;

	@Value(value = "${neo4j.password:}")
	String neo4jPassword;

	@Value(value = "${igniteGridName:}")
	String igniteGridName;

	@Inject
	ApplicationContext applicationContext;

	@Bean
	public NeoRxClient neo4j() {

		executeScriptOnStart(); // dependency

		NeoRxClient.Builder b = new NeoRxClient.Builder();
		if (!Strings.isNullOrEmpty(neo4jUrl)) {
			b = b.withUrl(neo4jUrl);
		}
		if (!Strings.isNullOrEmpty(neo4jUsername)) {
			b = b.withCredentials(neo4jUsername, neo4jPassword);
		}
		neo4jUsername = null;
		neo4jPassword = null;
		NeoRxClient client = b.build();

		if (client.checkConnection()) {
			logger.info("connected to neo4j");
		} else {
			logger.warn("unable to connect to neo4j");
		}

		return client;
	}

	@Bean
	Object bouncyCastleConfig() {
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
		return new Object() {
			// just a dummy object
		};
	}

	@Bean
	public Trident trident() {
		bouncyCastleConfig();
		Trident t = new Trident();
		return t;
	}

	@Bean
	public Projector projector() {
		trident();
		executeScriptOnStart(); // dependency;
		return new Projector.Builder().withNeoRxClient(neo4j()).build();
	}

	@Bean
	public IgniteConfiguration igniteConfiguration() {
		try {
			System.setProperty("ignite.update.notifier.enabled.by.default", "false");
		} catch (Exception IGNORE) {
		}
		IgniteConfiguration cfg = new IgniteConfiguration();
		if (Strings.isNullOrEmpty(igniteGridName)) {
			logger.warn("igniteGridName not set...using auto-generated value");
			igniteGridName = "trident-" + UUID.randomUUID().toString();
		}
		logger.info("setting grid name: {}", igniteGridName);

		cfg = cfg.setIgniteInstanceName(igniteGridName);

		return cfg;
	}
	/*
	 * @Bean public Ignite ignite() { return
	 * Ignition.start(igniteConfiguration()); }
	 */




	@Bean
	public AWSAccountManager awsClientManager() {
		return new AWSAccountManager();
	}

	@Bean
	public ConfigManager tridentConfigManager() {
		return new ConfigManagerImpl();
	}

	@Bean
	public ProxyManager proxyManager() {
		return new ProxyManagerImpl();
	}

	@Bean
	public TridentCryptoKeyStoreManager tridentCryptoKeyStoreManager() {
		return new TridentCryptoKeyStoreManager();
	}

	@Bean
	public TridentCryptoProvider tridentCryptoProvider() {
		return new TridentCryptoProvider();
	}

	@Bean
	public ExecuteScriptOnStart executeScriptOnStart() {

		ExecuteScriptOnStart xs = new ExecuteScriptOnStart();
		xs.execute();
		return xs;
	}

	@Bean
	public TridentEndpoints tridentEndpoints() {
		return new TridentEndpoints();
	}

	@Bean
	public AWSClusterManager awsClusterManager() {
		return new AWSClusterManager();
	}

	@Bean
	public SwarmClusterManager swarmClusterManager() {
		return new SwarmClusterManager();
	}

	@Bean
	public CertificateAuthorityManager tridentCertificateAuthorityManager() {
		return new CertificateAuthorityManagerImpl();
	}

	@Bean
	public DistributedTaskScheduler tridentTaskExecutor() {
		return new DistributedTaskScheduler();
	}

	@Bean
	public TridentSchemaManager tridentSchemaManager() {
		return new TridentSchemaManager(neo4j());
	}



	@Bean
	public AWSMetadataSync tridentAWSMetadataSync() {
		return new AWSMetadataSync();
	}

	@Bean
	public EventSystem eventSystem() {
		return new EventSystem();
	}

	@Bean
	public Slf4jEventWriter slf4jEventWriter() {
		return new Slf4jEventWriter();
	}

	@Bean
	public Neo4jEventLogWriter neo4jEventLogWriter() {
		return new Neo4jEventLogWriter();
	}








	@Bean
	public SwarmServiceDiscoveryDecorator envoySwarmServiceDiscoveryDecorator() {
		return new SwarmServiceDiscoveryDecorator();
	}

	@Bean
	public SwarmClusterDiscoveryDecorator envoySwarmClusterDiscoveryDecorator() {
		return new SwarmClusterDiscoveryDecorator();
	}

	@Bean
	public SwarmListenerDiscoveryDecorator envoySwarmListenerDiscoveryDecorator() {
		return new SwarmListenerDiscoveryDecorator();
	}

	@Bean
	public SwarmRouteDiscoveryDecorator envoySwarmRouteDiscoveryDecorator() {
		return new SwarmRouteDiscoveryDecorator();
	}

	@Bean
	public SwarmAgentController swarmAgentController() {
		return new SwarmAgentController();
	}

	@Bean
	public AWSSwarmAgentController AWSSwarmAgentController() {
		return new AWSSwarmAgentController();
	}

}
