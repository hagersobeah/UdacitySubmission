package com.loghog;

public class LogMetricsParser {

    @Extractor(".*ERROR.*")
    public void countErrors(LogEntry entry, MetricAccumulator metrics) {
        if (entry.level() == LogEntry.LogLevel.ERROR) {
            metrics.incrementErrorCount();
            entry.ipAddress().ifPresent(metrics::addErrorIp);
        }
    }

    @Extractor(".*POST /checkout.*latency_ms=\\d+.*")
    public void collectCheckoutLatency(LogEntry entry, MetricAccumulator metrics) {
        entry.latencyMs().ifPresent(metrics::addCheckoutLatency);
    }
}