package com.example.kafkademo;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
class JobControllerUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobService jobService;

    @Test
    void uploadTextFileCreatesJob() throws Exception {
        JobResponse response = new JobResponse(
                "job-123",
                "user-123",
                "TXT_TO_PDF",
                JobStatus.QUEUED,
                null,
                Instant.now());

        when(jobService.createJob(anyString(), anyString(), anyString())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello world\nthis is a test".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/jobs/upload")
                        .file(file)
                        .param("userId", "user-123")
                        .param("jobType", "TXT_TO_PDF"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(Matchers.containsString("job-123")))
                .andExpect(content().string(Matchers.containsString("user-123")))
                .andExpect(content().string(Matchers.containsString("TXT_TO_PDF")));
    }
}
