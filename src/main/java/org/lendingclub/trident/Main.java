package org.lendingclub.trident;

import org.lendingclub.trident.config.TridentConfig;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
@ComponentScan(basePackageClasses = {  TridentConfig.class })
public class Main {

	public static void main(String[] args) {
		try {
			SpringApplication.run(Main.class, args);
		} catch (RuntimeException e) {
			LoggerFactory.getLogger(Main.class).warn("spring context initialization failed", e);
			System.exit(99);
		}
	}

}
