# Local TXT-to-PDF SaaS

This project is a local, self-hosted TXT-to-PDF system. A React frontend accepts a `.txt` file, the Spring Boot API creates an asynchronous job, Kafka delivers the job to a Python worker, and the worker stores the generated PDF in MinIO. The frontend polls the API and downloads the completed PDF through a presigned MinIO URL.

## Architecture

```mermaid
flowchart LR
    Browser[React frontend\nlocalhost:5173]
    API[Spring Boot API\nlocalhost:8080 or 8082]
    DB[(PostgreSQL\nlocalhost:5432\njobs table)]
    Kafka[(Kafka\nlocalhost:9092)]
    Worker[Python TXT-to-PDF worker\nDocker containers]
    MinIO[(MinIO object storage\nAPI: 9000\nConsole: 9001)]

    Browser -->|POST /api/jobs/upload| API
    API -->|save input_text and QUEUED job| DB
    API -->|publish pdf-jobs| Kafka
    Kafka -->|consume jobId| Worker
    Worker -->|read input_text by jobId| DB
    Worker -->|upload jobId.pdf| MinIO
    Worker -->|publish pdf-completed| Kafka
    Worker -->|mark COMPLETED and outputLocation| DB
    Browser -->|poll GET /api/jobs/{jobId}| API
    Browser -->|GET /api/jobs/{jobId}/download| API
    API -->|presigned URL| Browser
    Browser -->|download PDF| MinIO
```

### Request flow

1. The user selects or drops a `.txt` file in the React frontend.
2. The frontend sends the file as multipart form data to `POST /api/jobs/upload`.
3. The API stores the text content in PostgreSQL in the `jobs.input_text` column.
4. The API creates a job with a unique `jobId` and status `QUEUED`.
5. The API publishes a job event to Kafka topic `pdf-jobs`.
6. A Python worker consumes the event and reads the text from PostgreSQL.
7. The worker converts the text to PDF and uploads `<jobId>.pdf` to the MinIO bucket `pdf-bucket`.
8. The worker marks the job `COMPLETED` and publishes a `pdf-completed` event.
9. The frontend polls the job status until completion.
10. The API creates a short-lived presigned MinIO URL. The browser uses that URL to download the PDF.

Kafka carries job metadata and events. It does not carry the uploaded text file or PDF binary. The uploaded text is stored in PostgreSQL, and generated PDFs are stored in MinIO.

## Service ports

| Service | Host port | Container port | Purpose |
| --- | ---: | ---: | --- |
| React/Vite frontend | `5173` | `5173` | Browser upload and download UI |
| Spring Boot API in Docker | `8080` | `8080` | REST API when using the full Compose stack |
| Spring Boot API locally | `8082` | `8082` | REST API when running `mvn spring-boot:run` beside Docker |
| Kafka UI | `8081` | `8080` | Optional Kafka topic and consumer inspection |
| PostgreSQL | `5432` | `5432` | Job persistence |
| Kafka external listener | `9092` | `9092` | Host applications connecting to Kafka |
| Kafka controller | `9093` | `9093` | Kafka controller port |
| MinIO API | `9000` | `9000` | S3-compatible object API |
| MinIO console | `9001` | `9001` | MinIO web console |

Inside Docker, services use container DNS names and internal ports, for example `kafka:29092`, `postgres:5432`, and `minio:9000`. From the host, use `localhost` and the host ports in the table.

## Prerequisites

- Docker and Docker Compose
- Java 21 or newer
- Maven
- Node.js and npm

## Run the whole project with Docker

This is the simplest full-stack option. It runs PostgreSQL, Kafka, Kafka UI, MinIO, the Spring Boot API, and two worker containers in Docker. The API is available on port `8080`.

### 1. Start the backend stack

From the `enterprise` directory:

```bash
mvn -B -DskipTests package
docker compose up -d --build
```

The Maven command builds the API JAR that Docker copies into the API image. Run it again after changing backend code. Docker then rebuilds the API image without downloading Maven dependencies inside the container.

Check that all containers are running:

```bash
docker compose ps
```

The first startup can take a little longer while Docker builds the API and worker images and PostgreSQL/Kafka initialize.

### 2. Start the React frontend

```bash
cd frontend
npm install
VITE_API_URL=http://localhost:8080 npm run dev
```

