package org.lendingclub.trident.swarm.aws;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.lendingclub.trident.TridentException;
import org.lendingclub.trident.config.ConfigManager;
import org.lendingclub.trident.util.JsonUtil;
import org.lendingclub.trident.util.ProxyManager;
import org.lendingclub.trident.util.ProxyManager.ProxyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StopWatch;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class AWSAccountManager {

	Cache<String,AmazonWebServiceClient> clientCache = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();
	
	public static abstract class CredentialsSupplier {

		static Logger logger = LoggerFactory.getLogger(CredentialsSupplier.class);
		JsonNode n;

		private Supplier<List<Regions>> regionSupplier = null;

		public CredentialsSupplier(JsonNode n) {
			this.n = n;

			regionSupplier = Suppliers.memoize(new RegionListSupplier());
		}

		public abstract Optional<String> getAccount();

		class RegionListSupplier implements Supplier<List<Regions>> {
			@Override
			public List<Regions> get() {
				return AWSAccountManager.getRegionsFromConfig(n);
			}
		}

		public JsonNode getConfig() {
			return n;
		}

		public Optional<String> getRoleArn() {
			return optionalString("roleArn");
		}

		public Optional<String> getSourceAccountName() {
			return optionalString("sourceAccountName");
		}

		public List<Regions> getRegions() {

			return regionSupplier.get();

		}

		public Optional<String> getSecretKey() {
			return optionalString("secretKey");
		}

		public Optional<String> getAccessKey() {
			return optionalString("accessKey");
		}

		private Optional<String> optionalString(String x) {

			return Optional.ofNullable(Strings.emptyToNull(Strings.nullToEmpty(n.path(x).asText()).trim()));
		}

		public Regions getDefaultRegion() {
			return getRegions().get(0);
		}

		public abstract AWSCredentialsProvider get();

	}

	Map<String, CredentialsSupplier> supplierMap = Maps.newConcurrentMap();

	static Logger logger = LoggerFactory.getLogger(AWSAccountManager.class);

	@Autowired
	ConfigManager configManager;

	@Autowired
	ProxyManager proxyManager;

	Cache<String, String> accountNumberCache = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();

	public AWSAccountManager() {

		registerCredentialsProvider("default", new DefaultAWSCredentialsProviderChain());

	}

	public class CredentialsProviderSupplier extends CredentialsSupplier {

		AWSCredentialsProvider provider;
		String account = null;

		public CredentialsProviderSupplier(JsonNode n, AWSCredentialsProvider p) {
			super(n);
			this.provider = p;
		}

		public AWSCredentialsProvider get() {
			return provider;
		}

		public Optional<String> getAccount() {
			if (account != null) {
				return Optional.of(account);
			}
			String exceptionLogMessage = "problem getting account";
			try {
				AWSSecurityTokenService client = applyProxy(AWSSecurityTokenServiceClientBuilder.standard()
						.withRegion(getDefaultRegion()).withCredentials(get()), getConfig().path("name").asText())
								.build();
				GetCallerIdentityResult result = client.getCallerIdentity(new GetCallerIdentityRequest());
				account = result.getAccount();
			} catch (AWSSecurityTokenServiceException e) {
				if (Strings.nullToEmpty(e.getErrorCode()).equals("ExpiredToken")) {
					logger.warn(exceptionLogMessage+" - "+e.toString());
				}
				else {
					logger.warn(exceptionLogMessage, e);
				}
			} catch (RuntimeException e) {
				logger.warn(exceptionLogMessage, e);
			}
			return Optional.ofNullable(account);
		}

	}

	public class DeclarativeCredentialsSupplier extends CredentialsSupplier {

		String account = null;

		public Optional<String> getAccount() {
			if (account != null) {
				return Optional.of(account);
			}
			try {
				AWSSecurityTokenService client = applyProxy(AWSSecurityTokenServiceClientBuilder.standard()
						.withRegion(getDefaultRegion()).withCredentials(get()), getConfig().path("name").asText())
								.build();
				GetCallerIdentityResult result = client.getCallerIdentity(new GetCallerIdentityRequest());
				account = result.getAccount();
			} 
			catch (AWSSecurityTokenServiceException e) {
				if (Strings.nullToEmpty(e.getErrorCode()).equals("ExpiredToken")) {
					logger.warn("problem - "+e.toString());
				}
				else {
					logger.warn("problem", e);
				}
			}
			catch (RuntimeException e) {
				logger.warn("problem", e);
				
			}
			return Optional.ofNullable(account);
		}

		public DeclarativeCredentialsSupplier(JsonNode n) {
			super(n);
		}

		public AWSCredentialsProvider get() {

			if (!Strings.isNullOrEmpty(getConfig().path("roleArn").asText())) {
				AWSSecurityTokenServiceClient sts = createSTSClient(getSourceAccountName().get());
				return new STSAssumeRoleSessionCredentialsProvider.Builder(getRoleArn().get(), "trident")
						.withStsClient(sts).build();

			} else {
				if (getAccessKey().isPresent()) {
					// we don't (yet) support assume role with static
					// credentials
					return new AWSStaticCredentialsProvider(
							new BasicAWSCredentials(getAccessKey().orElse(""), getSecretKey().orElse("")));
				} else {
					return new DefaultAWSCredentialsProviderChain();
				}
			}

		}

	}

	public AWSCredentialsProvider getCredentialsProvider(String name) {
		Preconditions.checkNotNull(name);
		CredentialsSupplier supplier = supplierMap.get(name);

		if (supplier == null) {
			name = resolveAccountName(name);
			if (name != null) {
				supplier = supplierMap.get(name);
			}
			if (supplier == null) {
				throw new TridentException("AWSCredentialsProvider supplier not found: " + name);
			}

		}
		return supplier.get();

	}

	public void registerCredentialsProvider(String name, AWSCredentialsProvider provider) {
		ObjectNode n = JsonUtil.createObjectNode().put("name", name);
		CredentialsProviderSupplier s = new CredentialsProviderSupplier(n, provider);

		registerCredentialsSupplier(name, s);
	}

	public void registerCredentialsSupplier(String name, CredentialsSupplier supplier) {
		CredentialsSupplier existing = supplierMap.get(name);
		if (existing != null) {

			// do not allow declarative to overwrite a
			// programmatically-registered provider
			if ((!(existing instanceof DeclarativeCredentialsSupplier))
					&& (supplier instanceof DeclarativeCredentialsSupplier)) {
				logger.info(
						"credentials supplier cannot be registered because a programmatically-pregistered supplier has already been registered: {}",
						name);
				return;
			}
		}
		logger.info("adding supplier {}: {}", name, supplier);
		supplierMap.put(name, supplier);
	}

	public <R extends AmazonWebServiceClient> R getClient(String name, Class<? extends AwsClientBuilder> x) {
		return getClient(name,x,(Regions) null);
	}
	public <R extends AmazonWebServiceClient> R getClient(String name, Class<? extends AwsClientBuilder> x, String region) {
		return getClient(name,x,Regions.fromName(region));
	}
	public <R extends AmazonWebServiceClient> R getClient(String name, Class<? extends AwsClientBuilder> x, Regions region) {
		if (region == null) { 
			region = Regions.fromName(getDefaultRegion(name));
		}
		
		String cacheKey = ""+name+"-"+x.getName()+"-"+region.toString();
		AmazonWebServiceClient client = clientCache.getIfPresent(cacheKey);
		if (client!=null) {
			return (R) client;
		}
		AwsClientBuilder builder = newClientBuilder(name,x).withRegion(region);

		client = (AmazonWebServiceClient) builder.build();
		clientCache.put(cacheKey, client);
		return (R) client;
	}
	
	
	/** 
	 * Creates an AWS client instance.  
	 * 
	 * getClient() should be used in preference to this method wherever possible
	 * @param name
	 * @param clazz
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <T extends AwsClientBuilder> T newClientBuilder(String name, Class<T> clazz) {
		Stopwatch sw = Stopwatch.createStarted();
		try {
			Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "account name/number must be set");
			Preconditions.checkArgument(clazz != null, "builder class must be set");

			// We do a bit of reflection to create the correct builder
			Method m = clazz.getMethod("standard");
			AwsClientBuilder b = (AwsClientBuilder) m.invoke(null);

			// resolve an account number or name ==> name
			name = resolveAccountName(name);

			AWSCredentialsProvider cp = getCredentialsProvider(name);
			if (cp != null) {
				b.withCredentials(cp);
			}

			// If proxy config is set, use it.
			if (proxyManager.getDefaultProxyConfig().isPresent()) {
				ProxyConfig cfg = proxyManager.getDefaultProxyConfig().get();

				ClientConfiguration cc = PredefinedClientConfigurations.defaultConfig().withProxyHost(cfg.getHost())
						.withProxyPort(cfg.getPort()).withNonProxyHosts("169.254.169.254");
				b = (AwsSyncClientBuilder) b.withClientConfiguration(cc);
			}

			// We set the region to what we consider to be the "default" which
			// is the first
			// in the list of the configured list of regions. Callers are more
			// than welcome to change the region
			// after they get the builder.
			b = (AwsClientBuilder) b.withRegion(getDefaultRegion(name));
			return (T) b;
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new TridentException(e);
		}
		finally {
			logger.info("create client builder took {} ms",sw.elapsed(TimeUnit.MILLISECONDS));
		}

	}

	protected String getDefaultRegion(String accountName) {
		// getRegionsFromConfig is guaranteed to return a non-empty list
		// We consider the first entry in the list to be the "default" region.
		return getRegionsFromConfig(
				configManager.getConfig("aws", resolveAccountName(accountName)).orElse(JsonUtil.createObjectNode()))
						.get(0).getName();
	}

	public ClientConfiguration getClientConfiguration(String name) {
		name = resolveAccountName(name);
		// We may want to enable per-client proxy in the future, hence the name
		// argument that isn't actually used yet
		Optional<ProxyConfig> proxy = proxyManager.getDefaultProxyConfig();
		if (proxy.isPresent()) {
			logger.info("using proxy for AWS connectivity: {}:{}", proxy.get().getHost(), proxy.get().getPort());
			String nonProxyHosts = proxy.get().getNonProxyHosts();
			if (Strings.isNullOrEmpty(nonProxyHosts)) {
				nonProxyHosts = "169.254.169.254";
			}
			ClientConfiguration cc = PredefinedClientConfigurations.defaultConfig().withProxyHost(proxy.get().getHost())
					.withProxyPort(proxy.get().getPort()).withNonProxyHosts(nonProxyHosts);
			return cc;
		}

		return PredefinedClientConfigurations.defaultConfig();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected <A extends AwsClientBuilder, B> AwsClientBuilder<A, B> applyProxy(AwsClientBuilder<A, B> x, String name) {

		return x.withClientConfiguration(getClientConfiguration(name));

	}

	protected AWSSecurityTokenServiceClient createSTSClient(String name) {

		AWSSecurityTokenServiceClientBuilder builder = AWSSecurityTokenServiceClient.builder();

		AWSSecurityTokenServiceClient client = (AWSSecurityTokenServiceClient) applyProxy(
				builder.withCredentials(getCredentialsProvider(name)).withRegion(getDefaultRegion(name)), name).build();
		return client;
	}

	public Map<String, CredentialsSupplier> getSuppliers() {
		return ImmutableMap.copyOf(supplierMap);
	}

	protected void register(JsonNode n) {

		String name = n.path("name").asText();
		registerCredentialsSupplier(name, new DeclarativeCredentialsSupplier(n));

	}

	@PostConstruct
	public void reload() {
		try {
			configManager.reload();

			Map<String, JsonNode> cfg = configManager.getConfigOfType("aws");
			cfg.values().forEach(it -> {
				register(it);
			});

			if (!supplierMap.containsKey("default")) {
				register(JsonUtil.createObjectNode().put("name", "default"));
			}
		} catch (RuntimeException e) {
			// This can fail if neo4j is unavailable at test time
			logger.warn("", e);
		}

	}

	/**
	 * Convenient method that can take an account name *or* an account number
	 * and return the account name.
	 * 
	 * @param s
	 * @return
	 */
	String resolveAccountName(String s) {
		if (logger.isDebugEnabled()) {
			logger.debug("resolving account name for '" + s + "'");
		}
		if (getSuppliers().containsKey(s)) {
			return s;
		}

		Optional<String> val = getSuppliers().entrySet().stream().filter(p -> {
			Optional<String> accountNumber = p.getValue().getAccount();
			logger.info("{} ==> {}", p.getKey(), accountNumber);
			return accountNumber.isPresent() && accountNumber.get().equals(s);
		}).map(f -> {
			return f.getKey();
		}).findFirst();

		if (val.isPresent()) {
			accountNumberCache.put(s, val.get());
			return val.get();
		} else {
			throw new org.lendingclub.trident.NotFoundException("name or account", s);
		}

	}

	protected static List<Regions> getRegionsFromConfig(JsonNode n) {
		JsonNode regions = n.path("regions");
		final List<String> regionList = Lists.newArrayList();
		if (regions.isTextual()) {
			regionList.addAll(Splitter.on(",").omitEmptyStrings().trimResults().splitToList(regions.asText()));
		} else if (regions.isArray()) {
			regions.forEach(it -> {
				regionList.add(it.asText());
			});
		}
		final List<Regions> rl = Lists.newArrayList();
		regionList.forEach(it -> {
			try {
				Regions r = Regions.fromName(it);
				rl.add(r);
			} catch (RuntimeException e) {
				logger.warn("invalid region={} in config name={}", it, n.path("name").asText());
			}
		});
		if (rl.isEmpty()) {

			// If we are in AWS, this will use the region where this code is
			// executing.
			// If we are not in AWS, default to the AWS default of
			// Regions.DEFAULT_REGION, which
			// is us-west-2...at least for now.
			Regions r = null;
			Region region = Regions.getCurrentRegion();
			if (region == null) {
				r = Regions.DEFAULT_REGION;
			} else {
				r = Regions.fromName(region.getName());
			}
			logger.info("regions not specified on aws config for {} ... defaulting to {}", n.path("name").asText(),
					r.getName());
			rl.add(r);

		}
		return rl;
	}

	public String lookupAccountNumber(String nameOrNumber) {
		String name = resolveAccountName(nameOrNumber);
		return getSuppliers().get(name).getAccount().get();
	}

}
