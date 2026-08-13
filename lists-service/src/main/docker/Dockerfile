# Use an official Maven runtime as a parent image
FROM --platform=$BUILDPLATFORM maven:3.9.16-eclipse-temurin-21-noble AS builder

# Set the working directory in the container
WORKDIR /app

# Copy the project files into the container
COPY pom.xml .
COPY src ./src

# Build the Maven project and create a WAR file
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-noble

ENV SERVER_PORT="8080"

ARG USER=lists-service
ARG APP_ARTIFACT=lists-service

# Add the apt.gbif.es repo and gpg key (modern apt-key replacement)
RUN curl -fsSL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0xF697D8D2ADB9E24A" \
    | gpg --dearmor -o /etc/apt/keyrings/ala.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/ala.gpg] https://apt.gbif.es/ bionic main" \
    > /etc/apt/sources.list.d/ala.list

# Directories and perms
RUN mkdir -p /data/$APP_ARTIFACT && \
    groupadd -r $USER && useradd -r -g $USER -m $USER && \
    chown -R $USER:$USER /data/$APP_ARTIFACT
WORKDIR /opt/atlas/$APP_ARTIFACT

COPY --from=builder /app/target/lists-service-*.jar /opt/atlas/$APP_ARTIFACT/app.jar

USER $USER
EXPOSE $SERVER_PORT

CMD ["java", "-jar", "app.jar"]