FROM ubuntu:focal

ARG ENV

ARG DEBIAN_FRONTEND=noninteractive

# Install initial dependencies
RUN apt-get -y update && \
        apt-get install -y -q add-apt-key software-properties-common

ARG APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=DontWarn

# Add the apt.gbif.es repo and gpg key
RUN echo "deb [arch=amd64] https://apt.gbif.es/ bionic main" > /etc/apt/sources.list.d/ala.list && \
        apt-key adv --keyserver keyserver.ubuntu.com --recv-keys F697D8D2ADB9E24A

# Install main dependencies
RUN apt-get install -y -q openjdk-17-jdk openjdk-17-jre

ADD ../../../target/lists-service-0.0.1-SNAPSHOT.jar /data/lists-service.jar

EXPOSE 8080

CMD ["java", "-jar", "/data/lists-service.jar", "--spring.config.location=classpath:file:///data/lists-service/config/lists-service-config.properties"]