package com.jcash.app.users;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MobileNumberNormalizerTest {

    @Test
    void keepsLocalFormatAndTrimsWhitespace() {
        assertThat(MobileNumberNormalizer.normalize("09171234567")).isEqualTo("09171234567");
        assertThat(MobileNumberNormalizer.normalize("  09171234567 ")).isEqualTo("09171234567");
    }

    @Test
    void convertsInternationalFormatToLocalFormat() {
        assertThat(MobileNumberNormalizer.normalize("+639171234567")).isEqualTo("09171234567");
        assertThat(MobileNumberNormalizer.normalize(" +639171234567 ")).isEqualTo("09171234567");
    }

    @Test
    void leavesOtherInputTrimmedButUnchanged() {
        assertThat(MobileNumberNormalizer.normalize("639171234567")).isEqualTo("639171234567");
        assertThat(MobileNumberNormalizer.normalize("+63917123456")).isEqualTo("+63917123456");
        assertThat(MobileNumberNormalizer.normalize("+6391712345678")).isEqualTo("+6391712345678");
        assertThat(MobileNumberNormalizer.normalize("   ")).isEmpty();
    }

    @Test
    void returnsNullForNull() {
        assertThat(MobileNumberNormalizer.normalize(null)).isNull();
    }
}
