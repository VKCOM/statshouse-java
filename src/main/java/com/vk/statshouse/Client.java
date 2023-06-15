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
    private static final String[] DEFAULT_TAGS_VALUES = new String[]{};
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
        private final int tagsLength;
        private final long unixTime;
        private final boolean hasEnv;

        private MetricRefImpl(String name) {
            this.name = name;
            this.tagsValues = DEFAULT_TAGS_VALUES;
            this.tagsNames = DEFAULT_TAGS;
            this.tagsLength = 0;
            this.unixTime = 0;
            this.hasEnv = false;
        }

        private MetricRefImpl(String name, boolean hasEnv, String[] tagsNames, String[] tagsValues, int tagsLength, long unixTime, String newTagName, String newTagValue) {
            this.name = name;
            if (!"".equals(newTagName)) {
                this.tagsNames = Arrays.copyOf(tagsNames, Math.max(DEFAULT_TAGS.length, tagsLength + 1));
                this.tagsNames[tagsLength] = newTagName;
            } else {
                this.tagsNames = tagsNames;
            }
            this.tagsValues = Arrays.copyOf(tagsValues, tagsLength + 1);
            this.tagsValues[tagsLength] = newTagValue;
            this.tagsLength = tagsLength + 1;
            this.unixTime = unixTime;
            this.hasEnv = hasEnv || ENV_NAME.equals(newTagName) || ENV_NUM.equals(newTagName);
        }


        private MetricRefImpl(String name, boolean hasEnv, String[] tagsNames, String[] tagsValues, int tagsLength, long unixTime) {
            this.name = name;
            this.tagsNames = tagsNames;
            this.tagsValues = tagsValues;
            this.tagsLength = tagsLength;
            this.unixTime = unixTime;
            this.hasEnv = hasEnv;
        }

        public MetricRef tag(String v) {
            return new MetricRefImpl(name, hasEnv, tagsNames, tagsValues, tagsLength, unixTime, "", v);
        }

        @Override
        public MetricRef tag(String name, String v) {
            return new MetricRefImpl(this.name, hasEnv, tagsNames, tagsValues, tagsLength, unixTime, name, v);
        }

        public MetricRef time(long unixTime) {
            return new MetricRefImpl(name, hasEnv, tagsNames, tagsValues, tagsLength, unixTime);
        }

        public void count(double count) throws IOException {
            Client.this.transport.writeCount(hasEnv, name, tagsNames, tagsValues, tagsLength, count, unixTime);
        }

        public void value(double value) throws IOException {
            Client.this.transport.writeValue(hasEnv, name, tagsNames, tagsValues, tagsLength, new double[]{value}, unixTime);
        }

        public void values(double[] values) throws IOException {
            Client.this.transport.writeValue(hasEnv, name, tagsNames, tagsValues, tagsLength, values, unixTime);
        }

        public void unique(long value) throws IOException {
            Client.this.transport.writeUnique(hasEnv, name, tagsNames, tagsValues, tagsLength, new long[]{value}, unixTime);
        }

        public void uniques(long[] value) throws IOException {
            Client.this.transport.writeUnique(hasEnv, name, tagsNames, tagsValues, tagsLength, value, unixTime);
        }
    }
}
