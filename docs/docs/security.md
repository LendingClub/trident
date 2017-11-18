# Authentication

Trident uses Spring Security internally.  

It ships with a built-in form-based login, which authenticates against a user store in Neo4j.  

On first start a default admin user named ```admin``` will be created with a password ```admin```.

## Alternate Authentication

Trident can be modified to support other authentication mechansims.  If you do this, it is recommended that you choose a new value of ```trident.auth.method```, which by default is set to ```form```.

We use the [Spring Security SAML](https://projects.spring.io/spring-security-saml/) to provide SAML
authentication, for example.  By changing ```tridend.auth.method``` from ```form``` to ```saml```, it allows Trident to un-configured the form-based configuration in spring security.

If you want to disable authentication altogether, set ```trident.auth.method``` to ```none```.

Note: We intend provide LDAP/AD support in the near future.


## API Authentication

API access is currently unauthenticated, with the understanding that you will provide your own authentication mechansim for API access.


There are 3 broad categories of APIs:

|Category|Mechanism|Description|
|--------|---------|-----------|
|Trident API | TBD | APIs that perform operations such as create swarm, etc. |
|Envoy/HAProxy API | TBD | APIs that facilitate the configuration of load balancers. |
|Swarm Provisioning| TBD | APIs that facilitate the creation of nodes within the swarm.|

The Swarm Provisioning APIs are hard to authenticate with traditional means.  However, at least in the AWS case, it is possible to validate incoming requests against AWS metadata.  If a manager node is 
requesting to join a swarm, the caller can be validated  against the AWS data itself.

The Envoy/HAProxy APIs are likely best handled wtih some Network-based authentication at a broad level.  Seeding credentials in the containers creates a difficult chicken-and-egg situation. We think that in general Network authentication is sufficient.  In this case, we just want to ensure that the origin of the request is consistent with the usage.

The high-level Trident APIs will need their own authentication mechanism.  We may offer a built-in mutual-TLS and/or Token-based scheme out of the box.


# Authorization

## Role Mapping

If you are using an external authentication authentication mechanism, you may have groups and roles in that system that need to be mapped to Trident.

To do this, you can register a custom [```GrantedAuthoritiesMapper```](https://docs.spring.io/spring-security/site/docs/current/apidocs/index.html?org/springframework/security/core/authority/mapping/GrantedAuthoritiesMapper.html), which is a Spring Security construct.  

You can register as many as you like with the singleton [```TridentGrantedAuthoritiesMapper```](https://github.com/LendingClub/trident/src/main/java/org/lendingclub/trident/auth/TridentGrantedAuthoritiesMapper.java)

This allows you to map groups in your Identity Management system into the roles that Trident understands.

## Coarse Grained Authorization
Trident uses Spring Security for coarse grained authorization.

Trident makes use of the following two roles:

* ```TRIDENT_ADMIN``` - Administrator Role
* ```TRIDENT_USER```  - User Role

## Fine-grained Authorization

There is a custom facility for enforcing fine-grained authorization, where an authorization decision may depend on:

* The roles/groups the user has/belongs-to
* The action being performed.  e.g. create swarm, delete swarm, create application cluster, etc.
* The object on which the application is being prformed.  Some users may be able to create swarms in some environments but not others, etc. 

Custom authorization rules may be created by implementing [```AuthorizationVoter```](https://github.com/LendingClub/trident/macgyver-core/src/main/java/io/macgyver/core/auth/AuthorizationVoter.java).  Custom instances of [```AuthorizationVoter```](https://github.com/LendingClub/trident/macgyver-core/src/main/java/io/macgyver/core/auth/AuthorizationVoter.java) can be registered with 
[```AuthorizationManager```](https://github.com/LendingClub/trident/macgyver-core/src/main/java/io/macgyver/core/auth/AuthorizationManager.java) at startup.

When actions are evaluated for authorization, all registered voters are executed in sequence.  Those that are interested in the given action can decide to ```permit()``` or ```deny()``` the request.

If the number of ```permit``` votes is greater than or equal to the number of ```deny``` votes, the action is allowed.  If there are more ```deny``` votes, the action is disallowed.

If you do not like the behavior where a tie is authorized, just register an AuthorizationVoter that denies all.

As a Trident developer, the easiest way to invoke this mechanism is via the [```AuthorizationUtil```](https://github.com/LendingClub/trident/src/main/java/org/lendingclub/trident/auth/AuthorizationUtil.java) class.  It has convenient 
static methods for testing authorization.


