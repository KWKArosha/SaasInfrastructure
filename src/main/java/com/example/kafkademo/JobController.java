package com.example.kafkademo;

import jakarta.validation.Valid;
import java.util.Optional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobService jobService;
    private final MinioService minioService;

    public JobController(JobService jobService, MinioService minioService) {
        this.jobService = jobService;
        this.minioService = minioService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobResponse response = jobService.createJob(request.userId(), request.inputText(), request.jobTypeOrDefault());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping(value = "/jobs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> uploadTextFile(
            @RequestParam("userId") String userId,
            @RequestParam(value = "jobType", required = false) String jobType,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String resolvedJobType = (jobType == null || jobType.isBlank()) ? "TXT_TO_PDF" : jobType;
        JobResponse response = jobService.createJob(userId, content, resolvedJobType);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<Map<String, String>> download(@PathVariable String jobId) {
        Optional<Job> job = jobService.findByJobId(jobId);
        if (job.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String outputLocation = job.get().getOutputLocation();
        if (outputLocation == null || outputLocation.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String objectName = outputLocation.replace("minio://" + minioService.getBucket() + "/", "");
        String downloadUrl = "/api/jobs/" + jobId + "/file";
        return ResponseEntity.ok(Map.of(
                "url", downloadUrl,
                "fileName", objectName,
                "jobId", jobId));
    }

    @GetMapping("/jobs/{jobId}/file")
    public ResponseEntity<Resource> file(@PathVariable String jobId) {
        Optional<Job> job = jobService.findByJobId(jobId);
        if (job.isEmpty() || job.get().getOutputLocation() == null || job.get().getOutputLocation().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        String objectName = job.get().getOutputLocation().replace("minio://" + minioService.getBucket() + "/", "");
        String contentType = objectName.endsWith(".png") ? "image/png" : "application/pdf";
        Resource resource = new InputStreamResource(minioService.getObject(objectName));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "attachment; filename=\"" + objectName + "\"")
                .body(resource);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable String jobId) {
        Optional<Job> job = jobService.findByJobId(jobId);
        return job.map(value -> ResponseEntity.ok(JobResponse.from(value)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
