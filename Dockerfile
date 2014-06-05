FROM dockerfile/java
MAINTAINER Doug Clinton <doug@wiredthing.com>

RUN useradd -m play

WORKDIR /home/play

USER play

ADD target/universal/hither-0.1.2-SNAPSHOT.zip /home/play/hither.zip
RUN unzip hither
RUN rm hither.zip
RUN mkdir /home/play/localRegistry

EXPOSE 9000

CMD ["/bin/bash", "/home/play/hither-0.1.2-SNAPSHOT/bin/hither", "-mem", "512"]
