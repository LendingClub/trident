package org.lendingclub.trident.auth;

public interface AuthorizationVoter {

	public void vote(AuthorizationContext ctx);
}
