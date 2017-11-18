package org.lendingclub.trident.auth;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class AuthorizationContext {
	enum VoteType {
		PERMIT,
		DENY,
		ABSTAIN
	}
	class Vote {
		VoteType voteType;
		String message;
	}
	
	String user;
	Set<String> roles = Sets.newHashSet();
	String action;
	JsonNode objectData;
	
	private List<Vote> votes = Lists.newLinkedList();
	
	private void vote(VoteType type, String message) {
		Vote vote = new Vote();
		vote.voteType = type;
		vote.message = Strings.nullToEmpty(message);
		votes.add(vote);
	}
	public void abstain() {
		vote(VoteType.ABSTAIN,"");
	}
	public void permit(String message) {
		vote(VoteType.PERMIT,message);
	}
	public void deny(String message) {
		vote(VoteType.DENY,message);
	}
	
	AuthorizationResult toResult() {
		int permitCount=0;
		int denyCount=0;
		int abstainCount=0;
		
		String denyMessage = null;
		String allowMessage = null;
		for (Vote vote: votes) {
			if (vote.voteType==VoteType.ABSTAIN) {
				abstainCount++;
			}
			else if (vote.voteType==VoteType.PERMIT) {
				permitCount++;
				allowMessage = vote.message;
			}
			else if (vote.voteType==VoteType.DENY) {
				denyCount++;
				denyMessage=vote.message;
			}
		}

		final boolean authorized = permitCount>=denyCount;
		final String finalDenyMessage = denyMessage;
		final String finalAllowMessage = allowMessage;
		AuthorizationResult result = new AuthorizationResult() {
			
			@Override
			public boolean isAuthorized() {
			
				return authorized;
			}
			
			@Override
			public String getMessage() {
	
				if (authorized) {
					return Strings.nullToEmpty(finalAllowMessage);
				}
				else {
					return Strings.nullToEmpty(finalDenyMessage);
				}
			}
		};
		return result;
	}
	
	public Optional<String> getAction() {
		return Optional.ofNullable(action);
	}
	public Optional<String> getSubject() {
		return Optional.ofNullable(user);
	}
	public Set<String> getRoles() {
		return roles;
	}
	public JsonNode getObjectData() {
		return objectData;
	}
}
