# List UI front end

This is the front end for the species-lists project.
It is a React app written in Typescript that uses the Mantine UI framework.

## Getting started

```bash
yarn run dev
```

## Production build

```bash
yarn run build
```

## Environment variables

The following environment variables are used:

```properties
VITE_DOWNLOAD_URL=http://localhost:8080/download
VITE_UPLOAD_URL=http://localhost:8080/upload
VITE_INGEST_URL=http://localhost:8080/ingest
VITE_DELETE_URL=http://localhost:8080/delete
VITE_GRAPHQL_URL=http://localhost:8080/graphql
VITE_METADATA_URL=http://localhost:8080/metadata
VITE_REINDEX_URL=http://localhost:8080/reindex
VITE_REMATCH_URL=http://localhost:8080/rematch
VITE_MIGRATE_URL=http://localhost:8080/migrate
VITE_HOME_URL=https://www.ala.org.au
VITE_LOGO_URL=https://www.ala.org.au/app/uploads/2019/01/logo.png
VITE_OIDC_AUTH_SERVER=https://auth-test.ala.org.au/cas/oidc
VITE_OIDC_AUTH_PROFILE=https://auth-test.ala.org.au/userdetails/myprofile
VITE_SIGNUP_URL=https://auth-test.ala.org.au/userdetails/registration/createAccount
VITE_OIDC_CLIENT_ID=TO_BE_ADDED
VITE_OIDC_REDIRECT_URL=http://localhost:5173
VITE_OIDC_SCOPE=TO_BE_ADDED
VITE_ROLE_ADMIN=ROLE_ADMIN
VITE_APP_MAPBOX_TOKEN=TO_BE_ADDED
VITE_APP_BIE_URL=https://bie.ala.org.au

# User roles are found in property 'role' (OIDC) or 'cognito:groups' (Cognito)
VITE_PROFILE_ROLES=cognito:groups

# User id is found in profile property 'userid' (OIDC) or 'cognito:username' (Cognito)
VITE_PROFILE_USERID=cognito:username

# Admin role is 'ROLE_ADMIN' (OIDC) or 'admin' (Cognito)
VITE_ADMIN_ROLE=admin
```
