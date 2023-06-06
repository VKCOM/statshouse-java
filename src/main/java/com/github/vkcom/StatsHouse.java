// Copyright 2023 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.vkcom;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

public class StatsHouse  {

    public static final int DEFAULT_PORT = 13337;
    public static final String TAG_STRING_TOP = "_s";
    public static final String TAG_HOST = "_h";
    final Transport transport;
    private static final String[] defaultTags = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", TAG_STRING_TOP};
    private static final String[] defaultTagsValues = new String[]{};

    public StatsHouse(InetAddress shHost, int shPort, String env) throws SocketException, UnknownHostException {
        transport = new Transport(shHost.getHostAddress(), shPort, env);
    }

    StatsHouse(String env) {
        transport = new Transport(env);
    }

    public Metric metric(String name) {
        return new MetricImpl(name);
    }



    class MetricImpl implements Metric {
        final String name;
        final String[] tagsNames;
        final String[] tagsValues;
        final int tagsLength;
        final long unixTime;
        final boolean hasEnv = false;

        private MetricImpl(String name) {
            this.name = name;
            this.tagsValues = defaultTagsValues;
            this.tagsNames = defaultTags;
            this.tagsLength = 0;
            this.unixTime = 0;
        }

        private MetricImpl(String name, String[] tagsNames, String[] tagsValues, int tagsLength, long unixTime, String newTagName, String newTagValue) {
            this.name = name;
            if (!"".equals(newTagName)) {
                this.tagsNames = Arrays.copyOf(tagsNames, defaultTags.length);
                this.tagsNames[tagsLength] = newTagName;
            } else {
                this.tagsNames = tagsNames;
            }
            this.tagsValues = Arrays.copyOf(tagsValues, tagsLength + 1);
            this.tagsValues[tagsLength] = newTagValue;
            this.tagsLength = tagsLength + 1;
            this.unixTime = unixTime;
        }


        private MetricImpl(String name, String[] tagsNames, String[] tagsValues, int tagsLength, long unixTime) {
            this.name = name;
            this.tagsNames = tagsNames;
            this.tagsValues = tagsValues;
            this.tagsLength = tagsLength;
            this.unixTime = unixTime;
        }

        public Metric tag(String v) {
            return new MetricImpl(name, tagsNames, tagsValues, tagsLength, unixTime, "", v);
        }

        @Override
        public Metric tag(String name, String v) {
            return new MetricImpl(this.name, tagsNames, tagsValues, tagsLength, unixTime, name, v);
        }

        public Metric time(long unixTime) {
            return new MetricImpl(name, tagsNames, tagsValues, tagsLength, unixTime);
        }

        public void count(double count) {
            System.out.println(toString());
            StatsHouse.this.transport.writeCount(this, tagsValues, count, unixTime);
        }

        public void value(double value) {
            StatsHouse.this.transport.writeValue(this, tagsValues, new double[]{value}, unixTime);
        }

        public void values(double[] values) {
            StatsHouse.this.transport.writeValue(this, tagsValues, values, unixTime);
        }

        public void unique(long[] value) {
            StatsHouse.this.transport.writeUnique(this, tagsValues, value, unixTime);
        }

        @Override
        public String toString() {
            return "MetricImpl{" +
                    "name='" + name + '\'' +
                    ", tagsNames=" + Arrays.toString(tagsNames) +
                    ", tagsValues=" + Arrays.toString(tagsValues) +
                    ", tagsLength=" + tagsLength +
                    ", unixTime=" + unixTime +
                    ", hasEnv=" + hasEnv +
                    '}';
        }
    }

    static boolean nonEmpty(String k) {
        return k != null && !"".equals(k);
    }
}
