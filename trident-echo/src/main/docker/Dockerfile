FROM openjdk:8-jdk-alpine


RUN apk add --no-cache --quiet curl bash jq

COPY trident-echo /trident-echo

EXPOSE 4567

CMD exec java -jar /trident-echo/echo-all.jar