Open [http://localhost:5173](http://localhost:5173), choose a `.txt` file, and click **Upload and convert**. The frontend polls the API and shows **Download PDF** when the worker finishes.

### 3. Open the optional administration tools

- Kafka UI: [http://localhost:8081](http://localhost:8081)
- MinIO console: [http://localhost:9001](http://localhost:9001)
  - Username: `minioadmin`
  - Password: `minioadmin`
- MinIO API: [http://localhost:9000](http://localhost:9000)

## Run the API locally with Docker infrastructure

Use this mode when you want to edit or debug the Spring Boot API locally. Docker still runs PostgreSQL, Kafka, MinIO, and the Python workers. The Docker API must be stopped so it does not occupy port `8080`.

### 1. Start infrastructure and workers

```bash
docker compose up -d postgres kafka kafka-ui minio txttopdf txttopdf-worker-2
```

### 2. Stop only the Docker API

```bash
docker compose stop api
```

### 3. Start Spring Boot on port 8082

From the `enterprise` directory:

```bash
SERVER_PORT=8082 mvn spring-boot:run
```

Keep this terminal running. The local API uses host services through `localhost`: PostgreSQL `5432`, Kafka `9092`, and MinIO `9000`.

### 4. Start the frontend against port 8082

In a second terminal:

```bash
cd frontend
npm install
VITE_API_URL=http://localhost:8082 npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

Do not run both the Docker API and the local API for the same workflow unless you intentionally want two API consumer instances. They share the same database and Kafka consumer group.

## API smoke test

Create a job directly without the frontend:

```bash
curl -i -X POST http://localhost:8080/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{
    "userId":"user-123",
    "inputText":"This is a test PDF job.",
    "jobType":"TXT_TO_PDF"
  }'
```

For the local API mode, replace `8080` with `8082`.

Upload an actual text file:

```bash
curl -i -X POST http://localhost:8080/api/jobs/upload \
  -F 'userId=user-123' \
  -F 'jobType=TXT_TO_PDF' \
  -F 'file=@/absolute/path/to/notes.txt'
```

The response contains a `jobId`. Poll it until it is complete:

```bash
curl http://localhost:8080/api/jobs/<jobId>
```

When `status` is `COMPLETED`, request the download URL:

```bash
curl http://localhost:8080/api/jobs/<jobId>/download
```

The response contains a presigned `url` and the MinIO object `fileName`.

## Quick debugging

### Check container health and logs

```bash
docker compose ps
docker compose logs --tail=100 api
docker compose logs --tail=100 txttopdf
docker compose logs --tail=100 txttopdf-worker-2
```

Follow a service while submitting a job:

```bash
docker compose logs -f api txttopdf
```

### Check which process owns a port

```bash
ss -lntp | grep -E ':5173|:8080|:8081|:8082|:5432|:9000|:9001'
```

If the browser reports `Failed to fetch`, check these in order:

1. Confirm the frontend API target. The frontend defaults to `http://localhost:8082`:

   ```bash
   echo "$VITE_API_URL"
   ```

   Use `VITE_API_URL=http://localhost:8080` when the Docker API is running.

2. Check the API directly:

   ```bash
   curl -i http://localhost:8082/api/jobs/does-not-exist
   ```

3. Test the upload route. A working route returns `202`, while `405 Method Not Allowed` usually means an older or wrong API instance is on that port:

   ```bash
   curl -i -X POST http://localhost:8082/api/jobs/upload \
     -F 'userId=debug-user' \
     -F 'file=@/absolute/path/to/notes.txt'
   ```

4. If the API is reachable from curl but not from the browser, restart the API after changing CORS settings and inspect the browser developer console for the blocked origin.

### Check Kafka

List topics:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list
```

Inspect submitted jobs:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic pdf-jobs \
  --from-beginning
```

Inspect completion events:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic pdf-completed \
  --from-beginning
```

### Check PostgreSQL

```bash
docker compose exec postgres psql -U saas_user -d saas_db \
  -c 'SELECT job_id, status, output_location, created_at FROM jobs ORDER BY created_at DESC LIMIT 10;'
```

The uploaded text is in `jobs.input_text`. A completed job should have an `output_location` similar to `minio://pdf-bucket/<jobId>.pdf`.

### Check MinIO objects

The MinIO console is available at [http://localhost:9001](http://localhost:9001). To inspect objects from a shell, install the MinIO client (`mc`), configure an alias, and list the bucket:

```bash
mc alias set local http://localhost:9000 minioadmin minioadmin
mc ls --recursive local/pdf-bucket
```

If the job is `COMPLETED` but no object exists, inspect both worker logs and the worker's MinIO environment variables.

### Common failures

| Symptom | Likely cause | Check or fix |
| --- | --- | --- |
| `Failed to fetch` in browser | Wrong API port, API stopped, or CORS mismatch | Set `VITE_API_URL` to the active API port and restart the API/frontend |
| `405 Method Not Allowed` on upload | Old API instance is listening on that port | Check `ss -lntp`, stop the stale process, or use the correct port |
| Job remains `QUEUED` | Worker or Kafka is unavailable | Check `docker compose logs txttopdf` and Kafka topics |
| Job is `COMPLETED` but download fails | PDF object is missing or MinIO is unavailable | Check `output_location`, worker logs, and `mc ls` |
| Port bind error for `8080` | Docker API already owns the port | Stop `api` or run the local API on `8082` |
| Database connection failure | PostgreSQL is not ready or credentials differ | Run `docker compose ps` and inspect `docker compose logs postgres` |

## Job states

- `QUEUED`: the API stored the job and published the Kafka message.
- `PROCESSING`: a worker has claimed the job.
- `COMPLETED`: the PDF is in MinIO and `output_location` is saved.
- `FAILED`: conversion or persistence failed; inspect API and worker logs.

## Stop and reset

Stop containers but keep PostgreSQL and MinIO data volumes:

```bash
docker compose down
```

Stop containers and delete all local database and object-storage data:

```bash
docker compose down -v
```

The `-v` option is destructive for local data. Use it when you need a clean database and empty MinIO bucket.
