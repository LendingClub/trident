FROM ubuntu

RUN apt-get update
RUN apt-get install python-pip python-dev build-essential libyaml-dev -y

RUN pip install mkdocs mkdocs-material

ADD init.sh /init.sh

RUN chmod +x /init.sh

EXPOSE  8000 

ENTRYPOINT ["/init.sh"]

CMD ["serve"]
