// Copyright 2023 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.vk.statshouse;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
public class StatsHouseBench {
    @Benchmark
    public void createAndInitAndCount(ShState sh) throws IOException {
        var metric = sh.sh.getMetric("test_jv");
        metric = metric.tag("get");
        metric = metric.tag("test");
        metric = metric.tag("test1");
        metric = metric.tag("test2");
        metric = metric.tag("test3");
        metric = metric.tag("test5");
        metric.count(1);
    }

    @Benchmark
    public void createAndInitAndCountWithBatchTagsCreate(ShState sh) throws IOException {
        var metric = sh.sh.getMetric("test_jv");
        metric = metric.tags("get", "test", "test1", "test2", "test3", "test4", "test5");
        metric.count(1);
    }

    @Benchmark
    public void createAndCount(ShState sh) throws IOException {
        countFromInited(sh.sh.getMetric("test_jv"));
    }

    @Benchmark
    public void createdInitAndCount(ShState sh) throws IOException {
        countFromInited(sh.empty);
    }

    @Benchmark
    public void createdInitedCount(ShState sh) throws IOException {
        sh.m.count(1);
    }

    private void countFromInited(MetricRef metric) throws IOException {
        metric = metric.tag("get");
        metric = metric.tag("test");
        metric = metric.tag("test1");
        metric = metric.tag("test2");
        metric = metric.tag("test3");
        metric = metric.tag("test5");
        metric.count(1);
    }

    @State(Scope.Thread)
    public static class ShState {
        final Client sh;
        final MetricRef m;
        final MetricRef empty;

        {
            sh = new Client("dev");
            MetricRef m = sh.getMetric("test_jv");
            m = m.tag("get");
            m = m.tag("test");
            m = m.tag("test1");
            m = m.tag("test2");
            m = m.tag("test3");
            m = m.tag("test5");
            this.m = m;
            empty = sh.getMetric("test_jv");
        }
    }
}
