# Base image
FROM alpine:3.21

# Arguments for version, architecture, base URL, and environment
ARG TRAILBLAZER_VERSION
ARG TARGETARCH
ARG BUILDS_URL
ARG ENVIRONMENT

# Set environment variables
ENV TRAILBLAZER_VERSION=${TRAILBLAZER_VERSION}

# Set working directory
WORKDIR /opt/traccar

# Add necessary tools, install Java, and prepare the environment
RUN set -ex; \
    apk add --no-cache --no-progress \
      openjdk17-jre-headless \
      wget \
      unzip \
      shadow; \
    # Create a user and group with UID/GID 1000
    addgroup -g 1000 traccar && \
    adduser -D -u 1000 -G traccar traccar; \
    # Determine the correct architecture-specific zip based on TARGETARCH
    if [ "$TARGETARCH" = "amd64" ]; then \
      ARCH="64"; \
    elif [ "$TARGETARCH" = "arm64" ]; then \
      ARCH="arm"; \
    else \
      echo "Unsupported architecture: $TARGETARCH" && exit 1; \
    fi; \
    # Download and extract the release zip
    wget -qO /tmp/traccar.zip ${BUILDS_URL}/builds/${ENVIRONMENT}/traccar-other-${TRAILBLAZER_VERSION}.zip; \
    unzip -qo /tmp/traccar.zip -d /opt/traccar; \
    rm /tmp/traccar.zip; \
    # Clean up tools
    apk del wget unzip; \
    # Create necessary directories for logs and media
    mkdir -p /opt/traccar/logs /opt/media; \
    touch /opt/traccar/logs/tracker-server.log; \
    # Ensure correct ownership and permissions
    chown -R traccar:traccar /opt/traccar /opt/media; \
    chmod -R 750 /opt/traccar /opt/media

# Switch to the newly created user

# Set entrypoint and default command
ENTRYPOINT ["java", "-Xms1g", "-Xmx1g", "-Djava.net.preferIPv4Stack=true"]
CMD ["-jar", "tracker-server.jar", "conf/traccar.xml"]
