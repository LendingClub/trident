FROM haproxy:1.7.9-alpine

RUN apk --update add openjdk8-jre
RUN apk --update add bash

COPY trident-haproxy /trident-haproxy
COPY zero-downtime-restart.sh /trident-haproxy/


EXPOSE 8080
EXPOSE 8003

CMD ["java", "-jar", "/trident-haproxy/haproxy-1.0.jar"]





