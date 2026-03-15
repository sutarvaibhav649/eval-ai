package com.evalai.main.services;


import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.evalai.main.entities.AnswersheetEntity;
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
                "Using raw images directly — not suitable for production.",
                taskId
            );
            return rawImagePaths;
        }

        // TODO: Enable after evalai-cpp is built and proto classes are generated
        // return callCppPreprocessing(answerSheet, taskId, rawImagePaths);
        throw new RuntimeException(
            "C++ gRPC service not yet implemented. Set app.pipeline.skip-cpp=true"
        );
    }
}
