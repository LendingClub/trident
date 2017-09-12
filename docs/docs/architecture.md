

# Service Discovery

One of Trident's primary functions is to serve as the service discovery system for Envoy.  Envoy is configured to call out to Trident to retrieve configuration for `Listeners`, `Routes`, `Clusters`, and `Services`.

## Docker Labels

Trident uses Docker labels on services to decide which services to add to an Envoy cluster.


| Label Name | Required | Single/Multi|Description | Example Value |
|------------|----------|-------------|---------------|----|
|tsdAppId| Required |Single Value |Distinct name of the app |  payment-api |
|tsdPort | Required |Single Value| Listening port of the app inside the container | 8080 |
|tsdPath | Required |Comma Separated| Path stems for path based routing | /api/foo, api/bar, etc.|
|tsdRegion| Optional|Comma Separated| Regions into which this service should be published | us-west-2, datacenter-1|
|tsdEnv| Optional | Comma Separated | Environments into which this service should be published | dev, qa |
|tsdSubEnv|Optional| Comma Separated | Sub-Environments into which this service should be published | default, feature-1|
|tsdServiceGroup|Optional| Comma Separated | Set of tags that allows Trident to group services | services, ui, etc. |



## Selection

Trident searches the label information that has been ingested by Mercator into Neo4j.  It then applies predicate logic to filter only the services that match the filter predicate.

If Envoy is launched with the following attributes:

| Name | Value |
|------|-------|
|region|us-west-2|
|environment| qa |

Then Trident is going to look for all services that match `tridentRegion=us-west-2` and `tridentEnvironment=qa`.

NOTE: Absence of a label on a service is functionally identical to a wildcard match.  Example: if there is no tridentEnvironment on a given service, it will be included in all Envoy configurations.





