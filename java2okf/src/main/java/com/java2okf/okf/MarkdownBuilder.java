package com.java2okf.okf;

import java.util.List;

/**
 * Minimal Markdown writer that produces consistently formatted output.
 */
public final class MarkdownBuilder {

    private final StringBuilder out = new StringBuilder();

    public MarkdownBuilder heading(int level, String text) {
        separate();
        out.append("#".repeat(level)).append(' ').append(text).append("\n\n");
        return this;
    }

    public MarkdownBuilder paragraph(String text) {
        separate();
        out.append(text).append("\n\n");
        return this;
    }

    public MarkdownBuilder bullet(String text) {
        out.append("- ").append(text).append('\n');
        return this;
    }

    /** Indented sub-bullet belonging to the previous bullet. */
    public MarkdownBuilder subBullet(String text) {
        out.append("  - ").append(text).append('\n');
        return this;
    }

    /** Writes a section with one bullet per item; omitted entirely when {@code items} is empty. */
    public MarkdownBuilder bulletSection(String heading, List<String> items) {
        if (items.isEmpty()) {
            return this;
        }
        heading(2, heading);
        items.forEach(this::bullet);
        out.append('\n');
        return this;
    }

    public MarkdownBuilder codeBlock(String language, String code) {
        separate();
        out.append("```").append(language).append('\n').append(code).append("\n```\n\n");
        return this;
    }

    public MarkdownBuilder table(List<String> header, List<List<String>> rows) {
        separate();
        out.append("| ").append(String.join(" | ", header)).append(" |\n");
        out.append("|").append(" --- |".repeat(header.size())).append('\n');
        for (List<String> row : rows) {
            out.append("| ").append(String.join(" | ", row.stream().map(MarkdownBuilder::escapeCell).toList())).append(" |\n");
        }
        out.append('\n');
        return this;
    }

    /** Ends a bullet list started with {@link #bullet}. */
    public MarkdownBuilder endList() {
        out.append('\n');
        return this;
    }

    public String build() {
        String text = out.toString();
        // Normalise to exactly one trailing newline.
        return text.replaceAll("\\n+$", "") + "\n";
    }

    private void separate() {
        int length = out.length();
        if (length > 0 && out.charAt(length - 1) != '\n') {
            out.append("\n\n");
        } else if (length > 1 && out.charAt(length - 2) != '\n') {
            out.append('\n');
        }
    }

    /** Inline code span; uses a longer fence when the text itself contains backticks. */
    public static String code(String text) {
        return text.contains("`") ? "`` " + text + " ``" : "`" + text + "`";
    }

    public static String link(String text, String target) {
        return "[" + escapeLinkText(text) + "](" + target + ")";
    }

    static String escapeLinkText(String text) {
        return text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }

    private static String escapeCell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
