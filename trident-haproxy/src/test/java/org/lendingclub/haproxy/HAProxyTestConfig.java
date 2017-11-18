package org.lendingclub.haproxy;

import org.lendingclub.haproxy.config.HAProxySpringConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses = { HAProxySpringConfig.class})
public class HAProxyTestConfig {

}