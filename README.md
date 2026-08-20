# Spring Boot Kafka Demo

A minimal Spring Boot application with a Kafka producer and consumer. Kafka runs locally in Docker using KRaft mode, so no separate Zookeeper container is required.

## Run

Start Kafka:

```bash
docker compose up -d
```

Start the Spring Boot application:

```bash
mvn spring-boot:run
```

Publish a message:

```bash
curl -i -X POST http://localhost:8080/api/messages \
  -H 'Content-Type: application/json' \
  -d '{"message":"hello from Kafka"}'
```

The application returns `202 Accepted`. The consumer logs the message in the application terminal.

Stop Kafka:

```bash
docker compose down
```
