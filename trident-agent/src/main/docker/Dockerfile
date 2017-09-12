FROM openjdk:8-alpine

RUN apk update && apk add curl bash jq

COPY trident-agent /trident-agent


#RUN chmod +x /envoy/*.sh /envoy/*.py /envoy/docker-init


CMD ["/trident-agent/docker-init"]
