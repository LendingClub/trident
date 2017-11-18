package org.lendingclub.trident.config;

import java.security.Security;

import javax.inject.Inject;

import org.lendingclub.mercator.core.Projector;
import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.HomeController;
import org.lendingclub.trident.Main;
import org.lendingclub.trident.Trident;
import org.lendingclub.trident.TridentEndpoints;
import org.lendingclub.trident.auth.AuthorizationManager;
import org.lendingclub.trident.auth.UserManager;
import org.lendingclub.trident.chatops.ChatOpsManager;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.crypto.CertificateAuthorityManagerImpl;
import org.lendingclub.trident.crypto.TridentCryptoKeyStoreManager;
import org.lendingclub.trident.crypto.TridentCryptoProvider;
import org.lendingclub.trident.dashboard.DashboardController;
import org.lendingclub.trident.dns.DNSManager;
import org.lendingclub.trident.dns.Route53ChangeExecutor;
import org.lendingclub.trident.envoy.EnvoyDiscoveryController;
import org.lendingclub.trident.event.AWSEventLogWriter;
import org.lendingclub.trident.event.EventRegistrations;
import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.event.Neo4jEventLogWriter;
import org.lendingclub.trident.event.Slf4jEventWriter;
import org.lendingclub.trident.extension.ExtensionRegistry;
import org.lendingclub.trident.git.GitRepoManager;
import org.lendingclub.trident.haproxy.HAProxyCertBundleDiscoveryInterceptor;
import org.lendingclub.trident.haproxy.HAProxyConfigBundleDiscoveryInterceptor;
import org.lendingclub.trident.haproxy.HAProxyFileSystemResourceProvider;
import org.lendingclub.trident.haproxy.HAProxyGitResourceProvider;
import org.lendingclub.trident.haproxy.HAProxyMetaResourceProvider;
import org.lendingclub.trident.haproxy.HAProxyUIController;
import org.lendingclub.trident.haproxy.swarm.SwarmBootstrapConfigInterceptor;
import org.lendingclub.trident.haproxy.swarm.SwarmHostDiscoveryInterceptor;
import org.lendingclub.trident.layout.NavigationManager;
import org.lendingclub.trident.loadbalancer.LoadBalancerManager;
import org.lendingclub.trident.loadbalancer.LoadBalancerSetupManager;
import org.lendingclub.trident.provision.SwarmNodeProvisionApiController;
import org.lendingclub.trident.scheduler.DistributedTaskScheduler;
import org.lendingclub.trident.settings.SettingsController;
import org.lendingclub.trident.swarm.CreateSwarmDataFormatting;
import org.lendingclub.trident.swarm.SwarmAgentController;
import org.lendingclub.trident.swarm.SwarmApiController;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.auth.DockerPluginAuthorizationApiController;
import org.lendingclub.trident.swarm.aws.AWSAccountManager;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.aws.AWSController;
import org.lendingclub.trident.swarm.aws.AWSEventSetup;
import org.lendingclub.trident.swarm.aws.AWSMetadataSync;
import org.lendingclub.trident.swarm.aws.AWSSwarmAgentController;
import org.lendingclub.trident.swarm.aws.SQSGatewayController;
import org.lendingclub.trident.swarm.baremetal.BareMetalController;
import org.lendingclub.trident.swarm.digitalocean.DigitalOceanClusterManager;
import org.lendingclub.trident.swarm.local.LocalSwarmManager;
import org.lendingclub.trident.swarm.platform.AppClusterManager;
import org.lendingclub.trident.swarm.platform.AppClusterManagerImpl;
import org.lendingclub.trident.swarm.platform.Catalog;
import org.lendingclub.trident.util.ExecuteScriptOnStart;
import org.lendingclub.trident.util.ProxyManager;
import org.lendingclub.trident.util.ProxyManagerImpl;
import org.lendingclub.trident.util.TridentSchemaManager;
import org.lendingclub.trident.util.TridentStartupConfigurator;
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
@ComponentScan(basePackageClasses = { HAProxyUIController.class,SwarmClusterManager.class,Main.class, LocalSwarmManager.class, DashboardController.class,
		EnvoyDiscoveryController.class, HomeController.class, SwarmNodeProvisionApiController.class, AWSController.class,
		BareMetalController.class,UserManager.class,SwarmAgentController.class,DockerPluginAuthorizationApiController.class, SettingsController.class})
public class TridentConfig {

	static Logger logger = LoggerFactory.getLogger(TridentConfig.class);
	@Value(value = "${neo4j.url:bolt://localhost:7687}")
	String neo4jUrl;

	@Value(value = "${neo4j.username:}")
	String neo4jUsername;

	@Value(value = "${neo4j.password:}")
	String neo4jPassword;

