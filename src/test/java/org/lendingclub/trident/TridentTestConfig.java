package org.lendingclub.trident;

import org.lendingclub.trident.config.TridentConfig;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackageClasses={TridentConfig.class})
public class TridentTestConfig {

}
