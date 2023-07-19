// Copyright 2023 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.vk.statshouse;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

public class Client implements Closeable {

    public static final int DEFAULT_PORT = 13337;
    public static final String TAG_STRING_TOP = "_s";
    public static final String TAG_HOST = "_h";
    private static final String[] DEFAULT_TAGS = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"};
    private static final String[] EMPTY_ARRAY = new String[]{};
    private static final String ENV_NAME = "env";
    private static final String ENV_NUM = "0";

    private final Transport transport;

    public Client(InetAddress shHost, int shPort, String env) throws SocketException {
        transport = new Transport(shHost, shPort, env);
    }

    Client(String env) {
        transport = new Transport(env);
    }

    public MetricRef getMetric(String name) {
        return new MetricRefImpl(name);
    }

    public void flush() throws IOException {
        transport.flush();
    }

    @Override
    public void close() throws IOException {
        transport.close();
    }


    final class MetricRefImpl implements MetricRef {
        private final String name;
        private final String[] tagsNames;
        private final String[] tagsValues;
        private final long unixTime;
        private final boolean hasEnv;
        private final String[] params;

        private MetricRefImpl(String name) {
            this.name = name;
            this.tagsValues = EMPTY_ARRAY;
            this.tagsNames = DEFAULT_TAGS;
            this.unixTime = 0;
            this.hasEnv = false;
            this.params = EMPTY_ARRAY;
        }

        private MetricRefImpl(String name, boolean hasEnv, String[] params, String[] tagsNames, String[] tagsValues, long unixTime, String newTagName, String newTagValue) {
            this.name = name;
            if (!"".equals(newTagName)) {
                this.tagsNames = Arrays.copyOf(tagsNames, Math.max(DEFAULT_TAGS.length, tagsValues.length + 1));
                this.tagsNames[tagsValues.length] = newTagName;
            } else {
                this.tagsNames = tagsNames;
            }
            this.tagsValues = Arrays.copyOf(tagsValues, tagsValues.length + 1);
            this.tagsValues[tagsValues.length] = newTagValue;
            this.unixTime = unixTime;
            this.hasEnv = hasEnv || ENV_NAME.equals(newTagName) || ENV_NUM.equals(newTagName);
            this.params = params;
        }

        private MetricRefImpl(String name, boolean hasEnv, String[] params, String[] tagsNames, String[] tagsValues, long unixTime, String... newTags) {
            this.name = name;
            this.tagsNames = tagsNames;
            this.tagsValues = Arrays.copyOf(tagsValues, tagsValues.length + newTags.length);
            System.arraycopy(newTags, 0, this.tagsValues, tagsValues.length, newTags.length);
            this.unixTime = unixTime;
            this.hasEnv = hasEnv;
            this.params = params;

            assert tagsValues.length <= tagsNames.length;
        }


        private MetricRefImpl(String name, boolean hasEnv, String[] params, String[] tagsNames, String[] tagsValues, long unixTime) {
            this.name = name;
            this.tagsNames = tagsNames;
            this.tagsValues = tagsValues;
            this.unixTime = unixTime;
            this.hasEnv = hasEnv;
            this.params = params;
        }

        public MetricRef tag(String v) {
            return new MetricRefImpl(name, hasEnv, this.params, tagsNames, tagsValues, unixTime, "", v);
        }

        public MetricRef tags(String... v) {
            return new MetricRefImpl(name, hasEnv, this.params, tagsNames, tagsValues, unixTime, v);
        }

        @Override
        public MetricRef tag(String name, String v) {
            return new MetricRefImpl(this.name, hasEnv, this.params, tagsNames, tagsValues, unixTime, name, v);
        }

        @Override
        public MetricRef addParams(String... v) {
            String[] newParams = Arrays.copyOf(this.params, this.params.length + v.length);
            System.arraycopy(v, 0, newParams, this.params.length, v.length);
            return new MetricRefImpl(this.name, hasEnv, newParams, tagsNames, tagsValues, unixTime);
        }

        public MetricRef time(long unixTime) {
            return new MetricRefImpl(name, hasEnv, this.params, tagsNames, tagsValues, unixTime);
        }

        public void count(double count) throws IOException {
            Client.this.transport.writeCount(hasEnv, this.params, name, tagsNames, tagsValues, count, unixTime);
        }

        public void value(double value) throws IOException {
            Client.this.transport.writeValue(hasEnv, this.params, name, tagsNames, tagsValues, new double[]{value}, unixTime);
        }

        public void values(double[] values) throws IOException {
            Client.this.transport.writeValue(hasEnv, this.params, name, tagsNames, tagsValues, values, unixTime);
        }

        public void unique(long value) throws IOException {
            Client.this.transport.writeUnique(hasEnv, this.params, name, tagsNames, tagsValues, new long[]{value}, unixTime);
        }

        public void uniques(long[] value) throws IOException {
            Client.this.transport.writeUnique(hasEnv, this.params, name, tagsNames, tagsValues, value, unixTime);
        }

        public String getName() {
            return name;
        }

        public String[] getTagsNames() {
            return tagsNames;
        }

        public String[] getTagsValues() {
            return tagsValues;
        }

        public long getUnixTime() {
            return unixTime;
        }

        public boolean isHasEnv() {
            return hasEnv;
        }

        public String[] getParams() {
            return params;
        }
    }
}
