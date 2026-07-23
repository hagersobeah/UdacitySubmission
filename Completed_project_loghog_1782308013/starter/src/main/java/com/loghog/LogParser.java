package com.loghog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(INFO|WARN|ERROR):\\s+(.*)$"
    );

    private static final Pattern IP_PATTERN =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    private static final Pattern LATENCY_PATTERN =
            Pattern.compile("latency_ms=(\\d+)");

    public static Optional<LogEntry> parse(String line) {
        try {
            if (line == null || line.isBlank()) {
                return Optional.empty();
            }

            Matcher logMatcher = LOG_PATTERN.matcher(line);

            if (!logMatcher.matches()) {
                return Optional.empty();
            }

            LocalDateTime timestamp = LocalDateTime.parse(logMatcher.group(1), FORMATTER);
            LogEntry.LogLevel level = LogEntry.LogLevel.valueOf(logMatcher.group(2));
            String message = logMatcher.group(3);

            Optional<String> ipAddress = Optional.empty();
            Matcher ipMatcher = IP_PATTERN.matcher(line);
            if (ipMatcher.find()) {
                ipAddress = Optional.of(ipMatcher.group());
            }

            Optional<Integer> latencyMs = Optional.empty();
            Matcher latencyMatcher = LATENCY_PATTERN.matcher(line);
            if (latencyMatcher.find()) {
                latencyMs = Optional.of(Integer.parseInt(latencyMatcher.group(1)));
            }

            return Optional.of(new LogEntry(
                    timestamp,
                    level,
                    message,
                    ipAddress,
                    latencyMs
            ));

        } catch (Exception e) {
            return Optional.empty();
        }
    }
}