FROM neo4j:latest

RUN apk add --no-cache --quiet curl
#COPY bin /bin
COPY trident /trident

RUN chmod +x /trident/bin/*
EXPOSE 7474 7473 7687 8080

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["/trident/bin/docker-init"]