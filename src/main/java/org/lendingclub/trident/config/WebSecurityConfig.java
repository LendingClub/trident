package org.lendingclub.trident.config;

import java.util.List;

import org.lendingclub.trident.auth.InternalAuthenticationProvider;
import org.lendingclub.trident.auth.TridentGrantedAuthoritiesMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
@Order(1500)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Value(value = "${trident.auth.method:form}")
	String authenticationMethod;

	Logger logger = LoggerFactory.getLogger(WebSecurityConfig.class);

	public static final String AUTH_METHOD_NONE="none";
	public static final String AUTH_METHOD_FORM="form";
	public static List<String> getUnauthenticatedPathPatterns() {
		return ImmutableList.of("/favicon.ico","/login", "/login/", "/cli/**", "/api/**", "/js/**", "/webjars/**", "/css/**", "/images/**");
		
	}
	
	public static void configureFormLogin(HttpSecurity http) throws Exception {
		http.csrf().disable()

		.authorizeRequests()
		.antMatchers(getUnauthenticatedPathPatterns().toArray(new String[0]))
		.permitAll().antMatchers("/error").permitAll().antMatchers("/saml/**").permitAll().anyRequest()
		.authenticated().and().formLogin().loginPage("/login").defaultSuccessUrl("/home");
	}
	@Override
	protected void configure(HttpSecurity http) throws Exception {

	
		// Spring security sets a bunch of no-cache headers which prevents the browser from caching JS loaded via WebJars
		// This makes page loading insanely slow.  The following disables cache-control headers.
		// https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#headers
		http
		.headers().cacheControl()
			.disable();
		
	
		
		if (Strings.isNullOrEmpty(authenticationMethod) || authenticationMethod.trim().toLowerCase().equals(AUTH_METHOD_NONE)) {
			http.csrf().disable();
			http.authorizeRequests().antMatchers("/**").permitAll();
			return;
		}
		
		else if (authenticationMethod.trim().toLowerCase().contains(AUTH_METHOD_FORM)) {
			configureFormLogin(http);
			return;
		}

		

	}

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
		
		auth.authenticationProvider(tridentInternalAuthenticationProvider());
	}

	@Bean
	public InternalAuthenticationProvider tridentInternalAuthenticationProvider() {
		return new InternalAuthenticationProvider();
	}
	
	@Bean
	public TridentGrantedAuthoritiesMapper tridentGrantedAuthoritiesMapper() {
		return new TridentGrantedAuthoritiesMapper();
	}
}
