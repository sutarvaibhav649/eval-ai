package com.evalai.main.services;


import java.util.List;
import java.util.concurrent.TimeUnit;

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
            channel = ManagedChannelBuilder
                    .forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .build();

            PreprocessingServiceGrpc.PreprocessingServiceBlockingStub stub =
                    PreprocessingServiceGrpc.newBlockingStub(channel).withDeadlineAfter(120,TimeUnit.SECONDS);

            // Convert to absolute path — C++ needs absolute path
            // ../upload relative to Java becomes D:\EvalAI\ upload absolute
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
                    .setOutputBasePath(absoluteUploadPath); // ← absolute path

            for (int i = 0; i < rawImagePaths.size(); i++) {
                RawImagePage page = RawImagePage.newBuilder()
                        .setPageNumber(i + 1)
                        .setImagePath(rawImagePaths.get(i))
                        .build();
                requestBuilder.addPages(page);
            }

            PreprocessResponse response = stub.preprocessStudentImages(
                    requestBuilder.build()
            );
            
            if (response.getPagesList().isEmpty()) {
                logger.error("C++ returned empty response — fallback");
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
                    .collect(java.util.stream.Collectors.toList());
            
            if (cleanedPaths.isEmpty()) {
                logger.warn("No pages processed by C++, using raw images");
                return rawImagePaths;
            }

            logger.info(
                "C++ preprocessing complete for task {} | {} pages cleaned",
                taskId, cleanedPaths.size()
            );
            
//            logger.error("C++ preprocessing failed for task {}. Falling back.", taskId);

            return cleanedPaths;

        }catch (StatusRuntimeException ex) {
        		if (ex.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                logger.error("C++ timeout — falling back to raw images");
            } else {
                logger.error("C++ error — falling back: {}", ex.getMessage());
            }

            return rawImagePaths;
	           
		} finally {
			try {
			    if (!channel.awaitTermination(3, TimeUnit.SECONDS)) {
			        channel.shutdownNow();
			    }
			} catch (InterruptedException e) {
			    channel.shutdownNow();
			    Thread.currentThread().interrupt();
			}
        }
    }
}
