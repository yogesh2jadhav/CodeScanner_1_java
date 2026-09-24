package com.java2okf.okf;

import com.java2okf.config.OkfSettings;
import com.java2okf.model.EntityIds;
import com.java2okf.model.JavaExecutable;
import com.java2okf.model.JavaPackage;
import com.java2okf.model.JavaProject;
import com.java2okf.model.JavaType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides where every document lives in the bundle.
 *
 * <p>File names are derived from qualified names, so they are stable across runs
 * and machines and diff well in Git. Method files additionally carry a short
 * hash of the full ID because raw signatures contain characters that are not
 * portable in file names, and overloads must not collide. Name clashes that only
 * differ in case (a problem on macOS and Windows) are detected and resolved
 * deterministically.</p>
 */
public final class DocumentLayout {

    public static final String PACKAGES_DIR = "packages";
    public static final String METHODS_DIR = "methods";
    public static final String METADATA_DIR = "_metadata";
    public static final String INDEX = "index.md";
    public static final String LOG = "log.md";

    private static final int SHORT_HASH = 6;
    private static final int MAX_FILE_NAME = 200;

    private final Map<String, String> paths = new HashMap<>();
    private final Set<String> usedPaths = new HashSet<>();

    private DocumentLayout() {
    }

    /** Plans the path of every document that the configuration asks for. */
    public static DocumentLayout plan(JavaProject project, OkfSettings settings) {
        DocumentLayout layout = new DocumentLayout();
        // Sorted maps guarantee that collision suffixes are assigned in the same order every run.
        for (JavaPackage javaPackage : project.packages().values()) {
            String name = javaPackage.name().isEmpty() ? "_default" : javaPackage.name();
            layout.assign(javaPackage.id(), PACKAGES_DIR, name);
        }
        if (settings.isGenerateClassDocuments()) {
            for (JavaType type : project.types().values()) {
                layout.assign(type.id(), OkfDocumentType.forKind(type.kind()).directory(), type.qualifiedName());
            }
        }
        if (settings.isGenerateMethodDocuments()) {
            for (JavaType type : project.types().values()) {
                for (JavaExecutable executable : type.methods()) {
                    layout.assignExecutable(executable, type);
                }
                for (JavaExecutable executable : type.constructors()) {
                    layout.assignExecutable(executable, type);
                }
            }
        }
        return layout;
    }

    /** Bundle-relative path of the document for {@code id}, if one is generated. */
    public Optional<String> pathOf(String id) {
        return Optional.ofNullable(id == null ? null : paths.get(id));
    }

    public boolean hasDocument(String id) {
        return id != null && paths.containsKey(id);
    }

    /** Relative link from one bundle file to another, e.g. {@code ../classes/a.B.md}. */
    public static String relativeLink(String fromPath, String toPath) {
        String[] from = fromPath.split("/");
        String[] to = toPath.split("/");
        int common = 0;
        while (common < from.length - 1 && common < to.length - 1 && from[common].equals(to[common])) {
            common++;
        }
        StringBuilder link = new StringBuilder();
        int ups = from.length - 1 - common;
        if (ups == 0 && from.length > 1) {
            link.append("./");
        }
        link.append("../".repeat(ups));
        for (int i = common; i < to.length; i++) {
            link.append(to[i]);
            if (i < to.length - 1) {
                link.append('/');
            }
        }
        return link.toString();
    }

    private void assignExecutable(JavaExecutable executable, JavaType type) {
        String base = type.qualifiedName() + "." + executable.name() + "-" + hash(executable.id(), SHORT_HASH);
        assign(executable.id(), METHODS_DIR, base);
    }

    private void assign(String id, String directory, String baseName) {
        String name = sanitize(baseName);
        if (name.length() > MAX_FILE_NAME) {
            // Keep the (most specific) tail and make the name unique with a hash of the ID.
            name = name.substring(name.length() - MAX_FILE_NAME) + "-" + hash(id, 12);
        }
        String path = directory + "/" + name + ".md";
        int hashLength = 8;
        while (!usedPaths.add(path.toLowerCase(Locale.ROOT))) {
            path = directory + "/" + name + "-" + hash(id, hashLength) + ".md";
            hashLength += 4;
        }
        paths.put(id, path);
    }

    /** Keeps characters that are safe in file names and Markdown links on every platform. */
    static String sanitize(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (char c : name.toCharArray()) {
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_' || c == '$';
            out.append(safe ? c : '_');
        }
        return out.toString();
    }

    /** First {@code length} hex characters of the SHA-256 of {@code value}; stable across JVMs. */
    public static String hash(String value, int length) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, Math.min(length, 64));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /** Short human label of an entity ID, e.g. {@code OrderRepository.save(Order)}. */
    public static String label(String id) {
        String local = EntityIds.localPart(id);
        if (id.startsWith(EntityIds.CONSTRUCTOR_PREFIX + ":")) {
            String owner = local.substring(0, local.indexOf('('));
            return simpleName(owner) + "(" + simpleParameters(local.substring(local.indexOf('(') + 1, local.length() - 1)) + ")";
        }
        if (id.startsWith(EntityIds.METHOD_PREFIX + ":")) {
            String beforeParams = local.substring(0, local.indexOf('('));
            String owner = beforeParams.substring(0, beforeParams.lastIndexOf('.'));
            String name = beforeParams.substring(beforeParams.lastIndexOf('.') + 1);
            return simpleName(owner) + "." + name + "(" + simpleParameters(local.substring(local.indexOf('(') + 1, local.lastIndexOf(')'))) + ")"
                    + local.substring(local.lastIndexOf(')') + 1);
        }
        if (id.startsWith(EntityIds.FIELD_PREFIX + ":")) {
            String owner = local.substring(0, local.lastIndexOf('.'));
            return simpleName(owner) + "." + local.substring(local.lastIndexOf('.') + 1);
        }
        if (id.startsWith(EntityIds.PACKAGE_PREFIX + ":")) {
            return local;
        }
        return simpleName(local);
    }

    static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
    }

    private static String simpleParameters(String parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String parameter : parameters.split(",")) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(simpleName(parameter));
        }
        return out.toString();
    }
}
