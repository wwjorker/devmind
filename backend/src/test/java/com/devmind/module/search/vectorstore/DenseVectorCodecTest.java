package com.devmind.module.search.vectorstore;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DenseVectorCodecTest {

    @Test
    void toFloatArrayShouldLayoutComponentsByIndex() {
        float[] values = DenseVectorCodec.toFloatArray(Map.of("0", 0.5, "2", -1.0, "1", 0.25), 3);

        assertThat(values).containsExactly(0.5f, 0.25f, -1.0f);
    }

    @Test
    void toFloatArrayShouldRejectDimensionMismatch() {
        assertThatThrownBy(() -> DenseVectorCodec.toFloatArray(Map.of("0", 1.0), 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimension mismatch");
    }

    @Test
    void toFloatArrayShouldRejectSparseTokenKeys() {
        assertThatThrownBy(() -> DenseVectorCodec.toFloatArray(Map.of("redis", 1.0), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sparse");
    }

    @Test
    void toVectorLiteralShouldProducePgvectorSyntax() {
        assertThat(DenseVectorCodec.toVectorLiteral(new float[]{1.0f, -0.5f}))
                .isEqualTo("[1.0,-0.5]");
    }
}
