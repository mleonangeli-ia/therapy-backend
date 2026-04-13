package com.therapy.audio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;

/**
 * Unified storage service.
 * - In production (AWS_REGION set + S3 buckets configured): uses Amazon S3
 * - In development (no credentials / LOCAL_STORAGE=true): stores files under /tmp/therapy-storage/
 */
@Slf4j
@Service
public class StorageService {

    @Value("${aws.region:us-east-1}")
    private String region;

    @Value("${aws.s3.bucket-audio:therapy-audio-dev}")
    private String audioBucket;

    @Value("${aws.s3.bucket-reports:therapy-reports-dev}")
    private String reportsBucket;

    @Value("${storage.local:true}")
    private boolean useLocalStorage;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private Path localBaseDir;

    @PostConstruct
    void init() {
        if (useLocalStorage) {
            localBaseDir = Path.of(System.getProperty("java.io.tmpdir"), "therapy-storage");
            try {
                Files.createDirectories(localBaseDir.resolve("audio"));
                Files.createDirectories(localBaseDir.resolve("reports"));
                log.info("Using LOCAL storage at {}", localBaseDir);
            } catch (IOException e) {
                throw new RuntimeException("Cannot create local storage dirs", e);
            }
        } else {
            Region awsRegion = Region.of(region);
            s3Client = S3Client.builder()
                    .region(awsRegion)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            s3Presigner = S3Presigner.builder()
                    .region(awsRegion)
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            log.info("Using S3 storage — audio: {}, reports: {}", audioBucket, reportsBucket);
        }
    }

    // ── Audio ──────────────────────────────────────────────────────────────

    public String storeAudio(byte[] bytes, String mimeType, UUID patientId, UUID sessionId) {
        String ext = mimeType != null && mimeType.contains("mp4") ? "mp4" : "webm";
        String key = "audio/%s/%s/%s.%s".formatted(patientId, sessionId, UUID.randomUUID(), ext);
        store(bytes, key, mimeType != null ? mimeType : "audio/webm", audioBucket);
        return key;
    }

    public String storeTtsAudio(byte[] bytes, UUID sessionId, UUID messageId) {
        String key = "responses/%s/%s.mp3".formatted(sessionId, messageId);
        store(bytes, key, "audio/mpeg", audioBucket);
        return key;
    }

    public String getAudioUrl(String key, Duration expiry) {
        return getUrl(key, audioBucket, expiry);
    }

    // ── Reports ────────────────────────────────────────────────────────────

    public String storeReport(byte[] pdfBytes, UUID patientId, UUID sessionId) {
        String key = "reports/%s/%s/report.pdf".formatted(patientId, sessionId);
        store(pdfBytes, key, "application/pdf", reportsBucket);
        return key;
    }

    public String getReportUrl(String key, Duration expiry) {
        return getUrl(key, reportsBucket, expiry);
    }

    public byte[] getReportBytes(String key) {
        if (useLocalStorage) {
            try {
                return Files.readAllBytes(localBaseDir.resolve(key));
            } catch (IOException e) {
                throw new RuntimeException("Cannot read local file: " + key, e);
            }
        }
        try (InputStream is = s3Client.getObject(
                GetObjectRequest.builder().bucket(reportsBucket).key(key).build())) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Cannot read S3 object: " + key, e);
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private void store(byte[] bytes, String key, String contentType, String bucket) {
        if (useLocalStorage) {
            try {
                Path target = localBaseDir.resolve(key);
                Files.createDirectories(target.getParent());
                Files.write(target, bytes);
            } catch (IOException e) {
                throw new RuntimeException("Cannot write local file: " + key, e);
            }
        } else {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                            .build(),
                    RequestBody.fromBytes(bytes)
            );
        }
        log.debug("Stored {} bytes at {}", bytes.length, key);
    }

    private String getUrl(String key, String bucket, Duration expiry) {
        if (useLocalStorage) {
            // Serve via /api/v1/audio/file?key=... in dev
            return baseUrl + "/api/v1/audio/file?key=" + key;
        }
        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(expiry)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                        .build()
        ).url().toString();
    }
}
