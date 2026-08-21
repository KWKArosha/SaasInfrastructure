package com.example.kafkademo;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class PdfJobConsumerTest {

    @Mock
    private JobService jobService;

    private PdfJobConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new PdfJobConsumer(jobService);
    }

    @Test
    void consumeHandlesBarcodeGeneratorJobType() {
        String jobId = "job-456";
        String userId = "user-456";
        Job job = new Job();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setJobType("BARCODE_GENERATOR");

        when(jobService.findByJobId(jobId)).thenReturn(Optional.of(job));

        consumer.consumeBarcode(
                Map.of("jobId", jobId, "userId", userId, "jobType", "BARCODE_GENERATOR"),
                jobId,
                JobProducer.BARCODE_JOBS_TOPIC);

        verify(jobService).markProcessing(jobId);
        verify(jobService).markCompleted(jobId, "minio://pdf-bucket/" + jobId + ".png");
    }
}
