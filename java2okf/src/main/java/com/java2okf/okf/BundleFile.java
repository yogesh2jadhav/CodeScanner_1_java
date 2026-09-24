package com.java2okf.okf;

/**
 * A file of the generated bundle: path relative to the bundle root (always
 * with {@code /} separators) and its complete UTF-8 content.
 */
public record BundleFile(String path, String content) implements Comparable<BundleFile> {

    @Override
    public int compareTo(BundleFile other) {
        return path.compareTo(other.path);
    }
}
