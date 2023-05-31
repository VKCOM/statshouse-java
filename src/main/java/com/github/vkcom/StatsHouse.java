// Copyright 2022 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.vkcom;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;

public class StatsHouse implements Closeable {

    final Transport transport;
    private static final String[] defaultTags = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"};

    public StatsHouse(InetAddress shHost, int shPort, String env) throws SocketException {
        transport = new Transport(shHost, shPort, env);
    }

    StatsHouse(String env) {
        transport = new Transport(env);
    }

    public Metric metric(String name) {
        return new Metric(name);
    }

    @Override
    public void close() throws IOException {
        transport.close();
    }


    public class Metric {
        final String name;
        final String[] tagsNames;
        String[] tagsValues;
        final int tagsLength;
        final long unixTime;
        final boolean hasEnv = false;

        private Metric(String name) {
            this.name = name;
            this.tagsValues = new String[0];
            this.tagsNames = defaultTags;
            this.tagsLength = 0;
            this.unixTime = 0;
        }

        private Metric(String name, String[] tagsNames, String[] tagsValues, int tagsLength, long unixTime, String newTag) {
            this.name = name;
            this.tagsNames = tagsNames;
            this.tagsValues = Arrays.copyOf(tagsValues, tagsLength + 1);
            this.tagsValues[tagsLength] = newTag;
            this.tagsLength = tagsLength + 1;
            this.unixTime = unixTime;
        }

        private Metric(String name, String[] tagsNames, String[] tagsValues, int tagsLength, long unixTime) {
            this.name = name;
            this.tagsNames = tagsNames;
            this.tagsValues = tagsValues;
            this.tagsLength = tagsLength;
            this.unixTime = unixTime;
        }

        public Metric withTag(String t) {
            return new Metric(name, tagsNames, tagsValues, tagsLength, unixTime, t);
        }

        public Metric withTime(long unixTime) {
            return new Metric(name, tagsNames, tagsValues, tagsLength, unixTime);
        }

        public void count(double count) {
            StatsHouse.this.transport.writeCount(this, tagsValues, tagsLength, "", count, unixTime);
        }

        public void value(double value) {
            StatsHouse.this.transport.writeValue(this, tagsValues, tagsLength, "", new double[]{value}, unixTime);
        }

        public void values(double[] values) {
            StatsHouse.this.transport.writeValue(this, tagsValues, tagsLength, "", values, unixTime);
        }

        public void stringTop(String str) {
            if (nonEmpty(str)) {
                StatsHouse.this.transport.writeCount(this, tagsValues, tagsLength, str, 1, unixTime);
            }
        }

        public void unique(long[] value) {
            StatsHouse.this.transport.writeUnique(this, tagsValues, tagsLength, "", value, unixTime);
        }

        boolean defaultKeys() {
            return tagsNames == defaultTags;
        }
    }

    static boolean nonEmpty(String k) {
        return k != null && !"".equals(k);
    }
}
