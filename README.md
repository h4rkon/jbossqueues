# jbossqueues

Minimal Jakarta EE service shell packaged as a WAR for WildFly.

## Build

```sh
mvn package
```

The deliverable is written to `target/jbossqueues.war`.

## Docker image

```sh
make docker-build
```

By default the image name is:

```text
ghcr.io/h4rkon/jbossqueues:<VERSION>
```

The image tag is read from `VERSION`. Override `IMAGE_REGISTRY`, `IMAGE_OWNER`,
`VERSION`, `IMAGE_TAG`, or `IMAGE` when building for another registry or tag.

For a new rollout version:

```sh
make bump-version
make print-image
make docker-build
make docker-push
```

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
make docker-build
make docker-push
```

If push fails with `permission_denied: create_package`, the token is valid but
GitHub is not allowing that user to create the package under `IMAGE_OWNER`.
Use an owner that your GitHub user can publish to, or grant package creation
permission in the GitHub organization.

To confirm the full image name before pushing:

```sh
make print-image
```

## Run locally

```sh
make run
curl http://localhost:8080/api/messages
curl -X POST http://localhost:8080/api/messages \
  -H 'Content-Type: application/json' \
  -d '{"key":"sauermann","value":"5"}'
```

## Reactive Messaging

Kafka is configured through MicroProfile Config (https://docs.wildfly.org/quickstart/microprofile-reactive-messaging-kafka/README.html) in
`META-INF/microprofile-config.properties`. These environment variables are used
by the channel configuration:

```text
KAFKA_BOOTSTRAP_SERVERS=kafka.kafka.svc.cluster.local:9092
KAFKA_TOPIC_MESSAGES=jbossqueues.messages
KAFKA_TOPIC_MESSAGES_DLQ=jbossqueues.messages.dlq
```

The app publishes every `POST /api/messages` payload to Kafka and runs a
Reactive Messaging consumer in the same WildFly deployment.

Resilience is handled by MicroProfile/SmallRye:

- producer retry/circuit breaker: MicroProfile Fault Tolerance annotations
- Kafka retry: SmallRye Kafka connector properties
- consumer DLQ: SmallRye Kafka `dead-letter-queue` failure strategy

### Runtime Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant WildFly as WildFly/JBoss
    participant Resource as MessageResource
    participant Publisher as MessagePublisher<MessagePayload>
    participant ReactivePublisher as ReactiveMessagePublisher
    participant Emitter as MP Reactive Messaging Emitter
    participant Kafka as Kafka topic<br/>jbossqueues.messages
    participant ReactiveConsumer as ReactiveMessageConsumer
    participant DLQ as Kafka DLQ topic<br/>jbossqueues.messages.dlq
    participant Logs as WildFly stdout

    Client->>WildFly: POST /api/messages<br/>{"key":"sauermann","value":"5"}
    WildFly->>Resource: Dispatch JAX-RS request
    Resource->>Logs: Log HTTP payload
    Resource->>Publisher: publish(MessagePayload)
    Publisher->>ReactivePublisher: CDI resolves Reactive Messaging implementation
    ReactivePublisher->>Emitter: send JSON to channel messages-out
    Emitter->>Kafka: SmallRye Kafka connector writes record
    ReactivePublisher->>Logs: Log published payload
    Resource-->>Client: 200 OK<br/>echo payload
    Kafka-->>ReactiveConsumer: channel messages-in receives JSON
    alt consume succeeds
        ReactiveConsumer->>ReactiveConsumer: MessagePayload.fromJson(json)
        ReactiveConsumer->>Logs: Log consumed payload
    else consume fails after retries
        ReactiveConsumer-->>DLQ: connector sends failed record to DLQ
    end
```

### Container Wiring

```mermaid
classDiagram
    direction LR

    class WildFlyContainer {
        <<container>>
        creates JAX-RS resources
        creates CDI beans
        enables reactive messaging subsystem
        enables fault tolerance subsystem
    }

    class MessageResource {
        <<JAX-RS resource>>
        +list() Response
        +create(MessagePayload) Response
    }

    class MessagePayload {
        <<record>>
        +String key
        +String value
        +fromJson(String) MessagePayload
        +toJson() String
    }

    class MessagePublisher~T~ {
        <<interface>>
        +publish(T message)
    }

    class MessageConsumer~T~ {
        <<interface>>
        +onMessage(T message)
    }

    class AbstractMessagePublisher~T~ {
        <<abstract>>
        #logger() Logger
    }

    class AbstractMessageConsumer~T~ {
        <<abstract>>
        #logger() Logger
    }

    class ReactiveMessagePublisher {
        <<CDI ApplicationScoped>>
        -Emitter<String> emitter
        +publish(MessagePayload)
        @Retry
        @CircuitBreaker
    }

    class ReactiveMessageConsumer {
        <<CDI ApplicationScoped>>
        +consume(String)
        +onMessage(MessagePayload)
        @Incoming messages-in
    }

    class MicroProfileConfig {
        <<META-INF/microprofile-config.properties>>
        mp.messaging.outgoing.messages-out
        mp.messaging.incoming.messages-in
        dead-letter-queue
    }

    class ReactiveMessagingRuntime {
        <<WildFly SmallRye>>
        channel messages-out
        channel messages-in
        Kafka connector
    }

    class FaultToleranceRuntime {
        <<WildFly SmallRye>>
        @Retry
        @CircuitBreaker
    }

    WildFlyContainer ..> MessageResource : creates
    WildFlyContainer ..> ReactiveMessagePublisher : creates CDI bean
    WildFlyContainer ..> ReactiveMessageConsumer : creates CDI bean
    WildFlyContainer ..> ReactiveMessagingRuntime : starts
    WildFlyContainer ..> FaultToleranceRuntime : starts
    MicroProfileConfig ..> ReactiveMessagingRuntime : configures channels

    MessageResource --> MessagePublisher~MessagePayload~ : @Inject
    ReactiveMessagePublisher ..|> MessagePublisher~MessagePayload~
    ReactiveMessagePublisher --|> AbstractMessagePublisher~MessagePayload~
    ReactiveMessagePublisher --> ReactiveMessagingRuntime : @Channel messages-out
    ReactiveMessagePublisher --> FaultToleranceRuntime : annotations

    ReactiveMessageConsumer ..|> MessageConsumer~MessagePayload~
    ReactiveMessageConsumer --|> AbstractMessageConsumer~MessagePayload~
    ReactiveMessagingRuntime --> ReactiveMessageConsumer : @Incoming messages-in
```
