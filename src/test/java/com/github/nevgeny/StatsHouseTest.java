package com.github.nevgeny;

import java.util.*;

import net.jqwik.api.*;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;

class StatsHouseTest {
    @Provide
    Arbitrary<String> metricName() {
        return Arbitraries.strings().alpha().numeric().withChars('_').ofMinLength(1);
    }

    @Provide
    Arbitrary<Object[]> tags() {
        return Arbitraries.entries(metricName(), metricName()).array(Object[].class).ofMaxSize(15);
    }

    @Provide
    Arbitrary<Double> value() {
        return Arbitraries.doubles();
    }

    @Provide
    Arbitrary<Double[]> values() {
        return Arbitraries.doubles().array(Double[].class);
    }

    @Provide
    Arbitrary<Long[]> uniques() {
        return Arbitraries.longs().array(Long[].class);
    }


    @Provide
    Arbitrary<TestCase> testCase() {
        return metricName().flatMap(name ->
                tags().flatMap(tags ->
                        value().flatMap(count ->
                                values().flatMap(values ->
                                        metricName().flatMap(strTop ->
                                                uniques().map(uniques -> {
                                                    var tagNames = new String[tags.length];
                                                    var tagValues = new String[tags.length];

                                                    for (int i = 0; i < tags.length; i++) {
                                                        var entry = (Map.Entry<String, String>) tags[i];
                                                        tagNames[i] = entry.getKey();
                                                        tagValues[i] = entry.getValue();
                                                    }
                                                    return new TestCase(name, tagNames, tagValues, count, Arrays.stream(values).mapToDouble(Double::doubleValue).toArray(), strTop, Arrays.stream(uniques).mapToLong(Long::longValue).toArray());
                                                })
                                        )))));
    }

    static class TestCase {
        String name;
        String[] tagNames;
        String[] tagValues;
        double count;
        double[] value;
        String stringTop;
        long[] uniques;

        public TestCase(String name, String[] tagNames, String[] tagValues, double count, double[] value, String stringTop, long[] uniques) {
            this.name = name;
            this.tagNames = tagNames;
            this.tagValues = tagValues;
            this.count = count;
            this.value = value;
            this.stringTop = stringTop;
            this.uniques = uniques;
        }
    }


    @Property
    void checkStatsHouse(@ForAll("statsHouseActions") ActionChain<StatsHouse> chain) {
        // TODO add check of receiving
        chain.run();
    }

    @Provide
    Arbitrary<ActionChain<StatsHouse>> statsHouseActions() {
        return ActionChain.startWith(() -> new StatsHouse("test"))
                .withAction(new CountAction())
                .withAction(new ValueAction())
                .withAction(new STopAction())
                .withAction(new UniqueAction());
    }

    class CountAction implements Action.Independent<StatsHouse> {
        @Override
        public Arbitrary<Transformer<StatsHouse>> transformer() {
            return testCase().map(tc -> Transformer.mutate("count", sh -> {
                var metric = sh.metric(tc.name);
                for (int i = 0; i < tc.tagValues.length; i++) {
                    metric = metric.withTag(tc.tagValues[i]);
                }
                metric.count(tc.count);
            }));
        }
    }

    class ValueAction implements Action.Independent<StatsHouse> {
        @Override
        public Arbitrary<Transformer<StatsHouse>> transformer() {
            return testCase().map(tc -> Transformer.mutate("count", sh -> {
                var metric = sh.metric(tc.name);
                for (int i = 0; i < tc.tagValues.length; i++) {
                    metric = metric.withTag(tc.tagValues[i]);
                }
                metric.values(tc.value);
            }));
        }
    }

    class STopAction implements Action.Independent<StatsHouse> {
        @Override
        public Arbitrary<Transformer<StatsHouse>> transformer() {
            return testCase().map(tc -> Transformer.mutate("count", sh -> {
                var metric = sh.metric(tc.name);
                for (int i = 0; i < tc.tagValues.length; i++) {
                    metric = metric.withTag(tc.tagValues[i]);
                }
                metric.stringTop(tc.stringTop);
            }));
        }
    }

    class UniqueAction implements Action.Independent<StatsHouse> {
        @Override
        public Arbitrary<Transformer<StatsHouse>> transformer() {
            return testCase().map(tc -> Transformer.mutate("count", sh -> {
                var metric = sh.metric(tc.name);
                for (int i = 0; i < tc.tagValues.length; i++) {
                    metric = metric.withTag(tc.tagValues[i]);
                }
                metric.unique(tc.uniques);
            }));
        }
    }
}