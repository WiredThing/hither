FROM dockerfile/java
MAINTAINER Doug Clinton <doug@wiredthing.com>

VOLUME ["/tmp/work"]

WORKDIR /tmp/work

ADD target/universal/hither-0.1.2.zip /tmp/work/hither.zip
RUN unzip hither
RUN rm hither.zip
RUN mv /tmp/work/hither-0.1.2 /opt/hither

VOLUME ["/opt/hither/localRegistry"]

EXPOSE 9000

WORKDIR /root

CMD ["/bin/bash", "/opt/hither/bin/hither", "-mem", "512"]