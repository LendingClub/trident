package org.lendingclub.trident.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.TridentIdentifier;
import org.lendingclub.trident.dns.DNSManager;
import org.lendingclub.trident.loadbalancer.LoadBalancerManager.CreateLoadBalancerCommand;
import org.lendingclub.trident.loadbalancer.LoadBalancerManager.LoadBalancerType;
import org.lendingclub.trident.scheduler.DistributedTask;
import org.lendingclub.trident.swarm.aws.task.LoadBalancerSetupTask;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class LoadBalancerSetupManager implements TridentStartupListener {

	@Autowired
	LoadBalancerManager loadBalancerManager;
	
	@Autowired
	DNSManager dnsManager;
	
	@Autowired
	NeoRxClient neo4j;
	
	@Value(value = "${loadbalancer.haproxy.image:}")
	String haproxyImage;
	
	@Value(value = "${loadbalancer.envoy.image:}")
	String envoyImage;
	
	@Value(value = "${LOAD_BALANCER_DEFAULT_PORT:}")
	String defaultPort;
	
	@Value(value = "${DNS_DEFAULT_DOMAIN:}")
	String defaultDomain;
	
	List<BuildLoadBalancerDNSRecordInterceptor> buildLbDnsRecordInterceptors = Lists.newArrayList();
	
	static Class<? extends DistributedTask> LOAD_BALANCER_SETUP_TASK_CLASS = LoadBalancerSetupTask.class;

	Logger logger = LoggerFactory.getLogger(LoadBalancerSetupManager.class);
	
	public void setupLoadBalancers() {
		if (isEnabled()) {
			logger.info("setting up load balancers");
			createLoadBalancers();
			mapLoadBalancersToSwarms();
			mapLoadBalancersToServices();
			registerLoadBalancerDNS();
		} else { 
			logger.info("{} is not enabled; skipping task", LOAD_BALANCER_SETUP_TASK_CLASS.getName());
		}
	}
	
	public void cleanUpLoadBalancers() { 
		if (isEnabled()) { 
			logger.info("cleaning up orphaned load balancers without docker services");
			String cypher = "match (x:TridentLoadBalancer) where not (x)--(:DockerService) detach delete x";
			neo4j.execCypher(cypher);
		}
	}
	
	/**
	 * scans all Docker services and creates a live and dark 
	 * HAProxy and Envoy load balancer for every distinct
	 * region-env-subenv-servicegroup tuple
	 */
	public void createLoadBalancers() { 
		String cypher = "match (x:DockerService) "
				+ "where NOT x.label_tsdServiceGroup=~'.*-d' return distinct "
				+ "x.label_tsdServiceGroup as svcGroup, "
				+ "x.label_tsdEnv as env, "
				+ "x.label_tsdSubEnv as subEnv, "
				+ "x.label_tsdRegion as region;";
		neo4j.execCypher(cypher).toList().blockingGet().forEach(it -> { 
			try {
				TridentIdentifier identifier = new TridentIdentifier()
						.withServiceGroup(it.path("svcGroup").asText())
						.withEnvironment(it.path("env").asText())
						.withSubEnvironment(it.path("subEnv").asText())
						.withRegion(it.path("region").asText());
				if (!Strings.isNullOrEmpty(defaultPort)) { 
					identifier.withPort(new Integer(defaultPort));
				}
				createLoadBalancers(identifier);
			} catch (RuntimeException e) { 
				logger.warn("problem creating load balancers for tuple {}-{}-{}-{}", 
						it.path("svcGroup").asText(), it.path("env").asText(), 
						it.path("subEnv").asText(), it.path("region").asText(), e);
			}
		});
	}
	
	/**
	 * creates live and dark HAProxy load balancers and live and dark Envoy load balancers
	 * for given tuple
	 */
	public void createLoadBalancers(TridentIdentifier identifier) { 
		createHAProxyLoadBalancers(identifier);
		createEnvoyLoadBalancers(identifier);
	}
	
	/**
	 * create live and dark HAProxy load balancers
	 */
	public void createHAProxyLoadBalancers(TridentIdentifier identifier) { 
		createHAProxyLoadBalancer(identifier, haproxyImage, true);
		createHAProxyLoadBalancer(identifier, haproxyImage, false);
	}
	
	/*
	 * create an HAProxy load balancer
	 */
	public void createHAProxyLoadBalancer(TridentIdentifier identifier, String image, boolean live) { 
		createLoadBalancer(identifier, LoadBalancerType.HAPROXY, image, loadBalancerManager.getLoadBalancerSwarmId(identifier), live);
	}
	
	/**
	 * create live and dark Envoy load balancers
	 */
	public void createEnvoyLoadBalancers(TridentIdentifier identifier) { 
		createEnvoyLoadBalancer(identifier, envoyImage, true);
		createEnvoyLoadBalancer(identifier, envoyImage, false);
	}
	
	/**
	 * create an Envoy load balancer
	 */
	public void createEnvoyLoadBalancer(TridentIdentifier identifier, String image, boolean live) { 
		createLoadBalancer(identifier, LoadBalancerType.ENVOY, image, loadBalancerManager.getLoadBalancerSwarmId(identifier), live);
	}
	
	/**
	 * maps TridentLoadBalancers to their
	 * load balancer swarm
	 */
	public void mapLoadBalancersToSwarms() { 
		try { 
			logger.info("mapping TridentLoadBalancers to DockerSwarms");
			String cypher = "match (x:TridentLoadBalancer), (y:DockerSwarm) where x.swarmClusterId=y.swarmClusterId "
					+ "merge (y)-[:CONTAINS]->(x);";
			neo4j.execCypher(cypher);
		} catch (RuntimeException e) { 
			logger.warn("problem creating Neo4j relationships between TridentLoadBalancer and DockerSwarm nodes", e); 
		}
	}
	
	/**
	 * maps TridentLoadBalancers to their 
	 * associated DockerService
	 */
	public void mapLoadBalancersToServices() { 
		try { 
			logger.info("mapping TridentLoadBalancer nodes to DockerService nodes");
			String cypher = "match (x:TridentLoadBalancer), (y:DockerService) where x.swarmServiceId=y.serviceId "
					+ "merge (y)-[:RUNS_AS]->(x);";
			neo4j.execCypher(cypher);
		} catch (RuntimeException e) { 
			logger.warn("problem creating Neo4j relationships between TridentLoadBalancer and DockerService nodes", e);
		}
	}
	
	public void createLoadBalancer(TridentIdentifier identifier, LoadBalancerType loadBalancerType, String image, String swarmId, boolean live) {
		// If load balancer to be created is dark, append "-d" to serviceGroup
		if (!live) {
			identifier.withServiceGroup(identifier.getServiceGroup().get() + "-d");
		}
		if (!loadBalancerManager.loadBalancerExists(loadBalancerType, identifier, swarmId)) {
			logger.info("Creating {} load balancer {}-{}-{}-{} with image={} in trident swarm={}", 
					loadBalancerType.toString(), identifier.getEnvironment().get(), 
					identifier.getSubEnvironment().get(), identifier.getRegion().get(), 
					identifier.getServiceGroup().get(), image, swarmId); 
			try { 
				CreateLoadBalancerCommand c = loadBalancerManager.createLoadBalancerCommand()
						.withImage(image)
						.withLoadBalancerType(loadBalancerType)
						.withSwarmClusterId(swarmId)
						.withTridentIdentifier(identifier)
						.execute();
				logger.info("Created load balancer={} in trident swarm={}", c.getSwarmServiceId().orElse("lb"), swarmId);
			} catch (RuntimeException e) { 
				logger.warn("could not create {} load balancer with image={} in trident swarm={}", loadBalancerType.toString(), image, swarmId, e);
			}
		} else { 
			logger.info("{} load balancer {}-{}-{}-{} with image={} in swarm={} already exists; will not create", 
					loadBalancerType.toString(), identifier.getEnvironment().get(), identifier.getSubEnvironment().get(),
					identifier.getRegion().get(), identifier.getServiceGroup().get(), image, swarmId);
		}
	}
	
	public void registerLoadBalancerDNS() { 
		logger.info("Registering TridentLoadBalancers with DNSManager");
		
		String cypher = "match (x:TridentLoadBalancer)<-[:RUNS_AS]-(y:DockerService)-[:CONTAINS]->(z:DockerTask)<-[:RUNS]-(a:DockerHost) "
				+ "return distinct x.serviceGroup as svcGroup, "
				+ "x.environment as env, x.subEnvironment as subEnv,"
				+ "x.region as region, x.publishedPort as publishedPort, "
				+ "collect(distinct(a.addr)) as ips;";
		neo4j.execCypher(cypher).toList().blockingGet().forEach(it -> { 
			String serviceGroup = it.path("svcGroup").asText();
			String env = it.path("env").asText();
			String subEnv = it.path("subEnv").asText();
			String region = it.path("region").asText();
			int publishedPort = it.path("publishedPort").asInt();
			ArrayNode ips = (ArrayNode)it.path("ips");
			
			TridentIdentifier identifier = new TridentIdentifier()
					.withEnvironment(env)
					.withSubEnvironment(subEnv)
					.withRegion(region)
					.withServiceGroup(serviceGroup)
					.withPort(publishedPort)
					.withDomain(determineDomain(env, subEnv, region, serviceGroup)); 
			
			LoadBalancerDNSRecord.Builder builder = new LoadBalancerDNSRecord.Builder().withTridentIdentifier(identifier);
			buildLbDnsRecordInterceptors.forEach(b -> { 
				logger.info("applying BuildLbDNSRecordInterceptor {}", b.getClass());
				b.accept(builder);
			});

			registerARecord(builder, toListOfStrings(ips));
			registerSRVRecord(builder);
		});
	}
	
	protected void registerARecord(LoadBalancerDNSRecord.Builder builder, List<String> ips) { 
		try { 
			LoadBalancerDNSRecord aRecord = builder.withIpRecords(ips).buildARecord();
			dnsManager.exec(aRecord);
		} catch (RuntimeException e) { 
			logger.warn("problem registering ARecord", e);
		}
	}
	
	protected void registerSRVRecord(LoadBalancerDNSRecord.Builder builder) { 
		try { 
			LoadBalancerDNSRecord srvRecord = builder.buildSRVRecord();
			dnsManager.exec(srvRecord);
		} catch (RuntimeException e) { 
			logger.warn("problem registering SRVRecord", e);
		}
	}

	protected boolean isEnabled() { 
		try { 
			String cypher = "match (x:TridentTaskSchedule {taskClass:{className}}) return x.enabled;";
			return neo4j.execCypher(cypher, "className", LOAD_BALANCER_SETUP_TASK_CLASS.getName()).blockingFirst().asBoolean();
		} catch (NoSuchElementException e) { 
			return false;
		}
	}
	
	@Override
	public void onStart(ApplicationContext context) {	
		setupLoadBalancers();
	}
	
	protected String determineDomain(String env, String subEnv, String region, String serviceGroup) { 
		if (env.contains("prod")) { 
			return defaultDomain;
		} else if (env.contains("inf")) { 
			return String.format("infra.%s", defaultDomain);
		} else { 
			return String.format("nonprod.%s", defaultDomain);
		}
	}
	
	public void addBuildLbDnsRecordInterceptors(BuildLoadBalancerDNSRecordInterceptor interceptor) {
		logger.info("adding BuildLbDNSRecordInterceptor {}", interceptor.getClass());
		buildLbDnsRecordInterceptors.add(interceptor);
	}
	
	private List<String> toListOfStrings(ArrayNode node) { 
		List<String> list = new ArrayList<>();
		for (JsonNode n : node) { 
			list.add(n.asText());
		}
		return list;
	}
}