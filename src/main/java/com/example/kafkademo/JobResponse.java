package com.example.kafkademo;

import java.time.Instant;

public record JobResponse(
        String jobId,
        String userId,
        String jobType,
        JobStatus status,
        String outputLocation,
        Instant createdAt) {

    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getJobId(),
                job.getUserId(),
                job.getJobType(),
                job.getStatus(),
                job.getOutputLocation(),
                job.getCreatedAt());
    }
}
