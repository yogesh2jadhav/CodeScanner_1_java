package com.java2okf.okf;

import java.util.Map;

/**
 * An OKF Markdown document before serialisation.
 *
 * @param path        bundle-relative path
 * @param frontmatter ordered frontmatter fields
 * @param body        Markdown body
 */
public record OkfDocument(String path, Map<String, Object> frontmatter, String body) {

    public BundleFile render(FrontmatterSerializer serializer) {
        String text = "---\n" + serializer.serialize(frontmatter) + "---\n\n" + body;
        return new BundleFile(path, text.endsWith("\n") ? text : text + "\n");
    }
}
