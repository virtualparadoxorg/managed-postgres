package eu.virtualparadox.managedpostgres.internal;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON string escaping helpers for stable internal codecs.
 */
public final class JsonStrings {

    private static final Map<Character, String> JSON_ESCAPES = Map.of(
            '"', "\\\"",
            '\\', "\\\\",
            '\b', "\\b",
            '\f', "\\f",
            '\n', "\\n",
            '\r', "\\r",
            '\t', "\\t");
    private static final Map<Character, Character> JSON_UNESCAPES = Map.of(
            '"', '"',
            '\\', '\\',
            '/', '/',
            'b', '\b',
            'f', '\f',
            'n', '\n',
            'r', '\r',
            't', '\t');

    private JsonStrings() {}

    /**
     * Escapes a value for embedding inside a JSON string literal.
     *
     * @param value raw value
     * @return escaped value without surrounding quotes
     */
    public static String escape(final String value) {
        Objects.requireNonNull(value, "value");

        final StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            escaped.append(escapeCharacter(value.charAt(index)));
        }

        return escaped.toString();
    }

    /**
     * Unescapes a JSON string literal value without surrounding quotes.
     *
     * @param value escaped value
     * @return raw value
     */
    public static String unescape(final String value) {
        final String checkedValue = Objects.requireNonNull(value, "value");
        final StringBuilder unescaped = new StringBuilder(checkedValue.length());
        for (int index = 0; index < checkedValue.length(); index++) {
            final char character = checkedValue.charAt(index);
            if (character == '\\') {
                index = appendEscapedCharacter(checkedValue, index, unescaped);
            } else {
                unescaped.append(character);
            }
        }

        return unescaped.toString();
    }

    /**
     * Quotes and escapes a raw value as a JSON string literal.
     *
     * @param value raw value
     * @return escaped JSON string literal including surrounding quotes
     */
    public static String quote(final String value) {
        return '"' + escape(value) + '"';
    }

    private static String escapeCharacter(final char character) {
        return Optional.ofNullable(JSON_ESCAPES.get(character)).orElseGet(() -> escapeControlCharacter(character));
    }

    private static String escapeControlCharacter(final char character) {
        final String escaped;
        if (character < ' ') {
            escaped = "\\u%04x".formatted((int) character);
        } else {
            escaped = String.valueOf(character);
        }

        return escaped;
    }

    private static int appendEscapedCharacter(final String value, final int slashIndex, final StringBuilder unescaped) {
        if (slashIndex + 1 >= value.length()) {
            throw new IllegalArgumentException("JSON escape sequence is incomplete");
        }
        final char escaped = value.charAt(slashIndex + 1);
        final int nextIndex;
        if (escaped == 'u') {
            nextIndex = appendUnicodeEscape(value, slashIndex, unescaped);
        } else {
            unescaped.append(unescapedCharacter(escaped));
            nextIndex = slashIndex + 1;
        }

        return nextIndex;
    }

    private static int appendUnicodeEscape(final String value, final int slashIndex, final StringBuilder unescaped) {
        final int unicodeStart = slashIndex + 2;
        final int unicodeEnd = unicodeStart + 4;
        if (unicodeEnd > value.length()) {
            throw new IllegalArgumentException("JSON unicode escape sequence is incomplete");
        }
        unescaped.append((char) Integer.parseInt(value.substring(unicodeStart, unicodeEnd), 16));

        return unicodeEnd - 1;
    }

    private static char unescapedCharacter(final char escaped) {
        return Optional.ofNullable(JSON_UNESCAPES.get(escaped))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported JSON escape sequence: " + escaped));
    }
}
