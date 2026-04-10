package com.evalai.main.dtos.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * FIX: Typed DTO for Python /embeddings/generate response.
 *
 * Previously FacultyService used raw Map and cast to List<Double>
 * which risks ClassCastException if Python response shape changes.
 * This DTO provides compile-time safety and clear error messages.
 */
@Getter
@Setter
public class EmbeddingResponseDTO {

    @JsonProperty("text")
    private String text;

    @JsonProperty("embedding")
    private List<Double> embedding;

    @JsonProperty("dimensions")
    private Integer dimensions;
}
