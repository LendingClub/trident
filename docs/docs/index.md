# Welcome to Trident

Trident is a container orchestration system that makes it easy for developers to manage fleets of micro-services
on top of [Docker Swarm](https://docs.docker.com/engine/swarm/).

Trident provides the following:

* Blue/Green deployment model
* Opinionated conventions for running fleets of microservices
* Extension points that make it easy to extend/customize out-of-box conventions to meet custom Enterprise requirements
* Integrated load balancing capabilities with [Lyft Envoy](https://lyft.github.io/envoy/) and [HAProxy](http://www.haproxy.org)
* Ability to support many environments (dev, test, prod, etc.) from a single management control plane
* Orchestration and maintenence of dozens of Docker Swarm clusters 
* Provisioning of Docker Swarm Clusters on bare-metal and AWS environments
* 100% Open Source Software

With Trident, we want to make it as easy as possibe to go from zero to production-ready clusters of micro-services, without compromising operational integrity, security, reliability, maintainability, etc.


