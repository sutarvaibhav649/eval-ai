package com.evalai.main.services;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class VectorConverter implements AttributeConverter<float[], String> {

    /**
     * Converts float[] to PostgreSQL vector string format: [0.1,0.2,0.3]
     */
    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attribute.length; i++) {
            sb.append(attribute[i]);
            if (i < attribute.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Converts PostgreSQL vector string back to float[]
     */
    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String cleaned = dbData.replace("[", "").replace("]", "").trim();
        String[] parts = cleaned.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}