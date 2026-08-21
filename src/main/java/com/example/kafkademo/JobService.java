package com.example.kafkademo;

import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobProducer jobProducer;

    public JobService(JobRepository jobRepository, JobProducer jobProducer) {
        this.jobRepository = jobRepository;
        this.jobProducer = jobProducer;
    }

    @Transactional
    public JobResponse createJob(String userId, String inputText, String jobType) {
        Job job = new Job();
        job.setUserId(userId);
        job.setInputText(inputText);
        job.setJobType(jobType);
        job.setStatus(JobStatus.QUEUED);
        job.setCreatedAt(Instant.now());
        job = jobRepository.save(job);

        jobProducer.publishPdfJob(job);
        return JobResponse.from(job);
    }

    public Optional<Job> findByJobId(String jobId) {
        return jobRepository.findByJobId(jobId);
    }

    @Transactional
    public void markProcessing(String jobId) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);
        });
    }

    @Transactional
    public void markCompleted(String jobId, String outputLocation) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.COMPLETED);
            job.setOutputLocation(outputLocation);
            jobRepository.save(job);
            jobProducer.publishCompletion(job);
            log.info("Job {} completed and completion event published", jobId);
        });
    }

    @Transactional
    public void markFailed(String jobId, String errorMessage) {
        jobRepository.findByJobId(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            jobRepository.save(job);
            log.error("Job {} failed: {}", jobId, errorMessage);
        });
    }
}
