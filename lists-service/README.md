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

Add the following properties, sourcing client IDs (values with`<insert-client-id>`) and secrets (values with `<insert-client-secret>`) from the [OIDC Migration on ALA Applications](https://confluence.csiro.au/spaces/ALASD/pages/1661624100/OIDC+Migration+on+ALA+Applications) Confluence page.

```properties
# AUTH config common to CAS and Cognito
security.admin.roles=ROLE_ADMIN,ala/internal
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
security.oidc.scope=openid profile email ala/attrs ala/roles
userDetails.api.url=https://api.test.ala.org.au/userdetails/cognito
userDetails.web.url=https://userdetails.test.ala.org.au/
webservice.client-id=<insert-client-id>
webservice.client-secret=<insert-client-secret>
webservice.jwt-scopes=users/read ala/internal
```

The client id for incoming tokens is read from `security.jwt.clientId`, and the credentials for
outgoing calls from `webservice.client-id` / `webservice.client-secret`. `security.oidc.clientId` and
`security.oidc.secret` are bound by ala-security-common 8.x, but no code reads them. See [Configuration](#configuration)
for the properties the service needs to start.

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

## Configuration

`src/main/resources/application.properties` holds ALA's defaults. A deployment overrides them with
its own properties file, without rebuilding the image:

- `/data/lists-service/config/lists-service-config.properties` is loaded when it exists
  (`spring.config.import` in `application.properties`).
- For a file elsewhere, use Spring Boot's `SPRING_CONFIG_ADDITIONAL_LOCATION`:

```bash
SPRING_CONFIG_ADDITIONAL_LOCATION=file:/etc/lists-service/lists-service.properties
```

### Authentication

Incoming authentication is configuration only: the service validates JWTs from any OIDC provider
(Cognito, CAS, Keycloak...) set in `security.jwt.*`.

These properties have no usable default and must be set, non-blank, or the service does not start:

| Property | Error when missing |
|---|---|
| `security.jwt.clientId` | pac4j: `clientId cannot be blank` |
| `webservice.client-id` | `PlaceholderResolutionException` for `webservice.client-id` |
| `webservice.client-secret` | `PlaceholderResolutionException` for `webservice.client-secret` |

To use a provider other than ALA's Cognito, point the discovery URI at it and set the claims that
carry the user id and the roles. For example, for a CAS or Keycloak provider:

```properties
security.jwt.discoveryUri=https://auth.example.org/cas/oidc/.well-known
security.jwt.roleClaims=role
security.jwt.userIdClaim=userid
security.jwt.clientId=<client-id>
# roles (or scopes) that grant admin access
security.admin.roles=ROLE_ADMIN,ala/internal
# client credentials for outgoing calls to other services
webservice.client-id=<client-id>
webservice.client-secret=<client-secret>
```

## GraphQL

The GraphQL test interface is available at `http://localhost:8080/graphiql`.

## REST

The OpenAPI/Swagger UI for REST services are available at `http://localhost:8080`.

## Docker hub

The docker images for list-service are available on [docker hub](https://hub.docker.com/r/atlasoflivingaustralia/lists-service). 
Commits to this `develop` branch will result in a new image being built and pushed to the `latest` tag on docker hub.

## Helm charts

The helm charts for list-service are available in the 
[helm-charts](https://github.com/AtlasOfLivingAustralia/helm-charts) repository.
