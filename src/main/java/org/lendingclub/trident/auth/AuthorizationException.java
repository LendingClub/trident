package org.lendingclub.trident.auth;

import java.util.Optional;

import org.lendingclub.trident.TridentException;

public class AuthorizationException extends TridentException {

	AuthorizationResult result = null;
	public AuthorizationException(AuthorizationResult result) {
		super(result!=null ? result.getMessage() : "");
		this.result = result;
	}
	
	public Optional<AuthorizationResult> getAuthorizationResult() {
		return Optional.ofNullable(result);
	}
}
