package org.lendingclub.trident;

import org.lendingclub.trident.config.TridentConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
@Configuration
@ComponentScan(basePackageClasses={DockerClientManager.class,TridentConfig.class})
public class Main {

	public static void main(String[] args) {
		SpringApplication.run(Main.class, args);
	}
	
	
}
