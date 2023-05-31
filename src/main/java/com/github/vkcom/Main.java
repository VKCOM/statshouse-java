// Copyright 2022 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.vkcom;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Main {
    public static void main(String[] args) throws SocketException, UnknownHostException, InterruptedException {
        var sh = new StatsHouse(InetAddress.getByName("127.0.0.1"), 13337, "dev");
        var metric = sh.metric("test_jv");
        for (long i = 0; ; i++) {
            metric.withTag("get").withTag("test").count(i);
            metric.withTag("put").withTag("test").values(new double[]{i, i});
            metric.withTag("get").withTag("test").stringTop("stop");
            metric.unique(new long[]{1});
            Thread.sleep(100);
        }
    }
}