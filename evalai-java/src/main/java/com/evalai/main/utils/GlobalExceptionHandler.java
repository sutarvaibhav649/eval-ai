package com.evalai.main.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.evalai.main.dtos.ApiResponse;

/**
 * FIX: RuntimeException was returning HTTP 400 BAD REQUEST for ALL cases.
 * Internal errors (NullPointerException, DB errors) would mislead clients.
 *
 * Fix strategy:
 *  - BadRequestException    → 400 (domain validation failures)
 *  - Known RuntimeException → 400 only for known business error codes
 *  - Unknown RuntimeException → 500 (unexpected server errors)
 *  - Exception (catch-all)  → 500
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles explicit domain validation failures.
     * These are intentional 400s thrown by service layer.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(buildResponse(false, ex.getMessage()));
    }

    /**
     * FIX: Split RuntimeException handling.
     *
     * Known business error codes (thrown intentionally from service layer)
     * map to 400 or appropriate 4xx.
     * Everything else is a genuine server error → 500.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntime(RuntimeException ex) {
        String msg = ex.getMessage();

        // Known business rule violations — intentional 400s
        if (msg != null && isKnownBusinessError(msg)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(buildResponse(false, msg));
        }

        // Known not-found errors → 404
        if (msg != null && isNotFoundError(msg)) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(buildResponse(false, msg));
        }

        // Everything else is an unexpected server error
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildResponse(false, "Internal server error: " + msg));
    }

    /**
     * Catch-all for checked exceptions and anything else.
     * Always 500 — never expose raw exception messages in production.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildResponse(false, "An unexpected error occurred: " + ex.getMessage()));
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private boolean isKnownBusinessError(String msg) {
        return msg.equals("EXAM_ALREADY_EXISTS")
                || msg.equals("GRIEVANCE_ALREADY_CLOSED")
                || msg.equals("GRIEVANCE_ALREADY_EXISTS")
                || msg.equals("GRIEVANCE_DEADLINE_PASSED")
                || msg.equals("UNAUTHORIZED_GRIEVANCE")
                || msg.equals("RESULTS_NOT_READY")
                || msg.equals("ANSWERSHEET_NOT_READY")
                || msg.equals("NO_PENDING_SHEETS")
                || msg.equals("NO_QUESTION_PAPER_FOUND")
                || msg.equals("FILE_STUDENT_COUNT_MISMATCH")
                || msg.equals("ONLY_PDF_ALLOWED");
    }

    private boolean isNotFoundError(String msg) {
        return msg.equals("EXAM_NOT_FOUND")
                || msg.equals("STUDENT_NOT_FOUND")
                || msg.equals("ANSWER_SHEET_NOT_FOUND")
                || msg.equals("RESULT_NOT_FOUND")
                || msg.equals("FEEDBACK_NOT_AVAILABLE")
                || msg.startsWith("SUBJECT_NOT_FOUND")
                || msg.startsWith("STUDENT_NOT_FOUND:");
    }

    private ApiResponse<Object> buildResponse(boolean success, String message) {
        return ApiResponse.builder()
                .success(success)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
