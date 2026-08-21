FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY consumer.py .

ENV KAFKA_BOOTSTRAP_SERVERS=kafka:29092
ENV KAFKA_INPUT_TOPIC=demo-messages
ENV KAFKA_OUTPUT_TOPIC=pdf-results
ENV KAFKA_GROUP_ID=txttopdf-converter
ENV OUTPUT_DIR=/output

CMD ["python", "consumer.py"]
