FROM alpine:3.21

ARG TRACCAR_VERSION=6.6

ENV TRAILBLAZER_VERSION=${TRAILBLAZER_VERSION}

WORKDIR /opt/traccar

RUN set -ex; \
    apk add --no-cache --no-progress \
      openjdk17-jre-headless \
      wget \
      unzip; \
    # Download and extract Traccar
    wget -qO /tmp/traccar.zip https://github.com/traccar/traccar/releases/download/vTRAILBLAZER_VERSION/traccar-other-TRAILBLAZER_VERSION.zip; \
    unzip -qo /tmp/traccar.zip -d /opt/traccar; \
    rm /tmp/traccar.zip; \
    apk del wget unzip

# Set entrypoint and default command
ENTRYPOINT ["java", "-Xms1g", "-Xmx1g", "-Djava.net.preferIPv4Stack=true"]
CMD ["-jar", "tracker-server.jar", "conf/traccar.xml"]