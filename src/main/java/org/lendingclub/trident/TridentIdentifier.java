package org.lendingclub.trident;

import java.util.Optional;

import com.google.common.base.Strings;

public class TridentIdentifier {

	String serviceGroup;
	String environment;
	String subEnvironment;
	String region;
	String appId;
	String domain;
	String serviceNode;
	Integer port;

	public <T extends TridentIdentifier> T withServiceGroup(String serviceGroup) { 
		this.serviceGroup = serviceGroup;
		return (T) this;
	}
	
	public <T extends TridentIdentifier> T withEnvironment(String environment) { 
		this.environment = environment;
		return (T) this;
	}
	
	public <T extends TridentIdentifier> T withSubEnvironment(String subEnvironment) { 
		this.subEnvironment = subEnvironment;
		return (T) this;
	}
	
	public <T extends TridentIdentifier> T withRegion(String region) { 
		this.region = region;
		return (T) this;
	}
	
	public <T extends TridentIdentifier> T withAppId(String appId) { 
		this.appId = appId;
		return (T) this;
	}
	
	public <T extends TridentIdentifier> T withDomain(String domain) { 
		this.domain = domain;
		return (T) this;
	}
	
	public <T extends TridentIdentifier> T withServiceNode(String serviceNode) { 
		this.serviceNode = serviceNode;
		return (T) this;
	}
	
	public <T extends TridentIdentifier> T withPort(Integer port) { 
		this.port = port;
		return (T) this;
	}
	
	public final Optional<String> getServiceGroup() { 
		return Optional.ofNullable(Strings.emptyToNull(serviceGroup));
	}
	
	public final Optional<String> getEnvironment() { 
		return Optional.ofNullable(Strings.emptyToNull(environment));
	}
	
	public final Optional<String> getSubEnvironment() { 
		return Optional.ofNullable(Strings.emptyToNull(subEnvironment));
	}
	
	public final Optional<String> getRegion() { 
		return Optional.ofNullable(Strings.emptyToNull(region));
	}
	
	public final Optional<String> getAppId() { 
		return Optional.ofNullable(Strings.emptyToNull(appId));
	}
	
	public final Optional<String> getDomain() { 
		return Optional.ofNullable(Strings.emptyToNull(domain));
	}
	
	public final Optional<String> getServiceNode() { 
		return Optional.ofNullable(Strings.emptyToNull(serviceNode));
	}
	
	public final Optional<Integer> getPort() { 
		return Optional.ofNullable(port);
	}
}
