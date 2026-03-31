package com.evalai.main.utils;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(exception = Exception.class)
	public ResponseEntity<?> handleGeneric(Exception exception){
		return ResponseEntity
                .status(500)
                .body("Internal Server Error: " + exception.getMessage());
	}
	
	@ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        return ResponseEntity
                .status(400)
                .body("Bad Request: " + ex.getMessage());
    }	
}
