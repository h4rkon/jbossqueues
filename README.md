# jbossqueues

Minimal Jakarta EE service shell packaged as a WAR for WildFly.

## Build

```sh
mvn package
```

The deliverable is written to `target/jbossqueues.war`.

## Docker image

```sh
make docker-build IMAGE_OWNER=<github-owner-or-org> IMAGE_TAG=0.1.0
```

By default the image name is:

```text
ghcr.io/hzd/jbossqueues:local
```

Override `IMAGE_REGISTRY`, `IMAGE_OWNER`, `IMAGE_TAG`, or `IMAGE` when building for another registry.

## Push

Log in to GitHub Container Registry first. `GHCR_USER` is your GitHub user or
organization account, and the token must have `write:packages` permission.

```sh
GHCR_TOKEN=<github-token> make docker-login GHCR_USER=<github-user-or-org>
```

For local development, you can also store the token in a gitignored file:

```sh
mkdir -p .secret
printf '%s' '<github-token>' > .secret/.gitpat
chmod 600 .secret/.gitpat
make docker-login GHCR_USER=<github-user-or-org>
```

Do not commit `.secret/`. The folder is ignored by Git.

Build and push the exact image name that your Kubernetes deployment will use:

```sh
make docker-build IMAGE_OWNER=<github-owner-or-org> IMAGE_TAG=0.1.0
make docker-push IMAGE_OWNER=<github-owner-or-org> IMAGE_TAG=0.1.0
```

If push fails with `permission_denied: create_package`, the token is valid but
GitHub is not allowing that user to create the package under `IMAGE_OWNER`.
Use an owner that your GitHub user can publish to, or grant package creation
permission in the GitHub organization.

To confirm the full image name before pushing:

```sh
make print-image IMAGE_OWNER=<github-owner-or-org> IMAGE_TAG=0.1.0
```

## Run locally

```sh
make run
curl http://localhost:8080/api/messages
```
