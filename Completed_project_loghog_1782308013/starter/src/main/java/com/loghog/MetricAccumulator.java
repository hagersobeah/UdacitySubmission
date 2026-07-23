package com.loghog;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

public class MetricAccumulator {

    private final LongAdder errorCount = new LongAdder();
    private final Set<String> errorIps = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Integer> checkoutLatencies = new ConcurrentLinkedQueue<>();

    private final LongAdder badLineCount = new LongAdder();
    public void incrementBadLineCount() {
        badLineCount.increment();
    }

    public long getBadLineCount() {
        return badLineCount.sum();
    }

    public void incrementErrorCount() {
        errorCount.increment();
    }

    public void addErrorIp(String ip) {
        errorIps.add(ip);
    }

    public void addCheckoutLatency(int latency) {
        checkoutLatencies.add(latency);
    }

    public long getErrorCount() {
        return errorCount.sum();
    }

    public Set<String> getErrorIps() {
        return errorIps;
    }

    public int[] getCheckoutLatenciesAsArray() {
        return checkoutLatencies.stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }

    public void clearCheckoutLatencies() {
        checkoutLatencies.clear();
    }
}