	@Value(value = "${igniteGridName:trident}")
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
	public ExtensionRegistry tridentExtensionRegistry() {
		return new ExtensionRegistry();
	}
	@Bean
	public TridentEndpoints tridentEndpoints() {
		return new TridentEndpoints();
	}

	@Bean
	public DigitalOceanClusterManager digitalOceanClusterManager() {
		return new DigitalOceanClusterManager();
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

	/*
	 * @Bean public SwarmServiceDiscoveryInterceptor
	 * envoySwarmServiceDiscoveryInterceptor() { return new
	 * SwarmServiceDiscoveryInterceptor(); }
	 * 
	 * @Bean public SwarmClusterDiscoveryInterceptor
	 * envoySwarmClusterDiscoveryInterceptor() { return new
	 * SwarmClusterDiscoveryInterceptor(); }
	 * 
	 * @Bean public SwarmListenerDiscoveryInterceptor
	 * envoySwarmListenerDiscoveryInterceptor() { return new
	 * SwarmListenerDiscoveryInterceptor(); }
	 * 
	 * @Bean public SwarmRouteDiscoveryInterceptor
	 * envoySwarmRouteDiscoveryInterceptor() { return new
	 * SwarmRouteDiscoveryInterceptor(); }
	 */

	@Bean
	public SwarmAgentController swarmAgentController() {
		return new SwarmAgentController();
	}

	@Bean
	public AWSSwarmAgentController AWSSwarmAgentController() {
		return new AWSSwarmAgentController();
	}

	// @Bean
	// public HAProxyDiscoveryController haproxyHostInfoDiscoveryController() {
	// return new HAProxyDiscoveryController();
	// }

	@Bean
	public ChatOpsManager chatOpsManager() {
		return new ChatOpsManager();
	}

	@Bean
	public SwarmBootstrapConfigInterceptor swarmBootstrapConfigInterceptor() {
		return new SwarmBootstrapConfigInterceptor();
	}

	@Bean
	public SwarmHostDiscoveryInterceptor swarmHostDiscoveryInterceptor() {
		return new SwarmHostDiscoveryInterceptor();
	}

	@Bean
	public HAProxyConfigBundleDiscoveryInterceptor swarmHAProxyConfigBundleDiscoveryInterceptor() {
		return new HAProxyConfigBundleDiscoveryInterceptor();
	}

	@Bean
	public HAProxyCertBundleDiscoveryInterceptor haProxyCertBundleDiscoveryInterceptor() {
		return new HAProxyCertBundleDiscoveryInterceptor();
	}

	@Bean
	public EventRegistrations eventRegistrations() {
		return new EventRegistrations();
	}

	@Bean
	public TridentStartupConfigurator tridentStartupConfigurator() {
		neo4j(); // establish dependency
		tridentConfigManager(); // establish dependency
		return new TridentStartupConfigurator();
	}

	@Bean
	public AppClusterManager deploymentClient() {
		return new AppClusterManagerImpl();
	}

	@Bean
	public GitRepoManager gitRepoManaager() {
		return new GitRepoManager();
	}

	@Bean
	public AWSEventLogWriter awsEventLogWriter() {
		return new AWSEventLogWriter();
	}

	@Bean
	public AWSEventSetup awsEventSetup() {
		return new AWSEventSetup();
	}

	@Bean
	public HAProxyFileSystemResourceProvider haProxyFileSystemResourceProvider() {
		return new HAProxyFileSystemResourceProvider();
	}
	
	@Bean
	public HAProxyGitResourceProvider haProxyGitResourceProvider() {
		return new HAProxyGitResourceProvider();
	}

	@Bean
	public HAProxyMetaResourceProvider haProxyMetaResourceProvider() {
		return new HAProxyMetaResourceProvider();
	}

	@Bean
	public DNSManager dnsManager() {
		DNSManager m = new DNSManager();
		m.register(route53ChangeExecutor());
		return m;
	}

	@Bean
	public Route53ChangeExecutor route53ChangeExecutor() {
		return new Route53ChangeExecutor();
	}

	@Bean
	public Catalog catalog() {
		return new Catalog();
	}

	@Bean
	public AuthorizationManager authorizationManager() {
		return new AuthorizationManager();
	}

	@Bean
	public LoadBalancerManager loadBalancerManager() {
		return new LoadBalancerManager();
	}

	@Bean
	public NavigationManager navigationManager() {
		return new NavigationManager();
	}

	@Bean
	public CreateSwarmDataFormatting createSwarmDataFormatting() {
		return new CreateSwarmDataFormatting();
	}
	
	@Bean
	public LoadBalancerSetupManager loadBalancerSetupManager() { 
		return new LoadBalancerSetupManager();
	}

	@Bean
	public SwarmApiController swarmApiController() {
		return new SwarmApiController();
	}
	

}
