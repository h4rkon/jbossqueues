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

## Kafka

Kafka configuration is declared as JNDI environment entries in `WEB-INF/web.xml`.
Kafka is enabled when these environment variables are present:

```text
KAFKA_BOOTSTRAP_SERVERS=kafka.kafka.svc.cluster.local:9092
KAFKA_TOPIC_MESSAGES=jbossqueues.messages
```

The app publishes every `POST /api/messages` payload to Kafka and runs a
background consumer in the same WildFly deployment. If the variables are absent,
the HTTP endpoint still works and Kafka is skipped.

### Runtime Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant WildFly as WildFly/JBoss
    participant Resource as MessageResource
    participant Publisher as MessagePublisher<MessagePayload>
    participant KafkaPublisher as KafkaMessagePublisher
    participant Kafka as Kafka topic<br/>jbossqueues.messages
    participant KafkaConsumer as KafkaMessageConsumer
    participant Logs as WildFly stdout

    Client->>WildFly: POST /api/messages<br/>{"key":"sauermann","value":"5"}
    WildFly->>Resource: Dispatch JAX-RS request
    Resource->>Logs: Log HTTP payload
    Resource->>Publisher: publish(MessagePayload)
    Publisher->>KafkaPublisher: CDI resolves Kafka implementation
    KafkaPublisher->>Kafka: Produce JSON message<br/>key=sauermann
    KafkaPublisher->>Logs: Log produced topic/partition/offset
    Resource-->>Client: 200 OK<br/>echo payload
    KafkaConsumer->>Kafka: Poll topic in background
    Kafka-->>KafkaConsumer: ConsumerRecord
    KafkaConsumer->>KafkaConsumer: MessagePayload.fromJson(record.value)
    KafkaConsumer->>Logs: Log consumed payload
```

### Container Wiring

```mermaid
classDiagram
    direction LR

    class WildFlyContainer {
        <<container>>
        creates JAX-RS resources
        creates CDI beans
        creates EJB singletons
        injects JNDI env entries
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

    class QueueConfiguration {
        <<interface>>
        +enabled() boolean
        +bootstrapServers() String
        +topic() String
        +consumerGroup() String
    }

    class AbstractMessagePublisher~T~ {
        <<abstract>>
        #logger() Logger
    }

    class AbstractMessageConsumer~T~ {
        <<abstract>>
        #logger() Logger
    }

    class KafkaMessagePublisher {
        <<CDI ApplicationScoped>>
        -QueueConfiguration configuration
        -KafkaProducer producer
        +publish(MessagePayload)
        +start()
        +stop()
    }

    class KafkaMessageConsumer {
        <<EJB Singleton Startup>>
        -QueueConfiguration configuration
        -ManagedExecutorService executorService
        -KafkaConsumer consumer
        +onMessage(MessagePayload)
        +start()
        +stop()
    }

    class KafkaQueueConfiguration {
        <<EJB Singleton>>
        -String bootstrapServers
        -String topicMessages
        -String consumerGroup
    }

    class WebXml {
        <<WEB-INF/web.xml>>
        env-entry kafka/bootstrapServers
        env-entry kafka/topicMessages
        env-entry kafka/consumerGroup
    }

    WildFlyContainer ..> MessageResource : creates
    WildFlyContainer ..> KafkaMessagePublisher : creates CDI bean
    WildFlyContainer ..> KafkaMessageConsumer : starts EJB
    WildFlyContainer ..> KafkaQueueConfiguration : creates EJB
    WildFlyContainer ..> WebXml : reads

    MessageResource --> MessagePublisher~MessagePayload~ : @Inject
    KafkaMessagePublisher ..|> MessagePublisher~MessagePayload~
    KafkaMessagePublisher --|> AbstractMessagePublisher~MessagePayload~

    KafkaMessageConsumer ..|> MessageConsumer~MessagePayload~
    KafkaMessageConsumer --|> AbstractMessageConsumer~MessagePayload~

    KafkaMessagePublisher --> QueueConfiguration : @Inject
    KafkaMessageConsumer --> QueueConfiguration : @Inject
    KafkaQueueConfiguration ..|> QueueConfiguration
    WebXml ..> KafkaQueueConfiguration : @Resource env-entry
```
