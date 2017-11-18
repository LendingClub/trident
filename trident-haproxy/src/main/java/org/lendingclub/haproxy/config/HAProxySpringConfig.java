package org.lendingclub.haproxy.config;

import org.lendingclub.haproxy.MonitorDaemon;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;


@Configuration
@ComponentScan(basePackages = {"org.lendingclub.haproxy"})
public class HAProxySpringConfig {

	@PostConstruct
	public MonitorDaemon monitor() {
		return new MonitorDaemon();
	}

}
