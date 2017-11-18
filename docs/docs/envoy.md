# Envoy Service Discovery

Envoy has a simple, yet sophisticated, mechanism for delegating configuration management to external systems.  It can be configured to make REST API requests to
a discovery service that provides configuration at runtime.  The contents that are returned are simply the fragments of the configuration that would otherwise be specified
in the monolithic configuration.

Trident implements the Envoy service discovery endpoints so that the two can work easily together. 

Trident follows the "batteries included" model of Docker to provide a service discovery model that can work out-of-the-box with minimal configuration.  However, using some
standard patterns, that out-of-box-configuration can be "decorated" to meet your specific requirements.

## Minimal Configuration

Trident will emit a basic configuration if called via:

```curl https://trident.example.com/api/trident/envoy/config```

The response will look like:

```json
{
    "listeners": [],
    "lds": {
        "cluster": "trident_service_discovery",
        "refresh_delay_ms": 30000
    },
    "admin": {
        "access_log_path": "/envoy/logs/admin_access.log",
        "address": "tcp://127.0.0.1:9901"
    },
    "cluster_manager": {
        "sds": {
            "cluster": {
                "name": "trident_service_discovery",
                "connect_timeout_ms": 250,
                "type": "strict_dns",
                "lb_type": "round_robin",
                "hosts": [
                    {
                        "url": "tcp://trident.example.com:443"
                    }
                ],
                "ssl_context": {
                    "ca_cert_file": "/envoy/config/ca-certificates.crt"
                }
            },
            "refresh_delay_ms": 30000
        },
        "cds": {
            "cluster": {
                "name": "trident_cluster_discovery",
                "connect_timeout_ms": 250,
                "type": "strict_dns",
                "lb_type": "round_robin",
                "hosts": [
                    {
                        "url": "tcp://trident.example.com:443"
                    }
                ],
                "ssl_context": {
                    "ca_cert_file": "/envoy/config/ca-certificates.crt"
                }
            },
            "refresh_delay_ms": 30000
        },
        "clusters": [
        ]
    }
}
```
The expectation is that service config will be loaded via LDS, RDS, CDS, and SDS APIs.  All of these will be served by Trident.

## Cluster Discovery Service (CDS)

Envoy discovers service clusters using the Envoy-defined [CDS REST API](https://lyft.github.io/envoy/docs/configuration/cluster_manager/cds.html).  Envoy uses three configuration 
options that are passed globally on the command-line:



 Option            | Description 
 ------------------|-------------
--service-zone    | Region/AZ/Datacenter ID 
--service-cluster | Logical Name for Envoy Cluster 
 --service-node    | Instance name for Envoy instance 

Envoy passes the service-cluster and service-node in the path of the API:

```GET /v1/clusters/{service-cluster}/{service-node}```

By convention, we encode extra information in the service-cluster, so that Trident can understand what configuration to serve.  That convention is:

```<service-zone>--<environment>--<subenvironment>--<logical-cluster>```

To make this concrete, let's consider the following example.


 Attribute       | Example Value 
 ----------------|-------------------
 service-zone    | uw2
 environment     | qa
 sub-environment | default
 logical-cluster | payment-microservices
 service-node    | 307b2d11ede6

In this case, we're running in AWS us-west-2 (uw2 for short), in the environment called "qa".  We use this idea of a sub-environment, which we will discuss elsewhere.
The logical cluster that this Envoy cluster will represent is our payment microservices.  The service-node value of 307b2d11ede6 is the container ID of the container 
in which Envoy is running.

The resulting configuration that we pass to Envoy is:

```bash
envoy \
  --service-zone uw2 \
  --service-cluster uw2--qa--default--payment-microservices \
  --service-node 307b2d11ede6 \
  -c envoy.config
```

The key point to understand here is that this is the dimensional metadata that Envoy will send Trident so that it can make an intelligent decision of what configuration to serve. 

Note: It would be nice if Envoy was better able to handle arbitrary dimensions of metadata in its discovery APIs, but this is currently not supported.

Once Envoy is started, it will begin making calls to Trident that are effectively like the following.  In this example, we demonstrate that Trident has discovered two services
for Envoy to proxy:

```bash
$ curl --silent http://trident.example.com/v1/clusters/uw2--qa--default--payment-microservices/307b2d11ede6
```

```json
{
  "clusters": [
    {
      "name": "uw2--qa--default--service1",
      "connect_timeout_ms": 250,
      "type": "logical_dns",
      "lb_type": "round_robin"
    },
    {
      "name": "uw2--qa--default--service2",
      "connect_timeout_ms": 250,
      "type": "logical_dns",
      "lb_type": "round_robin"
    }
  ]
}
```
Within Trident, there is a REST controller [EnvoyClusterDiscoveryController](https://github.com/LendingClub/trident/src/main/java/org/lendingclub/trident/envoy/EnvoyClusterDiscoveryController.java)
which receives
this request.  Trident maintains a list of [EnvoyClusterDiscoveryDecorator](https://github.com/LendingClub/trident/src/main/java/org/lendingclub/trident/envoy/EnvoyClusterDiscoveryDecorator.java) instances which are registered in [EnvoyManager](https://github.com/LendingClub/trident/src/main/java/org/lendingclub/trident/envoy/EnvoyManager.java). 

These decorators are executed in order and allow the resulting response to be built, added-to, modified or removed as requirements dictate.

### Default Service Discovery

Trident's default service discovery mechanism will look at Docker labels on the services/containers in the swarms that it manages.  It is little more than a simple predicate match against known labels.

However, extra Decorators can be added that can alter the default behavior by manipulating the resulting JSON.

Simple, easy, flexible!


