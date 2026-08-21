package com.example.kafkademo;

public record JobEvent(
        String jobId,
        String userId,
        String jobType,
        String status,
        String outputLocation,
        String errorMessage) {
}
