APP_NAME ?= jbossqueues
IMAGE_REGISTRY ?= ghcr.io
IMAGE_OWNER ?= hzd
IMAGE_TAG ?= local
IMAGE ?= $(IMAGE_REGISTRY)/$(IMAGE_OWNER)/$(APP_NAME):$(IMAGE_TAG)
MAVEN ?= mvn
DOCKER ?= docker

.PHONY: clean package docker-build docker-push run

clean:
	$(MAVEN) clean

package:
	$(MAVEN) package

docker-build: package
	$(DOCKER) build --pull -t $(IMAGE) .

docker-push:
	$(DOCKER) push $(IMAGE)

run: docker-build
	$(DOCKER) run --rm -p 8080:8080 $(IMAGE)
