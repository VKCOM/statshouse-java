// Copyright 2023 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.vk.statshouse;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.state.Action;
import net.jqwik.api.state.ActionChain;
import net.jqwik.api.state.Transformer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

class ClientTest {
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
                                                    return getTestCase(name, tags, count, values, strTop, uniques);
                                                })
                                        )))));
    }

    private static TestCase getTestCase(String name, Object[] tags, Double count, Double[] values, String strTop, Long[] uniques) {
        var tagNames = new String[tags.length];
        var tagValues = new String[tags.length];

        for (int i = 0; i < tags.length; i++) {
            var entry = (Map.Entry<String, String>) tags[i];
            tagNames[i] = entry.getKey();
            tagValues[i] = entry.getValue();
        }
        return new TestCase(name,
            tagNames,
            tagValues,
            count,
            Arrays.stream(values).mapToDouble(Double::doubleValue).toArray(),
            strTop,
            Arrays.stream(uniques).mapToLong(Long::longValue).toArray()
        );
    }

    @Property
    void checkStatsHouse(@ForAll("statsHouseActions") ActionChain<Client> chain) {
        // TODO add check of receiving
        chain.run();
    }

    @Provide
    Arbitrary<ActionChain<Client>> statsHouseActions() {
        return ActionChain.startWith(() -> new Client("test"))
                .withAction(new CountAction(false))
                .withAction(new ValueAction(false))
                .withAction(new STopAction(false))
                .withAction(new UniqueAction(false))
                .withAction(new FlushAction());
    }

    @Property
    void checkStatsHouseTagsCopy(@ForAll("statsHouseActionsTagsCopy") ActionChain<Client> chain) {
        // TODO add check of receiving
        chain.run();
    }

    @Provide
    Arbitrary<ActionChain<Client>> statsHouseActionsTagsCopy() {
        return ActionChain.startWith(() -> new Client("test"))
            .withAction(new CountAction(true))
            .withAction(new ValueAction(true))
            .withAction(new STopAction(true))
            .withAction(new UniqueAction(true))
            .withAction(new FlushAction());
    }

    static class TestCase {
        final String name;
        final String[] tagNames;
        final String[] tagValues;
        final double count;
        final double[] value;
        final String stringTop;
        final long[] uniques;

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

    class CountAction implements Action.Independent<Client> {
        boolean tagsBatchCopy;

        public CountAction(boolean tagsBatchCopy) {
            this.tagsBatchCopy = tagsBatchCopy;
        }

        @Override
        public Arbitrary<Transformer<Client>> transformer() {
            return testCase().map(tc -> Transformer.mutate("count", sh -> {
                var metric = sh.getMetric(tc.name);
                if (tagsBatchCopy) {
                    metric = metric.tags(tc.tagValues);
                } else {
                    for (int i = 0; i < tc.tagValues.length; i++) {
                        metric = metric.tag(tc.tagValues[i]);
                    }
                }
                try {
                    metric.count(tc.count);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
    }

    class ValueAction implements Action.Independent<Client> {
        boolean tagsBatchCopy;
        public ValueAction(boolean tagsBatchCopy) {
            this.tagsBatchCopy = tagsBatchCopy;
        }

        @Override
        public Arbitrary<Transformer<Client>> transformer() {
            return testCase().map(tc -> Transformer.mutate("count", sh -> {
                var metric = sh.getMetric(tc.name);
                if (tagsBatchCopy) {
                    metric = metric.tags(tc.tagValues);
                } else {
                    for (int i = 0; i < tc.tagValues.length; i++) {
                        metric = metric.tag(tc.tagValues[i]);
                    }
                }
                try {
                    metric.values(tc.value);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
    }

    class STopAction implements Action.Independent<Client> {
        boolean tagsBatchCopy;

        public STopAction(boolean tagsBatchCopy) {
            this.tagsBatchCopy = tagsBatchCopy;
        }

        @Override
        public Arbitrary<Transformer<Client>> transformer() {
            return testCase().map(tc -> Transformer.mutate("count", sh -> {
                var metric = sh.getMetric(tc.name);
                if (tagsBatchCopy) {
                    metric = metric.tags(tc.tagValues);
                } else {
                    for (int i = 0; i < tc.tagValues.length; i++) {
                        metric = metric.tag(tc.tagValues[i]);
                    }
                }
                metric.tag(Client.TAG_STRING_TOP, tc.stringTop);
            }));
        }
    }

    class UniqueAction implements Action.Independent<Client> {
        boolean tagsBatchCopy;

        public UniqueAction(boolean tagsBatchCopy) {
            this.tagsBatchCopy = tagsBatchCopy;
        }

        @Override
        public Arbitrary<Transformer<Client>> transformer() {
            return testCase().map(tc -> Transformer.mutate("count", sh -> {
                var metric = sh.getMetric(tc.name);
                if (tagsBatchCopy) {
                    metric = metric.tags(tc.tagValues);
                } else {
                    for (int i = 0; i < tc.tagValues.length; i++) {
                        metric = metric.tag(tc.tagValues[i]);
                    }
                }
                try {
                    metric.uniques(tc.uniques);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
    }

    class FlushAction implements Action.Independent<Client> {

        @Override
        public Arbitrary<Transformer<Client>> transformer() {
            return Arbitraries.just(Transformer.mutate("flush", (c) -> {
                try {
                    c.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
    }
}