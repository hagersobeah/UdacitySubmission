package com.loghog;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.StandardOpenOption;

public class ProjectRunner {

    private static final int BATCH_SIZE = 1000;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java --add-modules jdk.incubator.vector -jar <your-jar-file> <logFilePath>");
            System.exit(1);
        }

        Path logFile = Paths.get(args[0]);

        if (!Files.exists(logFile)) {
            System.err.println("Error: File not found at " + logFile);
            System.exit(1);
        }

        

        // TODO: STEP 1 - Implement a naive file reader here to see it fail.
        // For example, try Files.readAllLines() and observe the OutOfMemoryError.
        // Then, remove or comment it out before proceeding.
        // Files.readAllLines(logFile);
        Files.lines(logFile);

        // TODO: STEP 2 - Architect Pluggable Extractors (Reflection & Annotations -
        // Cold Path)
        // Instantiate LogMetricsParser. Use Reflection to scan its methods for the
        // @Extractor annotation.
        // Cache these methods in a Map for fast lookups during runtime processing.

        long startTime = System.nanoTime();

        LogMetricsParser metricsParser = new LogMetricsParser();
        Map<Pattern, Method> extractorMethods = discoverExtractors();
        MetricAccumulator metrics = new MetricAccumulator();

        System.out.println("Discovered extractor methods: " + extractorMethods.size());


        // TODO: STEP 3 - Implement Lazy I/O using Files.lines() (Stream API).
        // Create a LogParser utility class to map strings to LogEntry records.
        long totalLines = 0;
        long corruptLines = 0;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            Stream<String> lines = Files.lines(logFile);
            BufferedWriter badDataWriter = Files.newBufferedWriter(
                    Path.of("bad_data.log"),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            List<String> batch = new ArrayList<>(BATCH_SIZE);

            lines.forEach(line -> {
                batch.add(line);

                if (batch.size() == BATCH_SIZE) {
                    List<String> batchToProcess = new ArrayList<>(batch);
                    batch.clear();

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processBatch(batchToProcess, metricsParser, extractorMethods, metrics, badDataWriter), executor
                    );

                    futures.add(future);
                }
            });

            if (!batch.isEmpty()) {
                List<String> batchToProcess = new ArrayList<>(batch);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    processBatch(batchToProcess, metricsParser, extractorMethods, metrics, badDataWriter), executor
                );

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        // TODO: STEP 4 - Architect Async Processing with Virtual Threads.
        // Implement a Producer-Consumer pattern.
        // CRITICAL HINT: Batch log lines (e.g., 1000 lines per batch) before submitting
        // to the Executor.
        // Use ExecutorService.newVirtualThreadPerTaskExecutor() and CompletableFuture.

        // TODO: STEP 5 - Implement SIMD Statistics with the Vector API.
        // Extract latencies into an int[] array. Implement and benchmark a scalar
        // vs. a vector calculation for the mean and standard deviation.

        // TODO: STEP 6 - Add Resilience and Validation.
        // Wrap your parsing logic in a try-catch block to handle corrupt lines.
        // Log corrupt lines to a separate "bad_data.log" file.

        // --- Final Report ---
        System.out.println("--- LogHog Analysis Complete ---");
        // Print your final results here (Error count, unique IPs, latency stats)
        System.out.println("Total ERROR logs found: " + metrics.getErrorCount());
        System.out.println("Unique IP addresses causing errors: " + metrics.getErrorIps());

        int[] latencies = metrics.getCheckoutLatenciesAsArray();

        long scalarStart = System.nanoTime();
        VectorStats.Stats scalarStats = VectorStats.scalarStats(latencies);
        long scalarEnd = System.nanoTime();

        long vectorStart = System.nanoTime();
        VectorStats.Stats vectorStats = VectorStats.vectorStats(latencies);
        long vectorEnd = System.nanoTime();

        long scalarTimeMs = (scalarEnd - scalarStart) / 1_000_000;
        long vectorTimeMs = (vectorEnd - vectorStart) / 1_000_000;

        System.out.println("Checkout latency samples collected: " + latencies.length);

        System.out.println("Average latency for /checkout (Vector API): "
                + vectorStats.average() + " ms");

        System.out.println("Standard deviation for /checkout (Vector API): "
                + vectorStats.standardDeviation() + " ms");

        System.out.println("Scalar benchmark time: " + scalarTimeMs + " ms");
        System.out.println("Vector benchmark time: " + vectorTimeMs + " ms");

        System.out.println("Scalar average check: " + scalarStats.average() + " ms");

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        System.out.println("\nTotal execution time: " + durationMs + " ms");
        System.out.println("Corrupt lines written to bad_data.log: " + metrics.getBadLineCount());

        metrics.clearCheckoutLatencies();
        latencies = null;
        scalarStats = null;
        vectorStats = null;

        System.gc();

        System.out.println("Memory cleanup complete.");

        waitForProfiler();
    }

    /**
     * Pauses the application, allowing time to attach VisualVM or another profiler.
     */
    private static void waitForProfiler() {
        System.out.println("\nProcessing complete. Press Enter to exit...");
        try (Scanner scanner = new Scanner(System.in)) {
            scanner.nextLine();
        }
    }

    private static Map<Pattern, Method> discoverExtractors() {
        Map<Pattern, Method> extractors = new LinkedHashMap<>();
    
        for (Method method : LogMetricsParser.class.getDeclaredMethods()) {
            Extractor extractor = method.getAnnotation(Extractor.class);
    
            if (extractor != null) {
                Pattern pattern = Pattern.compile(extractor.value());
                method.setAccessible(true);
                extractors.put(pattern, method);
            }
        }
    
        return extractors;
    }

    private static void processBatch(
            List<String> batch,
            LogMetricsParser metricsParser,
            Map<Pattern, Method> extractorMethods,
            MetricAccumulator metrics,
            BufferedWriter badDataWriter
    ) {
        List<String> badLines = new ArrayList<>();

        for (String line : batch) {
            Optional<LogEntry> parsedEntry = LogParser.parse(line);

            if (parsedEntry.isEmpty()) {
                metrics.incrementBadLineCount();
                badLines.add(line);
                continue;
            }

            LogEntry entry = parsedEntry.get();

            for (Map.Entry<Pattern, Method> extractor : extractorMethods.entrySet()) {
                if (extractor.getKey().matcher(line).matches()) {
                    try {
                        extractor.getValue().invoke(metricsParser, entry, metrics);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to run extractor: "
                                + extractor.getValue().getName(), e);
                    }
                }
            }
        }

        if (!badLines.isEmpty()) {
            synchronized (badDataWriter) {
                try {
                    for (String badLine : badLines) {
                        badDataWriter.write(badLine);
                        badDataWriter.newLine();
                    }
                    badDataWriter.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to write bad data log", e);
                }
            }
        }
    }




}
