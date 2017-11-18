FROM lyft/envoy-alpine

RUN apk update && apk add python curl bash jq

COPY envoy /envoy

RUN chmod +x /envoy/*.sh /envoy/*.py /envoy/docker-init /envoy/admin-relay

# Envoy admin
EXPOSE  9901

# Envoy HTTP
EXPOSE 5080

# Envoy HTTPS
EXPOSE 5443 

CMD ["/envoy/docker-init"]
