FROM openjdk:8-jdk-alpine


RUN apk add --no-cache --quiet bash socat bash curl

COPY trident-authz /trident-authz

EXPOSE 4567

CMD exec /trident-authz/plugin-init





