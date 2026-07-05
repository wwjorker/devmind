package com.devmind.module.search.vectorstore;

import java.util.Map;

/**
 * Bridges the sparse-friendly {@code Map<String, Double>} embedding representation and the
 * fixed-dimension {@code float[]} that pgvector expects. Dense embeddings are keyed by
 * their component index ("0", "1", ...), so the conversion is a straight re-layout.
 */
public final class DenseVectorCodec {

    private DenseVectorCodec() {
    }

    public static float[] toFloatArray(Map<String, Double> vector, int dimension) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("dense vector is empty");
        }
        if (vector.size() != dimension) {
            throw new IllegalArgumentException(
                    "dense vector dimension mismatch: expected " + dimension + " but got " + vector.size()
                            + "; align devmind.vector-store.pgvector.dimension with the embedding provider output");
        }
        float[] values = new float[dimension];
        for (Map.Entry<String, Double> entry : vector.entrySet()) {
            int index = parseIndex(entry.getKey(), dimension);
            values[index] = entry.getValue() == null ? 0f : entry.getValue().floatValue();
        }
        return values;
    }

    public static String toVectorLiteral(float[] values) {
        StringBuilder builder = new StringBuilder(values.length * 10 + 2);
        builder.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.append(']').toString();
    }

    private static int parseIndex(String key, int dimension) {
        int index;
        try {
            index = Integer.parseInt(key);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "dense vector key is not a component index: " + key
                            + "; sparse vectors cannot be stored in pgvector", ex);
        }
        if (index < 0 || index >= dimension) {
            throw new IllegalArgumentException("dense vector index out of range: " + index);
        }
        return index;
    }
}
