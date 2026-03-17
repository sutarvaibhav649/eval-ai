package com.evalai.main.services;


import java.util.List;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Handles gRPC communication with the C++ preprocessing service.
 * Sends raw page images to C++ for enhancement, deskewing, and anonymization.
 * Receives cleaned image paths in response.
 *
 * When app.pipeline.skip-cpp=true, skips gRPC entirely and returns
 * raw image paths directly — used for local development without C++.
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@Service
public class GrpcService {
	@Value("${app.grpc.host}")
    private String grpcHost;

    @Value("${app.grpc.port}")
    private int grpcPort;

    @Value("${app.pipeline.skip-cpp}")
    private boolean skipCpp;

    @Value("${app.upload.base-path}")
    private String uploadBasePath;

    private static final Logger logger = LoggerFactory.getLogger(GrpcService.class);

    /**
     * Sends raw page images to C++ for preprocessing.
     *
     * When skip-cpp=true (current — C++ not built yet):
     * - Returns raw image paths directly
     * - Allows full pipeline testing without C++ running
     *
     * When skip-cpp=false (after evalai-cpp is built):
     * - Will call C++ via gRPC for image enhancement and deskewing
     *
     * @param answerSheet   the student's answer sheet
     * @param taskId        unique task ID for this job
     * @param rawImagePaths list of raw image paths from PDF splitting
     * @return list of image paths ready for OCR
     */
    public List<String> preprocessImages(
            AnswersheetEntity answerSheet,
            String taskId,
            List<String> rawImagePaths
    ) {
        if (skipCpp) {
            logger.warn(
                "skip-cpp=true: Skipping C++ preprocessing for task {}. " +
                "Using raw images directly.",
                taskId
            );
            return rawImagePaths;
        }

        return callCppPreprocessing(answerSheet, taskId, rawImagePaths);
    }

    /**
     * Makes the actual gRPC call to C++ preprocessing service.
     */
    private List<String> callCppPreprocessing(
            AnswersheetEntity answerSheet,
            String taskId,
            List<String> rawImagePaths
    ) {
        ManagedChannel channel = null;
        try {
            // Step 1 — Create gRPC channel
            channel = ManagedChannelBuilder
                    .forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .build();

            // Step 2 — Create blocking stub
            PreprocessingServiceGrpc.PreprocessingServiceBlockingStub stub =
                    PreprocessingServiceGrpc.newBlockingStub(channel);

            // Step 3 — Build request
            PreprocessRequest.Builder requestBuilder = PreprocessRequest.newBuilder()
                    .setTaskId(taskId)
                    .setExamId(answerSheet.getExam().getId())
                    .setStudentId(answerSheet.getStudent().getId())
                    .setAnswerSheetId(answerSheet.getId())
                    .setOutputBasePath(uploadBasePath);

            // Add each raw page
            for (int i = 0; i < rawImagePaths.size(); i++) {
                RawImagePage page = RawImagePage.newBuilder()
                        .setPageNumber(i + 1)
                        .setImagePath(rawImagePaths.get(i))
                        .build();
                requestBuilder.addPages(page);
            }

            // Step 4 — Call C++ service
            PreprocessResponse response = stub.preprocessStudentImages(
                    requestBuilder.build()
            );

            // Step 5 — Verify anonymization on page 1
            if (!response.getPagesList().isEmpty()) {
                boolean anonymized = response.getPages(0).getAnonymized();
                if (!anonymized) {
                    throw new RuntimeException(
                        "ANONYMIZATION_FAILED: C++ did not anonymize page 1"
                    );
                }
            }

            // Step 6 — Extract cleaned image paths
            List<String> cleanedPaths = response.getPagesList().stream()
                    .filter(p -> p.getStatus() == PageStatus.PAGE_STATUS_SUCCESS)
                    .map(CleanedImagePage::getCleanedPath)
                    .collect(java.util.stream.Collectors.toList());

            logger.info(
                "C++ preprocessing complete for task {} | {} pages cleaned",
                taskId, cleanedPaths.size()
            );

            return cleanedPaths;

        } finally {
            if (channel != null) {
                channel.shutdown();
            }
        }
    }
}
