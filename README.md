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

```sh
make docker-push IMAGE_OWNER=<github-owner-or-org> IMAGE_TAG=0.1.0
```

## Run locally

```sh
make run
curl http://localhost:8080/api/messages
```
