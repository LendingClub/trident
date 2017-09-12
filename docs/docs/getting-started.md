
Trident requires JDK 8 and Neo4j to function.  If you just want to see Trident in action, but don't want to set up these things, skip ahead to the "Quick Start with Docker" section.  


## Quick Start with Docker

You need to have a recent installation of Docker installed.

If you do not have Neo4j installed or do not want to run Trident from source, you can run
Trident from a Docker image.  This will start an image with Trident and Neo4j.

The `-v /var/run/docker.sock:/var/run/docker.sock` option will give the Trident running in the container access to your local Docker daemon.  This would not be needed in a production scenario, but it is the quickest/easiest way to get started.

```bash
docker run \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -p 7474:7474 \
  -p 7473:7473 \
  -p 7687:7687 \
  -p 8080:8080 \
  lendingclub/trident
```

NOTE: The image may not yet be available on Docker Hub.
  
You can see the trident UI by going to: http://localhost:8080


### Prerequisites

Trident has only two requirements:

* A functional JDK 8 installation
* A running instance of Neo4j

#### Install Neo4j

Neo4j is trivial to download and install.  It can be run natively or as a Docker container.

It can be obtained from [Neo4j's download site](https://neo4j.com/download/).

Once you get neo4j started, make sure that you can use the browser-based web console at [http://localhost:7474](http://localhost:7474)

If you have never used Neo4j before, prepare to be delighted.  Neo4j makes databases fun again.

There are excellent tutorials built into the web console.  You may have so much fun that you forget about Trident!

#### Neo4j Authentication

In order for Trident to communicate with Neo4j, you will need to either turn off Neo4j authentication or 
provide credentials to Trident.

To set Neo4j connection info, add the following to `${TRIDENT_SRC}/config/application.properties`:

```
neo4j.url=bolt://localhost:7687
neo4j.username=<username>
neo4j.password=<password>
```

## Building and Running from Source

### Clone the GitHub Repository 

You should have a directory structure that looks like the following:

```
├── LICENSE
├── README.md
├── build.gradle
├── config
├── docker
│   ├── trident
│   ├── trident-agent
│   ├── trident-echo
│   └── trident-envoy
├── docs
├── gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── src
│   ├── main
│   │   ├── java
│   │   └── resources
│   └── test
│       ├── java
│       └── resources
└── trident-agent
    ├── build.gradle
    ├── gradle
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── settings.gradle
    └── src
        ├── main
        │   ├── java
        │   └── resources
        └── test
            ├── java
            └── resources
```

### Build and Run Trident

```bash
$ ./gradlew run
```

That's it. In a few seconds Trident will be running on port 8080.  You can point your browser to:

[http://localhost:8080](http://localhost:8080)
  
Easy!

### Running Test Suite

```bash
$ ./gradlew check
```

Simple!

## Next Steps
