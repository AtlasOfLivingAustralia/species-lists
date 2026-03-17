# Use an official Maven runtime as a parent image
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set the working directory in the container
WORKDIR /app

# Copy the project files into the container
COPY pom.xml .
COPY src ./src

# Build the Maven project and create a WAR file
RUN mvn clean package -DskipTests

# Build phase
FROM ubuntu:noble

ARG DEBIAN_FRONTEND=noninteractive

ENV SERVER_PORT=8080
ENV APP_ARTIFACT=lists-service
ENV JAVA_CACERTS=/etc/ssl/certs/java/cacerts
ENV CUSTOM_TRUSTSTORE=/data/lists-service/config/lists-service-truststore.jks
ENV TRUSTSTORE_PASS=changeme

# -------------------------------
# 1️⃣ Install dependencies
# -------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        ca-certificates-java \
        openjdk-21-jdk \
        openjdk-21-jre \
        gnupg \
        coreutils \
        bsdmainutils \
    && rm -rf /var/lib/apt/lists/*

# -------------------------------
# 2️⃣ Add AWS DocumentDB bundle
# -------------------------------
COPY src/main/docker/certs/aws-global-bundle.pem /tmp/aws-global-bundle.pem

# -------------------------------
# 3️⃣ Create merged truststore (robust)
# -------------------------------
RUN test -s /tmp/aws-global-bundle.pem && \
    mkdir -p /data/$APP_ARTIFACT/config && \
    \
    # Copy default Java truststore (includes Sectigo, DigiCert, etc.)
    cp $JAVA_CACERTS $CUSTOM_TRUSTSTORE && \
    \
    # Split AWS bundle into individual certs (portable)
    awk '/-----BEGIN CERTIFICATE-----/ {n++} {print > ("/tmp/aws-cert-" n ".pem")}' /tmp/aws-global-bundle.pem && \
    \
    # Import each cert safely
    for cert in /tmp/aws-cert-*; do \
        if [ -s "$cert" ]; then \
            keytool -import -noprompt -trustcacerts \
                -keystore $CUSTOM_TRUSTSTORE \
                -storepass $TRUSTSTORE_PASS \
                -alias "aws-$(basename $cert)" \
                -file "$cert" || true; \
        fi; \
    done && \
    \
    # Cleanup temp cert files
    rm -f /tmp/aws-cert-*

# -------------------------------
# 4️⃣ Create application directories and user
# -------------------------------
RUN mkdir -p /opt/atlas/$APP_ARTIFACT && \
    groupadd -r $APP_ARTIFACT && useradd -r -g $APP_ARTIFACT -m $APP_ARTIFACT && \
    chown -R $APP_ARTIFACT:$APP_ARTIFACT /opt/atlas/$APP_ARTIFACT /data/$APP_ARTIFACT

WORKDIR /opt/atlas/$APP_ARTIFACT

# -------------------------------
# 5️⃣ Add your application JAR
# -------------------------------
COPY target/lists-service-*-SNAPSHOT.jar app.jar

# -------------------------------
# 6️⃣ Expose port and run
# -------------------------------
USER $APP_ARTIFACT
EXPOSE $SERVER_PORT

CMD ["java", "-jar", "app.jar"]