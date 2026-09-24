package com.java2okf.scanner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the package declaration from a Java file without a full parse.
 *
 * <p>The scanner needs the package to derive source roots <em>before</em> parsing,
 * because the symbol solver must know every source root up-front to resolve
 * cross-file references. A lightweight lexical scan is sufficient: comments
 * are removed and the first {@code package} statement is matched.</p>
 */
final class PackageDeclarationReader {

    // package declarations may be preceded by annotations (package-info.java)
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*package\\s+([\\p{L}_$][\\p{L}\\p{N}_$]*(?:\\s*\\.\\s*[\\p{L}_$][\\p{L}\\p{N}_$]*)*)\\s*;");

    private PackageDeclarationReader() {
    }

    /** Returns the declared package, or empty for the default package. */
    static Optional<String> read(Path file) throws IOException {
        String text = stripComments(decode(Files.readAllBytes(file)));
        Matcher matcher = PACKAGE.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).replaceAll("\\s+", ""));
    }

    /** Decodes as UTF-8, falling back to ISO-8859-1 so legacy encodings never abort scanning. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Removes line and block comments while preserving string literals, so that a
     * commented-out {@code package} line cannot be picked up by mistake.
     */
    static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int length = source.length();
        while (i < length) {
            char c = source.charAt(i);
            char next = i + 1 < length ? source.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < length && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < length && !(source.charAt(i) == '*' && i + 1 < length && source.charAt(i + 1) == '/')) {
                    // keep newlines so the multiline regex still sees line starts
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else if (c == '"' || c == '\'') {
                int end = skipLiteral(source, i, c);
                out.append(source, i, end);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int skipLiteral(String source, int start, char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote || c == '\n') {
                return i + 1;
            }
            i++;
        }
        return source.length();
    }
}
