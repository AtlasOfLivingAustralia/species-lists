# Use an official Maven runtime as a parent image
FROM maven:3.8.3-openjdk-17 AS builder

# Set the working directory in the container
WORKDIR /app

# Copy the project files into the container
COPY pom.xml .
COPY src ./src

# Build the Maven project and create a WAR file
RUN mvn clean package -DskipTests

FROM ubuntu:focal

ARG DEBIAN_FRONTEND=noninteractive
ENV SERVER_PORT="8080"

# Install initial dependencies
RUN apt-get -y update && \
        apt-get install -y -q add-apt-key software-properties-common

ARG APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=DontWarn
ARG USER=lists-service
ARG APP_ARTIFACT=lists-service

# Add the apt.gbif.es repo and gpg key
RUN echo "deb [arch=amd64] https://apt.gbif.es/ bionic main" > /etc/apt/sources.list.d/ala.list && \
        apt-key adv --keyserver keyserver.ubuntu.com --recv-keys F697D8D2ADB9E24A

# Install main dependencies
RUN apt-get install -y -q openjdk-17-jdk openjdk-17-jre

# Directories and perms
RUN mkdir -p /data/$APP_ARTIFACT && \
    groupadd -r $USER -g 1000 && useradd -r -g $USER -u 1000 -m $USER && \
    chown -R $USER:$USER /data/$APP_ARTIFACT
WORKDIR /opt/atlas/$APP_ARTIFACT

COPY --from=builder /app/target/lists-service-*-SNAPSHOT.jar /opt/atlas/$APP_ARTIFACT/app.jar

USER $USER
EXPOSE $SERVER_PORT

CMD ["java", "-jar", "app.jar", "--spring.config.location=classpath:file:///data/lists-service/config/lists-service-config.properties"]