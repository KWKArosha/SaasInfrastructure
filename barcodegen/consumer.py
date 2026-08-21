import json
import logging
import os
import re
import time
import uuid
from pathlib import Path
from urllib.parse import urlparse

import psycopg2
from barcode import Code128
from barcode.writer import ImageWriter
from kafka import KafkaConsumer, KafkaProducer
from minio import Minio

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092")
INPUT_TOPIC = os.getenv("KAFKA_INPUT_TOPIC", "pdf-jobs")
OUTPUT_TOPIC = os.getenv("KAFKA_OUTPUT_TOPIC", "pdf-completed")
GROUP_ID = os.getenv("KAFKA_GROUP_ID", "barcode-worker-group")
OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", "/output"))
MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "http://minio:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minioadmin")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "pdf-bucket")
JOB_DB_URL = os.getenv("KAFKA_JOB_DB_URL", "jdbc:postgresql://postgres:5432/saas_db")
JOB_DB_USER = os.getenv("KAFKA_JOB_DB_USER", "saas_user")
JOB_DB_PASSWORD = os.getenv("KAFKA_JOB_DB_PASSWORD", "saas_pass")


def safe_filename(value: str) -> str:
    cleaned = re.sub(r"[^a-zA-Z0-9._-]+", "_", value).strip("._")
    return cleaned[:80] or "barcode"


def generate_barcode(value: str, destination: Path) -> None:
    code = value.strip() if value and value.strip() else "EMPTY"
    Code128(code, writer=ImageWriter()).save(str(destination.with_suffix("")))
    logger.info("Generated barcode for %s at %s", code, destination)


def ensure_minio_bucket(minio_client: Minio, bucket_name: str) -> None:
    if not minio_client.bucket_exists(bucket_name):
        minio_client.make_bucket(bucket_name)
        logger.info("Created MinIO bucket %s", bucket_name)


def upload_barcode_to_minio(minio_client: Minio, bucket_name: str, file_path: Path) -> str:
    object_name = file_path.name
    minio_client.fput_object(
        bucket_name,
        object_name,
        str(file_path),
        content_type="image/png",
    )
    return f"minio://{bucket_name}/{object_name}"


def parse_job_db_url(url: str):
    parsed = urlparse(url.replace("jdbc:", ""))
    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 5432,
        "dbname": parsed.path.lstrip("/"),
        "user": JOB_DB_USER,
        "password": JOB_DB_PASSWORD,
    }


def load_input_text(job_id: str) -> str:
    if not job_id:
        return ""

    params = parse_job_db_url(JOB_DB_URL)
    try:
        with psycopg2.connect(**params) as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT input_text FROM jobs WHERE job_id = %s", (job_id,))
                result = cur.fetchone()
                if result and result[0]:
                    return str(result[0])
    except Exception as exc:  # pragma: no cover - runtime safety
        logger.warning("Could not load input text for jobId=%s from PostgreSQL: %s", job_id, exc)
    return ""


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    while True:
        try:
            consumer = KafkaConsumer(
                INPUT_TOPIC,
                bootstrap_servers=BOOTSTRAP_SERVERS,
                group_id=GROUP_ID,
                auto_offset_reset="earliest",
                enable_auto_commit=True,
                value_deserializer=lambda value: value.decode("utf-8"),
                consumer_timeout_ms=-1,
            )
            producer = KafkaProducer(
                bootstrap_servers=BOOTSTRAP_SERVERS,
                value_serializer=lambda value: json.dumps(value).encode("utf-8"),
            )
            break
        except Exception as error:
            logger.warning("Kafka is not ready (%s); retrying", error)
            time.sleep(5)

    minio_client = Minio(
        MINIO_ENDPOINT.replace("http://", "").replace("https://", ""),
        access_key=MINIO_ACCESS_KEY,
        secret_key=MINIO_SECRET_KEY,
        secure=MINIO_ENDPOINT.startswith("https://"),
    )
    ensure_minio_bucket(minio_client, MINIO_BUCKET)

    logger.info("Listening on %s via %s", INPUT_TOPIC, BOOTSTRAP_SERVERS)
    for record in consumer:
        try:
            payload = json.loads(record.value) if isinstance(record.value, str) else record.value
        except (TypeError, json.JSONDecodeError):
            payload = {"jobId": uuid.uuid4().hex, "inputText": record.value}

        job_id = str(payload.get("jobId") or uuid.uuid4().hex)
        text = str(payload.get("inputText") or load_input_text(job_id) or record.value or "")
        filename = f"{job_id}.png"
        destination = OUTPUT_DIR / filename

        try:
            generate_barcode(text, destination)
            object_url = upload_barcode_to_minio(minio_client, MINIO_BUCKET, destination)
            result = {
                "status": "completed",
                "filename": filename,
                "path": str(destination),
                "outputLocation": object_url,
                "inputTopic": INPUT_TOPIC,
                "partition": record.partition,
                "offset": record.offset,
            }
            producer.send(OUTPUT_TOPIC, result).get(timeout=10)
            logger.info("Generated barcode for job %s and uploaded to %s", job_id, object_url)
        except Exception as error:
            logger.exception("Could not generate barcode for job %s", job_id)
            producer.send(OUTPUT_TOPIC, {
                "status": "failed",
                "error": str(error),
                "partition": record.partition,
                "offset": record.offset,
            }).get(timeout=10)


if __name__ == "__main__":
    main()
