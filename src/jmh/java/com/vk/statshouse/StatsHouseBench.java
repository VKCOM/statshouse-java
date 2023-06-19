// Copyright 2023 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.vk.statshouse;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
public class StatsHouseBench {
    @Benchmark
    public void CreateAndInitAndCount(ShState sh) throws IOException {
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
    public void CreateAndInitAndCountWithBatchTagsCreate(ShState sh) throws IOException {
        var metric = sh.sh.getMetric("test_jv");
        metric = metric.tags("get", "test", "test1", "test2", "test3", "test4", "test5");
        metric.count(1);
    }

    @Benchmark
    public void CreateAndCount(ShState sh) throws IOException {
        countFromInited(sh.sh.getMetric("test_jv"));
    }

    @Benchmark
    public void CreatedInitAndCount(ShState sh) throws IOException {
        countFromInited(sh.empty);
    }

    @Benchmark
    public void CreatedInitedCount(ShState sh) throws IOException {
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
        Client sh;
        MetricRef m;
        MetricRef empty;

        {
            try {
                sh = new Client(InetAddress.getByName("127.0.0.1"), 65535, "dev");
            } catch (SocketException | UnknownHostException e) {
                throw new RuntimeException(e);
            }
            sh.transport.socket = null;
            m = sh.getMetric("test_jv");
            m = m.tag("get");
            m = m.tag("test");
            m = m.tag("test1");
            m = m.tag("test2");
            m = m.tag("test3");
            m = m.tag("test5");
            empty = sh.getMetric("test_jv");
        }
    }
}
