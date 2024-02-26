# List service - GraphQL and REST services for species lists

This is the back end for the species-lists project.
It is a Spring Boot application that provides REST and GraphQL web services for accessing, modifying species lists.

## Getting started

### Elasticsearch and MongoDB

To run the elasticsearch and mongodb containers needed for the list-service
to run, use the following command:

```bash
docker-compose -f src/main/docker/docker-compose.yml up
```

### Running the application locally

```bash
./gradlew bootRun
```

## GraphQL

The GraphQL test interface is available at `http://localhost:8080/graphiql`.

## REST

The Swagger UI for REST services are available at `http://localhost:8080`.

## Docker hub

The docker images for list-service are available on [docker hub](https://hub.docker.com/r/atlasoflivingaustralia/lists-service). 
Commits to this `develop` branch will result in a new image being built and pushed to the `latest` tag on docker hub.

## Helm charts

The helm charts for list-service are available in the 
[helm-charts](https://github.com/AtlasOfLivingAustralia/helm-charts) repository.