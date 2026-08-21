package com.example.kafkademo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, java.util.UUID> {

    Optional<Job> findByJobId(String jobId);
}
