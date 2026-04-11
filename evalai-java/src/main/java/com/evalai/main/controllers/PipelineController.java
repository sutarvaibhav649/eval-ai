package com.evalai.main.controllers;

import com.evalai.grpc.HealthCheckRequest;
import com.evalai.grpc.HealthCheckResponse;
import com.evalai.grpc.PreprocessingServiceGrpc;
import com.evalai.main.dtos.response.CallbackPayload;
import com.evalai.main.dtos.request.PipelineStartRequestDTO;
import com.evalai.main.utils.BadRequestException;
import com.evalai.main.services.PipelineService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * FIX: Shared secret for callback authentication.
     * Set app.pipeline.callback-secret in application.properties.
     * Python must send this in the X-Callback-Secret header.
     *
     * Example application.properties:
     *   app.pipeline.callback-secret=your-strong-random-secret-here
     */
    @Value("${app.pipeline.callback-secret}")
    private String callbackSecret;

    private static final org.slf4j.Logger logger =
            LoggerFactory.getLogger(PipelineController.class);

    /*-------------------------------------------------------------------
                      START PIPELINE
     -------------------------------------------------------------------*/
    /**
     * Admin triggers evaluation pipeline for all pending answer sheets.
     */
    @PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> startPipeline(
            @Valid @RequestBody PipelineStartRequestDTO request
    ) throws BadRequestException {
        try {
            int queued = pipelineService.startPipeline(request.getExamId(),request.getSubjectId());

            return ResponseEntity.ok(Map.of(
                    "message", "Pipeline started successfully",
                    "examId", request.getExamId(),
                    "sheetsQueued", queued,
                    "status", "PROCESSING"
            ));

        } catch (RuntimeException e) {
            return switch (e.getMessage()) {
                case "EXAM_NOT_FOUND" ->
                        ResponseEntity.status(HttpStatus.NOT_FOUND).body("Exam not found");
                case "NO_QUESTION_PAPER_FOUND" ->
                        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("No question paper found — faculty must upload one first");
                case "NO_PENDING_SHEETS" ->
                        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body("No pending answer sheets found for this exam");
                default ->
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Pipeline failed to start: " + e.getMessage());
            };
        }
    }

    /*-------------------------------------------------------------------
                      CALLBACK FROM PYTHON
     -------------------------------------------------------------------*/
    /**
     * Receives OCR results callback from Python Celery worker.
     *
     * FIX: Added shared-secret authentication.
     * The endpoint is still permitAll() in SecurityConfig (no JWT needed
     * because Python doesn't have a user token), but we validate a
     * pre-shared secret header instead.
     *
     * In production, also restrict this to internal network/VPC.
     *
     * Python must include: X-Callback-Secret: <secret>
     *
     * @param secret   shared secret from X-Callback-Secret header
     * @param payload  OcrResponse from Python
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleCallback(
            @RequestHeader(value = "X-Callback-Secret", required = false) String secret,
            @RequestBody CallbackPayload payload
    ) {
        // FIX: Validate shared secret before processing
        if (secret == null || !secret.equals(callbackSecret)) {
            logger.warn("Callback rejected — invalid or missing X-Callback-Secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized callback");
        }

        try {
            logger.info("Callback received for task_id: {}", payload.getTaskId());
            pipelineService.handleCallback(payload);
            return ResponseEntity.ok(Map.of(
                    "message", "Callback processed successfully",
                    "taskId", payload.getTaskId()
            ));
        } catch (Exception e) {
            logger.error("Callback processing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Callback processing failed: " + e.getMessage());
        }
    }

    /*-------------------------------------------------------------------
                      C++ HEALTH CHECK
     -------------------------------------------------------------------*/
    @GetMapping("/cpp-health")
    public ResponseEntity<?> cppHealth() {
        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress("localhost", 50051)
                    .usePlaintext()
                    .build();

            PreprocessingServiceGrpc.PreprocessingServiceBlockingStub stub =
                    PreprocessingServiceGrpc.newBlockingStub(channel);

            HealthCheckRequest request = HealthCheckRequest.newBuilder()
                    .setService("evalai-cpp")
                    .build();

            HealthCheckResponse response = stub.healthCheck(request);
            channel.shutdown();

            return ResponseEntity.ok(Map.of(
                    "status", response.getStatus(),
                    "version", response.getVersion(),
                    "message", response.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("C++ service unreachable: " + e.getMessage());
        }
    }
}