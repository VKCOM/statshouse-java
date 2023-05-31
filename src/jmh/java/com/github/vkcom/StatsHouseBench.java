// Copyright 2022 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.vkcom;

import org.openjdk.jmh.annotations.*;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
public class StatsHouseBench {
    @State(Scope.Thread)
    public static class ShState {
        StatsHouse sh;
        StatsHouse.Metric m;

        {
            try {
                sh = new StatsHouse(InetAddress.getByName("127.0.0.1"), 65535, "dev");
            } catch (SocketException e) {
                throw new RuntimeException(e);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
            m = sh.metric("test_jv");
            m = m.withTag("get");
            m = m.withTag("test");
        }
    }

    @Benchmark
    public void InitAndCount(ShState sh) {
        var metric = sh.sh.metric("test_jv");
        metric = metric.withTag("get");
        metric = metric.withTag("test");
        metric.count(1);
    }

    @Benchmark
    public void Count(ShState sh) {
        sh.m.count(1);
    }
}
