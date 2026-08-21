package com.example.kafkademo;

import jakarta.validation.constraints.NotBlank;

public record CreateJobRequest(
        @NotBlank(message = "userId is required") String userId,
        @NotBlank(message = "inputText is required") String inputText,
        String jobType) {

    public String jobTypeOrDefault() {
        return jobType == null || jobType.isBlank() ? "TXT_TO_PDF" : jobType;
    }
}
