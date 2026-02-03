# List service - GraphQL and REST services for species lists

This is the back end for the species-lists project.
It is a Spring Boot (Maven) application that provides REST and GraphQL web services for accessing, modifying and administering species lists.

## Getting started

### Local development setup

The recommended way to run this app for local development, is to use the [DevContainer](https://containers.dev/) environment. 
This has the advantage of not requiring an installed version of java or maven - the container provides these. 

A container runtime is needed, such as Docker Desktop, OrbStack, Podman Desktop, etc. Ensure this is running first.

Both VSCode and IntelliJ IDEA will detect the included `.devcontainer/devcontainer.json` file and launch an app container for development, 
as well as the 2 required containers for ElasticSearch and MongoDB. 

Create a local config properties file at `/data/lists-service/config/lists-service-config.properties`. 

Add the following properties, sourcing client IDs (values with`<insert-client-id>`) and secrets (values with `<insert-client-secret>`) from the [OIDC Migration on ALA Applications](https://confluence.csiro.au/spaces/ALASD/pages/1661624100/OIDC+Migration+on+ALA+Applications) Confulence page.

```properties
# AUTH config common to CAS and Cognito
security.admin.role=ROLE_ADMIN,ala/internal
security.apikey.enabled=false
security.jwt.enabled=true
security.cas.enabled=false
security.oidc.enabled=true

# Cognito AUTH settings
security.jwt.clientId=<insert-client-id>
security.jwt.discovery-uri=https://cognito-idp.ap-southeast-2.amazonaws.com/ap-southeast-2_OOXU9GW39/.well-known/openid-configuration
security.jwt.roleClaims=cognito:groups
security.jwt.rolesFromAccessToken=true
security.jwt.userIdClaim=username
security.oidc.discoveryUri=https://cognito-idp.ap-southeast-2.amazonaws.com/ap-southeast-2_OOXU9GW39/.well-known/openid-configuration
security.oidc.clientId=<insert-client-id>
security.oidc.scope=openid profile email ala/attrs ala/roles
security.oidc.secret=<insert-client-secret>
userDetails.api.url=https://api.test.ala.org.au/userdetails/cognito
userDetails.web.url=https://userdetails.test.ala.org.au/
webservice.client-id=<insert-client-id>
webservice.client-secret=<insert-client-secret>
webservice.jwt-scopes=users/read ala/internal
```

When opening the project in the IDE, select the "Open in container" option. Everything should be automatic after that.

### Running the application locally

```bash
mvn spring-boot:run
```

### Elasticsearch and MongoDB

Elasticsearch and MongoDB are needed to run this app locally and if you are NOT using the Dev Container method, 
the easiest way to run these is via `docker-compose` (Docker Desktop or an equivalent container app is required).

Run the following command:

```bash
docker-compose -f lists-service/src/main/docker/docker-compose.yml up -d
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
