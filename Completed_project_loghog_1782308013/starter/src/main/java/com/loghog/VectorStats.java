package com.loghog;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class VectorStats {

    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    public record Stats(double average, double standardDeviation) {
    }

    public static Stats scalarStats(int[] values) {
        if (values.length == 0) {
            return new Stats(0.0, 0.0);
        }

        long sum = 0;
        long sumSquares = 0;

        for (int value : values) {
            sum += value;
            sumSquares += (long) value * value;
        }

        double average = (double) sum / values.length;
        double variance = ((double) sumSquares / values.length) - (average * average);
        double standardDeviation = Math.sqrt(Math.max(variance, 0.0));

        return new Stats(average, standardDeviation);
    }

    public static Stats vectorStats(int[] values) {
        if (values.length == 0) {
            return new Stats(0.0, 0.0);
        }

        long sum = 0;
        long sumSquares = 0;

        int i = 0;
        int upperBound = SPECIES.loopBound(values.length);

        for (; i < upperBound; i += SPECIES.length()) {
            IntVector vector = IntVector.fromArray(SPECIES, values, i);

            sum += vector.reduceLanes(VectorOperators.ADD);

            IntVector squared = vector.mul(vector);
            sumSquares += squared.reduceLanes(VectorOperators.ADD);
        }

        // Handle remaining values that do not fit into a full vector
        for (; i < values.length; i++) {
            sum += values[i];
            sumSquares += (long) values[i] * values[i];
        }

        double average = (double) sum / values.length;
        double variance = ((double) sumSquares / values.length) - (average * average);
        double standardDeviation = Math.sqrt(Math.max(variance, 0.0));

        return new Stats(average, standardDeviation);
    }
}