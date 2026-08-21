package com.example.kafkademo;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MinioService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioService(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey,
            @Value("${app.minio.bucket}") String bucket) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    public String getBucket() {
        return bucket;
    }

    public String presignedDownloadUrl(String objectName, int seconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(io.minio.http.Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(seconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate presigned MinIO URL for object=" + objectName, e);
        }
    }
}
