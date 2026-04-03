package com.evalai.main.controllers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.coyote.BadRequestException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.evalai.grpc.HealthCheckRequest;
import com.evalai.grpc.HealthCheckResponse;
import com.evalai.grpc.PreprocessingServiceGrpc;
import com.evalai.main.dtos.request.PipelineStartRequestDTO;
import com.evalai.main.dtos.response.CallbackPayload;
import com.evalai.main.services.PipelineService;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Handles pipeline trigger and Python callback endpoints.
 *
 * Endpoints:
 * POST /pipeline/start      → Admin triggers evaluation for an exam
 * POST /pipeline/callback   → Python sends OCR results back to Java
 *
 * @author Vaibhav Sutar
 * @version 1.0
 */
@RestController
@RequestMapping("/pipeline")
@RequiredArgsConstructor
public class PipelineController {
	private final PipelineService pipelineService;

    @Value("${app.grpc.host}")
    private String grpcHost;

    @Value("${app.grpc.port}")
    private int grpcPort;

    @Value("${app.grpc.deadline-seconds}")
    private long grpcDeadlineSeconds;
	
	
	/**
     * Admin triggers evaluation pipeline for all pending answer sheets in an exam.
     * Requires ADMIN role.
     *
     * @return 200 OK with count of sheets queued
	 * @throws BadRequestException 
     */
	@PostMapping("/start")
    @PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<?> startPipeline(
            @Valid @RequestBody PipelineStartRequestDTO request
    ) throws BadRequestException {
        try {
            int queued = pipelineService.startPipeline(request.getExamId());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Pipeline started successfully");
            response.put("examId", request.getExamId());
            response.put("sheetsQueued", queued);
            response.put("status", "PROCESSING");

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return switch (e.getMessage()) {
                case "EXAM_NOT_FOUND" ->
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("Exam not found");
                case "NO_QUESTION_PAPER_FOUND" ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("No question paper found for this exam — "
                                    + "faculty must upload question paper first");
                case "NO_PENDING_SHEETS" ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("No pending answer sheets found for this exam");
                default ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Pipeline failed to start: " + e.getMessage());
            };
        }
    }
	
	/**
     * Receives OCR results callback from Python Celery worker.
     * No auth required — internal service-to-service communication.
     * Should be restricted to internal network in production.
     *
     * @param payload OcrResponse from Python matching CallbackPayload schema
     * @return 200 OK when results saved successfully
     */
    @PostMapping("/callback")
    public ResponseEntity<?> handleCallback(
            @RequestBody CallbackPayload payload
    ) {
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
    
    @GetMapping("/cpp-health")
    public ResponseEntity<?> cppHealth() {
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(grpcHost, grpcPort)
                    .usePlaintext()
                    .build();

            PreprocessingServiceGrpc.PreprocessingServiceBlockingStub stub =
                    PreprocessingServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(grpcDeadlineSeconds, TimeUnit.SECONDS);

            HealthCheckRequest request = HealthCheckRequest.newBuilder()
                    .setService("evalai-cpp")
                    .build();

            HealthCheckResponse response = stub.healthCheck(request);

            return ResponseEntity.ok(Map.of(
                    "status", response.getStatus(),
                    "version", response.getVersion(),
                    "message", response.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("C++ service unreachable: " + e.getMessage());
        } finally {
            if (channel != null) {
                channel.shutdown();
            }
        }
    }
    private static final org.slf4j.Logger logger =
            LoggerFactory.getLogger(PipelineController.class);
}
