# List service - GraphQL and REST services for species lists

This is the back end for the species-lists project.
It is a Spring Boot application that provides REST and GraphQL web services for accessing, modifying species lists.

## Getting started

```bash
./gradlew bootRun
```

## Elasticsearch and MongoDB

To run the elasticsearch and mongodb containers, use the following command:

```bash
docker-compose -f src/main/docker/docker-compose.yml up
```

## GraphQL

The GraphQL test interface is available at `http://localhost:8080/graphiql`.

## REST

The Swagger UI for REST services are available at `http://localhost:8080`.


# Docker hub

The docker images for list-service are available on [docker hub](https://hub.docker.com/r/atlasoflivingaustralia/lists-service). 
Commits to this `develop` branch will result in a new image being built and pushed to the `latest` tag on docker hub.

