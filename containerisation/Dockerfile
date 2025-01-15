FROM alpine:3.21

# Arguments for version, architecture, base URL, and environment
ARG TRAILBLAZER_VERSION
ARG TARGETARCH
ARG BUILDS_URL
ARG ENVIRONMENT

# Set environment variables
ENV TRAILBLAZER_VERSION=${TRAILBLAZER_VERSION}

WORKDIR /opt/traccar

RUN set -ex; \
    apk add --no-cache --no-progress \
      openjdk17-jre-headless \
      wget \
      unzip; \
    # Determine the correct architecture-specific zip based on TARGETARCH
    if [ "$TARGETARCH" = "amd64" ]; then \
      ARCH="64"; \
    elif [ "$TARGETARCH" = "arm64" ]; then \
      ARCH="arm"; \
    else \
      echo "Unsupported architecture: $TARGETARCH" && exit 1; \
    fi; \
    # Download and extract the release zip
    wget -qO /tmp/traccar.zip ${BUILDS_URL}/builds/${ENVIRONMENT}/traccar-linux-${ARCH}-${TRAILBLAZER_VERSION}.zip; \
    unzip -qo /tmp/traccar.zip -d /opt/traccar; \
    rm /tmp/traccar.zip; \
    apk del wget unzip

# Set entrypoint and default command
ENTRYPOINT ["java", "-Xms1g", "-Xmx1g", "-Djava.net.preferIPv4Stack=true"]
CMD ["-jar", "tracker-server.jar", "conf/traccar.xml"]