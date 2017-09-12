package org.lendingclub.trident;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.lendingclub.trident.cluster.TridentClusterManager;
import org.lendingclub.trident.config.ConfigManagerImpl;
import org.lendingclub.trident.crypto.CertificateAuthorityManager;
import org.lendingclub.trident.crypto.CryptoService;
import org.lendingclub.trident.crypto.TridentCryptoKeyStoreManager;
import org.lendingclub.trident.crypto.TridentCryptoProvider;
import org.lendingclub.trident.dashboard.DashboardController;
import org.lendingclub.trident.envoy.EnvoyBootstrapController;
import org.lendingclub.trident.envoy.EnvoyClusterDiscoveryController;
import org.lendingclub.trident.envoy.EnvoyListenerDiscoveryController;
import org.lendingclub.trident.envoy.EnvoyManager;
import org.lendingclub.trident.envoy.EnvoyRouteDiscoveryController;
import org.lendingclub.trident.envoy.EnvoyServiceDiscoveryController;
import org.lendingclub.trident.event.EventSystem;
import org.lendingclub.trident.event.Neo4jEventLogWriter;
import org.lendingclub.trident.event.Slf4jEventWriter;
import org.lendingclub.trident.mustache.TridentMustacheTemplateLoader;
import org.lendingclub.trident.mustache.TridentMustacheViewResolver;
import org.lendingclub.trident.provision.ProvisioningApiController;
import org.lendingclub.trident.provision.ProvisioningManager;
import org.lendingclub.trident.scheduler.DistributedTaskScheduler;
import org.lendingclub.trident.scheduler.TridentSchedulerHistory;
import org.lendingclub.trident.swarm.SwarmAgentController;
import org.lendingclub.trident.swarm.SwarmClusterController;
import org.lendingclub.trident.swarm.SwarmClusterManager;
import org.lendingclub.trident.swarm.SwarmDiscoverySearch;
import org.lendingclub.trident.swarm.SwarmEventManager;
import org.lendingclub.trident.swarm.aws.AWSAccountManager;
import org.lendingclub.trident.swarm.aws.AWSClusterManager;
import org.lendingclub.trident.swarm.aws.AWSController;
import org.lendingclub.trident.swarm.aws.AWSEventManager;
import org.lendingclub.trident.swarm.aws.AWSMetadataSync;
import org.lendingclub.trident.swarm.aws.AWSSwarmAgentController;
import org.lendingclub.trident.swarm.baremetal.DCController;
import org.lendingclub.trident.swarm.local.LocalSwarmManager;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;

public class BeanIntegrationTest extends TridentIntegrationTest {

	@Autowired
	org.springframework.context.ApplicationContext ctx;

	public void assertBean(Class c) {
		ctx.getBean(c);
	}

	public void assertBeanByClass(String clazz) {
		try {
			org.junit.Assert.assertTrue("bean can be resolved: " + clazz, ctx.getBean(Class.forName(clazz)) != null);
		} catch (ClassNotFoundException e) {
			org.junit.Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testIt() {
		assertBean(AWSSwarmAgentController.class);
		assertBean(DashboardController.class);
		assertBean(ProvisioningApiController.class);
		assertBean(ProvisioningManager.class);
		assertBean(CryptoService.class);
		assertBean(HomeController.class);
		assertBean(CryptoService.class);
		assertBean(DashboardController.class);
		assertBean(EnvoyBootstrapController.class);
		assertBean(EnvoyClusterDiscoveryController.class);
		assertBean(EnvoyListenerDiscoveryController.class);
		assertBean(EnvoyRouteDiscoveryController.class);
		assertBean(EnvoyServiceDiscoveryController.class);
		assertBean(SwarmEventManager.class);
		assertBean(ProvisioningApiController.class);
		assertBean(ProvisioningManager.class);
		assertBean(TridentSchedulerHistory.class);
		assertBean(SwarmAgentController.class);
		assertBean(SwarmClusterController.class);
		assertBean(AWSController.class);
		assertBean(AWSSwarmAgentController.class);
		assertBean(DCController.class);
		assertBean(LocalSwarmManager.class);
		assertBean(Trident.class);
		assertBean(AWSAccountManager.class);
		assertBean(ConfigManagerImpl.class);
		assertBean(ProxyManagerImpl.class);
		assertBean(TridentCryptoKeyStoreManager.class);
		assertBean(TridentCryptoProvider.class);
		assertBean(ExecuteScriptOnStart.class);
		assertBean(TridentEndpoints.class);
		assertBean(AWSClusterManager.class);
		assertBean(SwarmClusterManager.class);
		assertBean(CertificateAuthorityManager.class);
		assertBean(DistributedTaskScheduler.class);
		assertBean(TridentSchemaManager.class);
		assertBean(TridentClusterManager.class);
		assertBean(AWSMetadataSync.class);
		assertBean(EventSystem.class);
		assertBean(Slf4jEventWriter.class);
		assertBean(Neo4jEventLogWriter.class);
		assertBean(AWSEventManager.class);
		assertBean(EnvoyManager.class);

		assertBean(TridentMustacheViewResolver.class);
		assertBean(TridentMustacheTemplateLoader.class);

		assertBeanNotFound(SwarmDiscoverySearch.class);

	}

	public void assertBeanNotFound(Class clazz) {
		try {
			Object bean = ctx.getBean(clazz);
			if (bean!=null) {
				Assertions.fail("must not be registered as a bean: "+clazz);
		
			}
			
		}
		catch (NoSuchBeanDefinitionException e) {
			//ignore
		}
		
	}
}
