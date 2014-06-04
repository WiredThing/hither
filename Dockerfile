FROM dockerfile/java
MAINTAINER Doug Clinton <doug@wiredthing.com>

RUN useradd -m play

WORKDIR /home/play

USER play

ADD target/universal/tmp/bin/hither /home/play/hither
RUN chmod 500 /home/play/hither
ADD targer/universal/hither-0.1.0-SNAPSHOT.zip /home/play/hither.zip
RUN unzip hither
RUN rm hither.zip

CMD ["/bin/bash", "/home/play/hither"]
