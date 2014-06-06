FROM dockerfile/java
MAINTAINER Doug Clinton <doug@wiredthing.com>

WORKDIR /opt

ADD target/universal/hither-0.1.1-SNAPSHOT.zip /opt/hither.zip
RUN unzip hither && rm hither.zip
RUN mkdir /opt/hither/localRegistry

VOLUME /opt/hither/localRegistry

EXPOSE 9000

CMD ["/bin/bash", "/opt/hither-0.1.1-SNAPSHOT/bin/hither", "-mem", "512"]