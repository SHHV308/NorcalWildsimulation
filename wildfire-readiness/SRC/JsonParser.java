import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonParser {

    private final String input;
    private int position;

    private JsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        JsonParser parser = new JsonParser(input);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != input.length()) {
            throw parser.error("Unexpected content after JSON value.");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object.");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Expected a JSON array.");
        }
        return (List<Object>) value;
    }

    static Object get(Object object, String key) {
        return object(object).get(key);
    }

    static String string(Object value) {
        return value instanceof String ? (String) value : null;
    }

    static Double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private Object readValue() {
        skipWhitespace();
        if (position >= input.length()) {
            throw error("Unexpected end of JSON.");
        }

        char current = input.charAt(position);
        if (current == '{') {
            return readObject();
        }
        if (current == '[') {
            return readArray();
        }
        if (current == '"') {
            return readString();
        }
        if (current == 't') {
            readLiteral("true");
            return Boolean.TRUE;
        }
        if (current == 'f') {
            readLiteral("false");
            return Boolean.FALSE;
        }
        if (current == 'n') {
            readLiteral("null");
            return null;
        }
        return readNumber();
    }

    private Map<String, Object> readObject() {
        position++;
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) {
            return result;
        }

        while (true) {
            skipWhitespace();
            if (position >= input.length() || input.charAt(position) != '"') {
                throw error("Expected a quoted object key.");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            result.put(key, readValue());
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            expect(',');
        }
    }

    private List<Object> readArray() {
        position++;
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) {
            return result;
        }

        while (true) {
            result.add(readValue());
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char current = input.charAt(position++);
            if (current == '"') {
                return result.toString();
            }
            if (current != '\\') {
                if (current < 0x20) {
                    throw error("Unescaped control character in string.");
                }
                result.append(current);
                continue;
            }
            if (position >= input.length()) {
                throw error("Incomplete string escape.");
            }
            char escaped = input.charAt(position++);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    result.append(escaped);
                    break;
                case 'b':
                    result.append('\b');
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'u':
                    result.append(readUnicodeEscape());
                    break;
                default:
                    throw error("Invalid string escape.");
            }
        }
        throw error("Unterminated string.");
    }

    private char readUnicodeEscape() {
        if (position + 4 > input.length()) {
            throw error("Incomplete Unicode escape.");
        }
        try {
            char result = (char) Integer.parseInt(input.substring(position, position + 4), 16);
            position += 4;
            return result;
        } catch (NumberFormatException exception) {
            throw error("Invalid Unicode escape.");
        }
    }

    private Number readNumber() {
        int start = position;
        consume('-');
        readDigits();
        if (consume('.')) {
            readDigits();
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) {
                consume('-');
            }
            readDigits();
        }
        if (start == position) {
            throw error("Expected a JSON value.");
        }
        try {
            return Double.valueOf(input.substring(start, position));
        } catch (NumberFormatException exception) {
            throw error("Invalid number.");
        }
    }

    private void readDigits() {
        int start = position;
        while (position < input.length() && Character.isDigit(input.charAt(position))) {
            position++;
        }
        if (start == position) {
            throw error("Expected a digit.");
        }
    }

    private void readLiteral(String literal) {
        if (!input.startsWith(literal, position)) {
            throw error("Invalid JSON literal.");
        }
        position += literal.length();
    }

    private void skipWhitespace() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
            position++;
        }
    }

    private boolean consume(char expected) {
        if (position < input.length() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!consume(expected)) {
            throw error("Expected '" + expected + "'.");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " At character " + position + ".");
    }
}
