/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lendingclub.trident.auth;


import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.lendingclub.neorx.NeoRxClient;
import org.lendingclub.trident.util.TridentStartupListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.lambdaworks.crypto.SCryptUtil;

import io.reactivex.Observable;
import io.reactivex.functions.Function;

@Component
public class UserManager implements TridentStartupListener {

	Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	NeoRxClient neo4j;

	public class UserImpl implements User {

		String username;
		Set<String> roles = ImmutableSet.of();
		@Override
		public String getUsername() {
			return username;
		}

		@Override
		public Set<String> getRoles() {
			return roles;
		}
		
	}
	public Optional<User> getInternalUser(final String id) {

		String q = "match (u:TridentUser) where u.username={username} return u.username, 'dummy' as dummy";

		JsonNode n = neo4j.execCypher(q, "username", id.toLowerCase())
				.blockingFirst(null);
		if (n != null) {

			UserImpl u = new UserImpl();
			u.username = n.get("u.username").asText();

			u.roles = ImmutableSet.copyOf(findRolesForUser(id.toLowerCase()));

			return Optional.of(u);
		}

		return Optional.absent();
	}

	public boolean authenticate(String username, String password) {
		try {
			String q = "match (u:TridentUser) where u.username={username} return u.scryptHash";
			ObjectNode n = new ObjectMapper().createObjectNode();
			n.put("username", username);
			JsonNode userNode = neo4j.execCypher(q, "username", username)
					.blockingFirst(null);
			if (userNode != null) {

				String hashValue = Strings.emptyToNull(userNode.asText());
				if (hashValue == null) {
					return false;
				}
				try {
					return SCryptUtil.check(password,
							Strings.nullToEmpty(hashValue));
				} catch (IllegalArgumentException e) {
					// if the hash is invalid, we'll get an
					// IllegalArgumentException
					// This could happen if the hashed password was set to
					// something to prevent login
					// no need to log a whole stack trace for this
					logger.info(e.toString());
					return false;
				}

			} else {
				return false;
			}
		}

		catch (Exception e) {
			logger.warn("auth error", e);
			return false;
		}

	}

	public void setPassword(String username, String password) {

		String hash = SCryptUtil.scrypt(password, 4096, 8, 1);

		String c = "match (u:TridentUser) where u.username={username} set u.scryptHash={hash}";

		neo4j.execCypher(c, "username", username, "hash", hash);

	}

	public void setRoles(String username, List<String> roles) {

		for (String role : roles) {
			addRoleToUser(username, role);
		}

	}

	public User createUser(String username, List<String> roles) {

		if (getInternalUser(username).isPresent()) {
			throw new IllegalArgumentException("user already exists: "
					+ username);
		}
		username = username.trim().toLowerCase();

		String cypher = "create (u:TridentUser {username:{username}}) return u";
		neo4j.execCypher(cypher, "username", username);

		setRoles(username, roles);
		UserImpl u = new UserImpl();
		u.username = username;
		u.roles = ImmutableSet.of();

		return u;

	}


	public void initializeGraphDatabase() {
		

	}

	public Collection<String> findRolesForUser(String username) {

		HashSet<String> roles = new HashSet<>();

		// First grab all the users with roles directly attached to them
		neo4j.execCypher(
				"match (u:TridentUser{username:{username}})-[:HAS_ROLE]-(r:TridentRole) return distinct r.name as role_name",
				"username", username)
				.flatMap(jsonNodeToString())
				.forEach(role -> roles.add(role));

		// Now grab roles that are indirectly attached via groups
		neo4j.execCypher(
				"match (u:TridentUser {username:{username}})-[:HAS_MEMBER]-(g:TridentGroup)-[]-(r:TridentRole) return r.name as role_name",
				"username", username)
				.flatMap(jsonNodeToString())
				.forEach(role -> roles.add(role));
		
		// Add the groups themselves as well
		neo4j.execCypher(
				"match (u:TridentUser {username:{username}})-[:HAS_MEMBER]-(g:TridentGroup) return g.name as group_name",
				"username", username)
				.flatMap(jsonNodeToString())
				.forEach(role -> roles.add(normalizeUpperCase("GROUP_"+role)));
		// Note that this will NOT include roles for externally managed groups
		return roles;

	}
	private static String normalizeUpperCase(String s) {
		return s.replaceAll("[^A-Za-z0-9]", "_").trim().toUpperCase();
	}
	public Collection<String> findRolesForGroup(String group) {

		return neo4j
				.execCypher(
						"match (g:TridentGroup{name:{name}})-[:HAS_ROLE]-(r:TridentRole) return distinct r.name as role_name",
						"name", group)
				.flatMap(UserManager.jsonNodeToString()).toList().blockingGet();

	}

