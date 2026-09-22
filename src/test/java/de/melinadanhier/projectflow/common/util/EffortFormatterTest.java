package de.melinadanhier.projectflow.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EffortFormatterTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, -50})
    void returnsEmptyStringForNullOrNonPositive(Integer minutes) {
        assertThat(EffortFormatter.formatMinutes(minutes)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "1, 1 Min.",
            "30, 30 Min.",
            "45, 45 Min.",
            "59, 59 Min.",
            "60, 1 Std.",
            "61, 1 Std. 1 Min.",
            "90, 1 Std. 30 Min.",
            "120, 2 Std.",
            "125, 2 Std. 5 Min.",
            "600, 10 Std.",
            "615, 10 Std. 15 Min."
    })
    void formatsMinutesCorrectly(int minutes, String expected) {
        assertThat(EffortFormatter.formatMinutes(minutes)).isEqualTo(expected);
    }
}
