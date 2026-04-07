package com.evalai.main.dtos;

import java.util.List;

import com.evalai.main.entities.AnswersheetEntity;

public record ProcessingContext(
        String taskId,
        AnswersheetEntity answerSheet,
        List<String> rawImagePaths
) {}
