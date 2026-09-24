package com.java2okf.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@code java2okf.yaml}. Unknown keys are rejected so that a typo such as
 * {@code generateMethodDocs} fails loudly instead of silently using a default.
 */
public final class ConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /**
     * Loads the configuration file, or returns defaults when {@code configFile} is null.
     *
     * @throws ConfigException if the file is missing, unreadable, or malformed
     */
    public Java2OkfConfig load(Path configFile) {
        if (configFile == null) {
            return Java2OkfConfig.defaults();
        }
        if (!Files.isRegularFile(configFile)) {
            throw new ConfigException("Configuration file not found: " + configFile);
        }
        LOG.debug("Loading configuration from {}", configFile);
        try {
            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            // An empty (or comment-only) YAML document means "use every default".
            JsonNode tree = mapper.readTree(content);
            if (tree == null || tree.isMissingNode() || tree.isNull()) {
                return Java2OkfConfig.defaults();
            }
            // Re-read from text rather than from the tree so error locations keep line numbers.
            return mapper.readValue(content, Java2OkfConfig.class);
        } catch (UnrecognizedPropertyException e) {
            throw new ConfigException("Unknown configuration property '" + e.getPropertyName()
                    + "' in " + configFile + " (line " + e.getLocation().getLineNr() + ")", e);
        } catch (JsonProcessingException e) {
            throw new ConfigException("Invalid configuration file " + configFile + ": " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new ConfigException("Unable to read configuration file " + configFile + ": " + e.getMessage(), e);
        }
    }
}
