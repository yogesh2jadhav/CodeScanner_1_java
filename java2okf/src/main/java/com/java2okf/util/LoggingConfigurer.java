package com.java2okf.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import com.java2okf.config.LoggingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Applies the {@code logging:} configuration to Logback at runtime.
 *
 * <p>The static {@code logback.xml} only defines the console appender. The file
 * appender is added here because its location is user-configurable and must
 * be known before the first analysis log line is written.</p>
 */
public final class LoggingConfigurer {

    private static final String APP_LOGGER = "com.java2okf";
    private static final String FILE_APPENDER_NAME = "JAVA2OKF_FILE";
    private static final String CONSOLE_APPENDER_NAME = "CONSOLE";

    private LoggingConfigurer() {
    }

    /**
     * Configures level, console, and file output. Failure to create the log file
     * is reported but does not abort the run: analysis output matters more than
     * the log file.
     */
    public static void configure(LoggingSettings settings) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
            // Another SLF4J binding is active (e.g. in an embedding application); leave it alone.
            return;
        }
        Level level = Level.toLevel(settings.getLevel().toUpperCase(Locale.ROOT), Level.INFO);
        ch.qos.logback.classic.Logger appLogger = context.getLogger(APP_LOGGER);
        appLogger.setLevel(level);

        ch.qos.logback.classic.Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        if (!settings.isConsole()) {
            root.detachAppender(CONSOLE_APPENDER_NAME);
        }

        String file = settings.getFile();
        if (file != null && !file.isBlank() && root.getAppender(FILE_APPENDER_NAME) == null) {
            attachFileAppender(context, root, Path.of(file));
        }
    }

    private static void attachFileAppender(LoggerContext context, ch.qos.logback.classic.Logger root, Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(LoggingConfigurer.class)
                    .warn("Unable to create log directory for {}: {}", file, e.getMessage());
            return;
        }

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] %logger{36} - %msg%n");
        encoder.start();

        FileAppender<ILoggingEvent> appender = new FileAppender<>();
        appender.setContext(context);
        appender.setName(FILE_APPENDER_NAME);
        appender.setFile(file.toString());
        appender.setAppend(true);
        appender.setEncoder(encoder);
        appender.start();

        // The file receives everything the application logger emits, independent of the console.
        root.addAppender(appender);
    }
}
