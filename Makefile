APP_NAME ?= jbossqueues
IMAGE_REGISTRY ?= ghcr.io
IMAGE_OWNER ?= h4rkon
VERSION_FILE ?= VERSION
VERSION ?= $(shell cat $(VERSION_FILE))
IMAGE_TAG ?= $(VERSION)
IMAGE ?= $(IMAGE_REGISTRY)/$(IMAGE_OWNER)/$(APP_NAME):$(IMAGE_TAG)
GHCR_USER ?= $(IMAGE_OWNER)
GHCR_TOKEN_FILE ?= .secret/.gitpat
MAVEN ?= mvn
DOCKER ?= docker

.PHONY: bump-version clean package docker-build docker-login docker-push print-image print-version run

print-version:
	@echo $(VERSION)

bump-version:
	@next=$$(expr $$(cat "$(VERSION_FILE)") + 1); \
	printf '%s\n' "$$next" > "$(VERSION_FILE)"; \
	echo "$$next"

clean:
	$(MAVEN) clean

package:
	$(MAVEN) clean package

docker-build: package
	$(DOCKER) build --pull -t $(IMAGE) .

docker-login:
	@if [ -n "$$GHCR_TOKEN" ]; then \
		printf '%s' "$$GHCR_TOKEN" | $(DOCKER) login $(IMAGE_REGISTRY) -u "$(GHCR_USER)" --password-stdin; \
	elif [ -f "$(GHCR_TOKEN_FILE)" ]; then \
		$(DOCKER) login $(IMAGE_REGISTRY) -u "$(GHCR_USER)" --password-stdin < "$(GHCR_TOKEN_FILE)"; \
	else \
		echo "GHCR_TOKEN is not set and $(GHCR_TOKEN_FILE) does not exist."; \
		echo "Create a GitHub token with write:packages and store it in $(GHCR_TOKEN_FILE), or run GHCR_TOKEN=<token> make docker-login GHCR_USER=<github-user>."; \
		exit 1; \
	fi

docker-push:
	$(DOCKER) push $(IMAGE)

print-image:
	@echo $(IMAGE)

run: docker-build
	$(DOCKER) run --rm -p 8080:8080 -p 9990:9990 $(IMAGE)
