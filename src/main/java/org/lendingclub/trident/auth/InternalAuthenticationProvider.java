package org.lendingclub.trident.auth;
import java.util.Collection;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class InternalAuthenticationProvider implements AuthenticationProvider {

	org.slf4j.Logger logger = LoggerFactory
			.getLogger(InternalAuthenticationProvider.class);

	@Autowired(required=true)
	UserManager userManager;

	@Autowired(required=true)
	TridentGrantedAuthoritiesMapper grantedAuthoritiesMapper;

	@Override
	public Authentication authenticate(final Authentication authentication)
			throws AuthenticationException {

		Optional<User> u = Optional.absent();
		u = userManager.getInternalUser(authentication.getPrincipal().toString());

		if (!u.isPresent()) {
			throw new UsernameNotFoundException("user not found: "+authentication.getPrincipal().toString());
		}
		boolean b = userManager.authenticate(authentication.getPrincipal()
				.toString(), authentication.getCredentials().toString());
		if (!b) {
			throw new BadCredentialsException("invalid credentials");
		}

		List<GrantedAuthority> gaList = Lists.newArrayList();
		for (String role: u.get().getRoles()) {

			GrantedAuthority ga = new SimpleGrantedAuthority(role);
			gaList.add(ga);
		}

		Collection<? extends GrantedAuthority> finalGrantedAuthorities = grantedAuthoritiesMapper.mapAuthorities(gaList);
		

		UsernamePasswordAuthenticationToken upt = new UsernamePasswordAuthenticationToken(
				authentication.getPrincipal().toString(), authentication
						.getCredentials().toString(), finalGrantedAuthorities);
		return upt;

	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class
				.isAssignableFrom(authentication);

	}



}