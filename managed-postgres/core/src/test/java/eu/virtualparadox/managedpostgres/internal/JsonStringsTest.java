package eu.virtualparadox.managedpostgres.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

public final class JsonStringsTest {

    JsonStringsTest() {}

    @Test
    void escapeAndUnescapeRoundTripJsonControlCharacters() {
        final String raw = "\"slash\\backspace\bformfeed\fnewline\nreturn\rtab\tcontrol" + (char) 1;
        final String escaped = JsonStrings.escape(raw);

        assertThat(escaped)
                .contains("\\\"")
                .contains("\\\\")
                .contains("\\b")
                .contains("\\f")
                .contains("\\n")
                .contains("\\r")
                .contains("\\t")
                .contains("\\u0001");
        assertThat(JsonStrings.unescape(escaped)).isEqualTo(raw);
    }

    @Test
    void unescapeSupportsSlashAndUnicodeEscapes() {
        assertThat(JsonStrings.unescape("\\/tmp\\/pg\\u002ddata")).isEqualTo("/tmp/pg-data");
    }

    @Test
    void unescapeRejectsIncompleteAndUnsupportedEscapes() {
        assertThatThrownBy(() -> JsonStrings.unescape("\\"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incomplete");
        assertThatThrownBy(() -> JsonStrings.unescape("\\u12"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unicode");
        assertThatThrownBy(() -> JsonStrings.unescape("\\x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void quoteEscapesAndWrapsJsonStringLiteral() {
        assertThat(JsonStrings.quote("app\"db")).isEqualTo("\"app\\\"db\"");
    }
}
