package com.example.kafkademo;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class JobProducer {

    private static final Logger log = LoggerFactory.getLogger(JobProducer.class);

    public static final String PDF_JOBS_TOPIC = "pdf-jobs";
    public static final String PDF_COMPLETED_TOPIC = "pdf-completed";
    public static final String BARCODE_JOBS_TOPIC = "barcode-jobs";
    public static final String BARCODE_COMPLETED_TOPIC = "barcode-completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public JobProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishJob(Job job) {
        if ("TXT_TO_PDF".equalsIgnoreCase(job.getJobType())) {
            publishPdfJob(job);
            return;
        }
        if ("BARCODE_GENERATOR".equalsIgnoreCase(job.getJobType())) {
            publishBarcodeJob(job);
            return;
        }
        throw new IllegalArgumentException("Unsupported job type: " + job.getJobType());
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void publishPdfJob(Job job) {
        Map<String, Object> payload = Map.of(
                "jobId", job.getJobId(),
                "userId", job.getUserId(),
                "jobType", job.getJobType());
        try {
            kafkaTemplate.send(PDF_JOBS_TOPIC, job.getJobId(), payload).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to publish pdf job for jobId=" + job.getJobId(), e);
        }
        log.info("Published pdf job message for jobId={}", job.getJobId());
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void publishBarcodeJob(Job job) {
        Map<String, Object> payload = Map.of(
                "jobId", job.getJobId(),
                "userId", job.getUserId(),
                "jobType", job.getJobType());
        try {
            kafkaTemplate.send(BARCODE_JOBS_TOPIC, job.getJobId(), payload).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to publish barcode job for jobId=" + job.getJobId(), e);
        }
        log.info("Published barcode job message for jobId={}", job.getJobId());
    }

    @Retryable(retryFor = Exception.class, maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public void publishCompletion(Job job) {
        Map<String, Object> payload = Map.of(
                "jobId", job.getJobId(),
                "userId", job.getUserId(),
                "status", job.getStatus().name());
        String topic;
        if ("TXT_TO_PDF".equalsIgnoreCase(job.getJobType())) {
            topic = PDF_COMPLETED_TOPIC;
        } else if ("BARCODE_GENERATOR".equalsIgnoreCase(job.getJobType())) {
            topic = BARCODE_COMPLETED_TOPIC;
        } else {
            throw new IllegalArgumentException("Unsupported job type: " + job.getJobType());
        }
        try {
            kafkaTemplate.send(topic, job.getJobId(), payload).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to publish completion event for jobId=" + job.getJobId(), e);
        }
        log.info("Published completion event for jobId={} on topic={}", job.getJobId(), topic);
    }

    @Recover
    public void recover(Exception e, Job job) {
        log.error("Kafka publish failed for jobId={} after retries: {}", job.getJobId(), e.getMessage(), e);
        throw new IllegalStateException("Kafka publish failed for jobId=" + job.getJobId(), e);
    }
}
