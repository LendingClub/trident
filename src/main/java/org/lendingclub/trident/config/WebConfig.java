package org.lendingclub.trident.config;

import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.lendingclub.trident.auth.TridentLoginController;
import org.lendingclub.trident.dashboard.DashboardController;
import org.lendingclub.trident.envoy.EnvoyDiscoveryRewriteFilter;
import org.lendingclub.trident.layout.TridentMVCHandlerInterceptor;
import org.lendingclub.trident.mustache.JacksonMustacheSupport;
import org.lendingclub.trident.mustache.TridentMustacheTemplateLoader;
import org.lendingclub.trident.mustache.TridentMustacheViewResolver;
import org.lendingclub.trident.swarm.SwarmClusterController;
import org.lendingclub.trident.swarm.aws.AWSController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mustache.MustacheEnvironmentCollector;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import com.samskivert.mustache.Mustache;

@Configuration
public class WebConfig extends WebMvcConfigurerAdapter {

	@Autowired
	private Environment environment;

	@Autowired
	private TridentMVCHandlerInterceptor mvcInterceptor;
	
	@Override
	public void configureViewResolvers(ViewResolverRegistry registry) {

	}

	@Bean
	public TridentLoginController tridentLoginController() {
		return new TridentLoginController();
	}

	@Bean
	public TridentMustacheViewResolver tridentResolver() {

		TridentMustacheViewResolver r = new TridentMustacheViewResolver();
		r.setCompiler(mustacheCompiler());
		r.setSuffix(".html");
		r.setCache(false);
		r.setOrder(-10);
		r.setTemplateLoader(tridentMustacheTemplateLoader());

		TridentMustacheTemplateLoader tl = tridentMustacheTemplateLoader();
		tl.setTridentMustacheViewResolver(r);

		return r;
	}

	@Bean
	TridentMustacheTemplateLoader tridentMustacheTemplateLoader() {
		return new TridentMustacheTemplateLoader();
	}

	@Bean
	public Mustache.Compiler mustacheCompiler() {

		MustacheEnvironmentCollector collector = new MustacheEnvironmentCollector();
		collector.setEnvironment(this.environment);
		return JacksonMustacheSupport
				.configure(Mustache.compiler().defaultValue("").withLoader(tridentMustacheTemplateLoader()), collector);
	}

	@Bean
	public SwarmClusterController swarmClusterController() {
		return new SwarmClusterController();
	}

	public AWSController awsAccountController() {
		return new AWSController();
	}

	public DashboardController dashboardController() {
		return new DashboardController();
	}

	@Bean
	public FilterRegistrationBean shallowEtagHeaderFilter() {
		FilterRegistrationBean registration = new FilterRegistrationBean();
		EnvoyDiscoveryRewriteFilter filter = new EnvoyDiscoveryRewriteFilter();
		registration.setFilter(filter);
		registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
		registration.addUrlPatterns("/*");
		registration.setOrder(-100);
		return registration;

	}

	
	@Bean
	public TridentMVCHandlerInterceptor tridentMVCHandlerInterceptor() {
		return new TridentMVCHandlerInterceptor();
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		super.addInterceptors(registry);
		registry.addInterceptor(mvcInterceptor);
	}
}
