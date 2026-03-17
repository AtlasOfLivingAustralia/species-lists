# Use an official Maven runtime as a parent image
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set the working directory in the container
WORKDIR /app

# Copy the project files into the container
COPY pom.xml .
COPY src ./src

# Build the Maven project and create a WAR file
RUN mvn clean package -DskipTests

# -------------------------------
# Dockerfile: lists-service with merged truststore
# -------------------------------
FROM ubuntu:noble

ARG DEBIAN_FRONTEND=noninteractive

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        ca-certificates-java \
        openjdk-21-jdk \
        openjdk-21-jre \
        gnupg \
        coreutils \
        csplit \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /opt/atlas/lists-service

FROM ubuntu:noble

ARG DEBIAN_FRONTEND=noninteractive

# Install dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        ca-certificates-java \
        openjdk-21-jdk \
        openjdk-21-jre \
        gnupg \
        coreutils \
        csplit \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /opt/atlas/lists-service

# -------------------------------
# 1️⃣ Download certificates
# -------------------------------
RUN curl -fsSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o /tmp/aws-global-bundle.pem && \
    curl -fsSL "https://crt.sh/?d=1304325995" -o /tmp/sectigo-r36.crt

# -------------------------------
# 2️⃣ Create merged truststore in the same path as deployment.yaml expects
# -------------------------------
RUN mkdir -p /data/lists-service/config && \
    keytool -genkey -alias dummy \
        -keystore /data/lists-service/config/lists-service-truststore.jks \
        -storepass changeme \
        -keypass changeme \
        -dname "CN=dummy" && \
    keytool -delete -alias dummy \
        -keystore /data/lists-service/config/lists-service-truststore.jks \
        -storepass changeme && \
    keytool -import -noprompt -trustcacerts \
        -keystore /data/lists-service/config/lists-service-truststore.jks \
        -storepass changeme \
        -alias sectigo-r36 \
        -file /tmp/sectigo-r36.crt && \
    csplit -z -f /tmp/aws-cert- /tmp/aws-global-bundle.pem '/-----BEGIN CERTIFICATE-----/' '{*}' && \
    for cert in /tmp/aws-cert-*; do \
        keytool -import -noprompt -trustcacerts \
            -keystore /data/lists-service/config/lists-service-truststore.jks \
            -storepass changeme \
            -alias "aws-$(basename $cert)" \
            -file "$cert"; \
    done && \
    rm /tmp/aws-cert-* /tmp/aws-global-bundle.pem /tmp/sectigo-r36.crt

# -------------------------------
# 3️⃣ Add your application
# -------------------------------
ARG APP_ARTIFACT=lists-service
COPY target/lists-service-*-SNAPSHOT.jar app.jar

# -------------------------------
# 4️⃣ Keep JVM flags unchanged (deployment.yaml still works)
# -------------------------------
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]