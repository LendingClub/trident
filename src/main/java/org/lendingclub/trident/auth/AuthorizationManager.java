package org.lendingclub.trident.auth;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.SecurityContext;

import org.lendingclub.trident.auth.AuthorizationContext.VoteType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public final class AuthorizationManager {

	Logger logger = LoggerFactory.getLogger(AuthorizationManager.class);
	
	
	
	List<AuthorizationVoter> voters = Lists.newCopyOnWriteArrayList();
	

	public final AuthorizationResult authorize(String user, Set<String> roles, String action) {
		return authorize(user,roles,action,MissingNode.getInstance());
	}
	
	private final AuthorizationResult authorizeAnonymous(String action, JsonNode objectData) {
		return authorize("anonymousUser", ImmutableSet.of(), action,objectData);
	}
	
	public static void throwExceptionOnAuthorizationFailure(AuthorizationResult result) {
		if (!result.isAuthorized()) {
			throw new AuthorizationException(result);
		}
	}
	public AuthorizationResult authorizeCurrentUser(String action) {
		return authorizeCurrentUser(action,MissingNode.getInstance());
	}
	public AuthorizationResult authorizeCurrentUser(String action, JsonNode objectData) {
		org.springframework.security.core.context.SecurityContext ctx = SecurityContextHolder.getContext();
		if (ctx==null) {
			return authorizeAnonymous(action, objectData);
		}		
		Authentication auth = ctx.getAuthentication();
		if (auth==null) {
			return authorizeAnonymous(action,objectData);
		}
		Set<String> roles = auth.getAuthorities().stream().map(x->x.getAuthority()).collect(Collectors.toSet());
		return authorize(auth.getName(), roles, action,objectData);
	}
	public final AuthorizationResult authorize(String user, Set<String> roles, String action, JsonNode objectData) {
		AuthorizationContext ctx = new AuthorizationContext();
		ctx.user = user;
		ctx.roles = Sets.newHashSet();
		if (roles!=null) {
			ctx.roles.addAll(roles);
		}
		ctx.action = action;
	
		if (objectData!=null) {
			ctx.objectData = objectData;
		}
		else {
			ctx.objectData = MissingNode.getInstance();
		}
		
		for (AuthorizationVoter voter: voters) {
			try {
				voter.vote(ctx);
			}
			catch (RuntimeException e) {
				logger.info("uncaught exception from "+voter,e);
			}
		}
		return ctx.toResult();
	}
	
	
	public void lock() {
		voters = ImmutableList.copyOf(voters);
	}
	public void addVoter(AuthorizationVoter voter) {
		Preconditions.checkNotNull(voter);
		logger.info("adding authorization voter: {}",voter);
		voters.add(voter);
	}
}
