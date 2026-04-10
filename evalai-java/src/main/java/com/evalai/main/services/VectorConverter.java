package com.evalai.main.services;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * FIX: convertToDatabaseColumn used float.toString() which produces scientific
 * notation (e.g., 1.5E-4) for very small values.
 * PostgreSQL's vector type REJECTS scientific notation — this would cause
 * silent data corruption or DB errors for embeddings near zero.
 *
 * Fix: use String.format("%.8f", value) to always produce decimal notation.
 */
@Converter
public class VectorConverter implements AttributeConverter<float[], String> {

    /**
     * Converts float[] → PostgreSQL vector string: [0.12345678,0.00000015,...]
     *
     * FIX: was sb.append(attribute[i]) which may produce "1.5E-4"
     *      pgvector rejects scientific notation — use fixed decimal format.
     */
    @Override
    public String convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) return null;

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < attribute.length; i++) {
            // FIX: %.8f guarantees decimal notation, never scientific notation
            sb.append(String.format("%.8f", attribute[i]));
            if (i < attribute.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Converts PostgreSQL vector string → float[]
     * No changes needed here — parseFloat handles both notations.
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