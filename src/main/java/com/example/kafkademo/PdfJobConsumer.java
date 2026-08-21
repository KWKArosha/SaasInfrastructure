package com.example.kafkademo;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PdfJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(PdfJobConsumer.class);

    private final JobService jobService;

    public PdfJobConsumer(JobService jobService) {
        this.jobService = jobService;
    }

    @KafkaListener(
            topics = JobProducer.PDF_JOBS_TOPIC,
            groupId = "pdf-job-workers",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(@Payload Map<String, Object> payload,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        String jobId = String.valueOf(payload.get("jobId"));
        String userId = String.valueOf(payload.get("userId"));
        String jobType = String.valueOf(payload.get("jobType"));
        log.info("Received job event for jobId={}, userId={}, jobType={}, topic={}", jobId, userId, jobType, topic);

        try {
            jobService.markProcessing(jobId);
            jobService.findByJobId(jobId).ifPresentOrElse(job -> {
                if ("TXT_TO_PDF".equalsIgnoreCase(job.getJobType())) {
                    String outputLocation = "minio://pdf-bucket/" + jobId + ".pdf";
                    jobService.markCompleted(jobId, outputLocation);
                    log.info("Completed PDF job {} stored at {}", jobId, outputLocation);
                } else {
                    throw new IllegalArgumentException("Unsupported job type: " + jobType);
                }
            }, () -> log.warn("No job found for jobId={}", jobId));
        } catch (Exception ex) {
            jobService.markFailed(jobId, ex.getMessage());
            log.error("Failed processing jobId={}", jobId, ex);
        }
    }
}
