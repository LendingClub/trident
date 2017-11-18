package org.lendingclub.trident.auth;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Spring bean allows other GrantedAuthoritiesMapper instances to be registered
 * with the Trident runtime.
 * @author rschoening
 *
 */
public final class TridentGrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

	Logger logger = LoggerFactory.getLogger(TridentGrantedAuthoritiesMapper.class);
	
	static SimpleGrantedAuthority TRIDENT_ADMIN = new SimpleGrantedAuthority("TRIDENT_ADMIN");
	static GrantedAuthority TRIDENT_USER = new SimpleGrantedAuthority("TRIDENT_USER");
	
	List<GrantedAuthoritiesMapper> mappers = Lists.newCopyOnWriteArrayList();
	public void addGrantedAuthoritiesMapper(GrantedAuthoritiesMapper m) {
		Preconditions.checkNotNull(m,"GrantedAuthoritiesMapper cannot be null");
		mappers.add(m);
	}
	@Override
	public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
		
		Collection<GrantedAuthority> translated = (Collection<GrantedAuthority>) authorities;
		for (GrantedAuthoritiesMapper mapper: mappers) {
			translated = (Collection<GrantedAuthority>) mapper.mapAuthorities(translated);
		}
		
		if (translated.stream().anyMatch(p->p.getAuthority().equals(TRIDENT_ADMIN.getAuthority()))) {	
			translated.add( TRIDENT_USER);
		}
		
		translated = translated.stream().distinct().collect(Collectors.toList());
		logger.info("granted authorities: "+translated);
		return translated;
	}

}
