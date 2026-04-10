package com.evalai.main.services;


import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.evalai.grpc.CleanedImagePage;
import com.evalai.grpc.PageStatus;
import com.evalai.grpc.PreprocessRequest;
import com.evalai.grpc.PreprocessResponse;
import com.evalai.grpc.PreprocessingServiceGrpc;
import com.evalai.grpc.RawImagePage;
import com.evalai.main.entities.AnswersheetEntity;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * FIX: was creating a new ManagedChannel on every single call.
 * gRPC channels are expensive — designed to be long-lived and shared.
 *
 * Fix: Create ONE channel at startup (@PostConstruct),
 *      reuse it for all calls, shut it down cleanly on app stop (@PreDestroy).
 */
@Service
public class GrpcService {

    private static final Logger logger = LoggerFactory.getLogger(GrpcService.class);

    @Value("${app.grpc.host}")
    private String grpcHost;

    @Value("${app.grpc.port}")
    private int grpcPort;

    @Value("${app.pipeline.skip-cpp}")
    private boolean skipCpp;

    @Value("${app.upload.base-path}")
    private String uploadBasePath;

    /**
     * FIX: Single shared channel, created once at startup.
     * Thread-safe — gRPC channels are designed for concurrent use.
     */
    private ManagedChannel sharedChannel;

    /**
     * FIX: Initialize channel once at startup instead of per-request.
     */
    @PostConstruct
    public void initChannel() {
        if (!skipCpp) {
            sharedChannel = ManagedChannelBuilder
                    .forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .build();
            logger.info("gRPC channel initialized → {}:{}", grpcHost, grpcPort);
        }
    }

    /**
     * FIX: Gracefully shut down the shared channel on app stop.
     */
    @PreDestroy
    public void destroyChannel() {
        if (sharedChannel != null && !sharedChannel.isShutdown()) {
            try {
                sharedChannel.shutdown();
                if (!sharedChannel.awaitTermination(5, TimeUnit.SECONDS)) {
                    sharedChannel.shutdownNow();
                }
                logger.info("gRPC channel shut down cleanly");
            } catch (InterruptedException e) {
                sharedChannel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Sends raw page images to C++ for preprocessing.
     *
     * When skip-cpp=true: returns raw image paths directly.
     * When skip-cpp=false: calls C++ via gRPC for image enhancement.
     */
    public List<String> preprocessImages(
            AnswersheetEntity answerSheet,
            String taskId,
            List<String> rawImagePaths
    ) {
        if (skipCpp) {
            logger.warn(
                    "skip-cpp=true: Skipping C++ preprocessing for task {}. Using raw images.",
                    taskId
            );
            return rawImagePaths;
        }

        return callCppPreprocessing(answerSheet, taskId, rawImagePaths);
    }

    /**
     * FIX: Uses sharedChannel instead of creating new channel per call.
     */
    private List<String> callCppPreprocessing(
            AnswersheetEntity answerSheet,
            String taskId,
            List<String> rawImagePaths
    ) {
        try {
            // FIX: Use sharedChannel — no more new channel per request
            PreprocessingServiceGrpc.PreprocessingServiceBlockingStub stub =
                    PreprocessingServiceGrpc.newBlockingStub(sharedChannel)
                            .withDeadlineAfter(120, TimeUnit.SECONDS);

            String absoluteUploadPath = java.nio.file.Paths.get(uploadBasePath)
                    .toAbsolutePath()
                    .normalize()
                    .toString();

            logger.info("Sending absolute upload path to C++: {}", absoluteUploadPath);

            PreprocessRequest.Builder requestBuilder = PreprocessRequest.newBuilder()
                    .setTaskId(taskId)
                    .setExamId(answerSheet.getExam().getId())
                    .setStudentId(answerSheet.getStudent().getId())
                    .setAnswerSheetId(answerSheet.getId())
                    .setOutputBasePath(absoluteUploadPath);

            for (int i = 0; i < rawImagePaths.size(); i++) {
                RawImagePage page = RawImagePage.newBuilder()
                        .setPageNumber(i + 1)
                        .setImagePath(rawImagePaths.get(i))
                        .build();
                requestBuilder.addPages(page);
            }

            PreprocessResponse response = stub.preprocessStudentImages(requestBuilder.build());

            if (response.getPagesList().isEmpty()) {
                logger.error("C++ returned empty response — fallback to raw images");
                return rawImagePaths;
            }

            if (!response.getPagesList().isEmpty()) {
                boolean anonymized = response.getPages(0).getAnonymized();
                if (!anonymized) {
                    throw new RuntimeException(
                            "ANONYMIZATION_FAILED: C++ did not anonymize page 1"
                    );
                }
            }

            List<String> cleanedPaths = response.getPagesList().stream()
                    .filter(p -> p.getStatus() == PageStatus.PAGE_STATUS_SUCCESS)
                    .map(CleanedImagePage::getCleanedPath)
                    .collect(Collectors.toList());

            if (cleanedPaths.isEmpty()) {
                logger.warn("No pages processed by C++, using raw images");
                return rawImagePaths;
            }

            logger.info("C++ preprocessing complete for task {} | {} pages cleaned",
                    taskId, cleanedPaths.size());

            return cleanedPaths;

        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                logger.error("C++ timeout — falling back to raw images");
            } else {
                logger.error("C++ gRPC error — falling back: {}", ex.getMessage());
            }
            return rawImagePaths;

        } catch (Exception ex) {
            logger.error("C++ unexpected error — falling back: {}", ex.getMessage());
            return rawImagePaths;
        }
        // FIX: No finally block needed — we don't own the channel lifecycle here
    }
}
