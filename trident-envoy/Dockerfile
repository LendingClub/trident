FROM lyft/envoy-alpine

RUN apk update && apk add python curl bash jq

COPY envoy /envoy

RUN chmod +x /envoy/*.sh /envoy/*.py /envoy/docker-init


CMD ["/envoy/docker-init"]