	public void seedRoles() {
		addRole(TridentRoles.TRIDENT_ADMIN.toString(),
				"Trident Administrator");
		addRole(TridentRoles.TRIDENT_USER.toString(), "Trident User");
		
		
		addGroup("TRIDENT_ADMIN","Trident Admin");
		
		addRoleToGroup("TRIDENT_ADMIN", TridentRoles.TRIDENT_ADMIN);
		addRoleToGroup("TRIDENT_ADMIN", TridentRoles.TRIDENT_USER);
		
		
		
		
	}

	public void migrateRolesForUser(String username) {
		JsonNode n = neo4j
				.execCypher("match (u:TridentUser {username: {username}}) return u",
						"username", username).blockingFirst();

		for (JsonNode s : Lists.newArrayList(n.path("roles").iterator())) {
			String roleName = s.asText();
			logger.info("adding role={} to user={}", roleName, username);
			addRoleToUser(username, roleName);

		}

	}

	public void addGroup(String name, String description) {
		String cypher = "merge (g:TridentGroup {name:{name}}) ON CREATE SET g.description={description} return g";
		neo4j.execCypher(cypher, "name", name, "description", description);
	}

	public void addRole(String name, String description) {
		String cypher = "merge (r:TridentRole {name:{name}}) ON CREATE SET r.description={description} return r";
		neo4j.execCypher(cypher, "name", name, "description", description);
	}

	public void addRoleToUser(String user, String role) {

		String cypher = "match (u:TridentUser {username:{username}}),(r:TridentRole {name:{role}}) MERGE (u)-[x:HAS_ROLE]-(r) return u,r";

		neo4j.execCypher(cypher, "username", user, "role", role);
	}

	public void addRoleToGroup(String group, String role) {
		String cypher = "match (g:TridentGroup {name:{name}}),(r:TridentRole {name:{role}}) MERGE (g)-[x:HAS_ROLE]-(r) return g,r";

		neo4j.execCypher(cypher, "name", group, "role", role);
	}
	public void addUserToGroup(String group, String username) {
		String cypher = "match (g:TridentGroup {name:{name}}),(u:TridentUser {username:{username}}) MERGE (g)-[x:HAS_MEMBER]-(u) return x";
		neo4j.execCypher(cypher, "username",username,"name",group);
	}
	
	public static Function<JsonNode, Observable<String>> jsonNodeToString() {
		return new Function<JsonNode, Observable<String>>() {

			@Override
			public Observable<String> apply(JsonNode t1) {
				if (t1==null) {
					return Observable.just(null);
				}
				else if (t1 instanceof NullNode) {
					return Observable.just(null);
				}
				else {
					return Observable.just(t1.asText());
				}
			}
			
		};
		
	}

	@Override
	public void onStart(ApplicationContext context) {
		
		try {

			String cipher = "CREATE CONSTRAINT ON (u:TridentUser) ASSERT u.username IS UNIQUE";
			neo4j.execCypher(cipher);

		} catch (Exception e) {
			logger.warn(e.toString());
		}

		try {

			String cipher = "CREATE CONSTRAINT ON (r:TridentRole) ASSERT r.name IS UNIQUE";
			neo4j.execCypher(cipher);

		} catch (Exception e) {
			logger.warn(e.toString());
		}

		if (neo4j.checkConnection()) {
			seedRoles();
			Optional<User> admin = getInternalUser("admin");
			if (admin.isPresent()) {
				logger.debug("admin user already exists");
			} else {
				logger.info("adding admin user");
				List<String> roleList = Lists.newArrayList();

				createUser("admin", roleList);
				
				addUserToGroup("TRIDENT_ADMIN","admin");
				setPassword("admin", "admin");

			}

			migrateRolesForUser("admin");
		}
		
		
	}
}
