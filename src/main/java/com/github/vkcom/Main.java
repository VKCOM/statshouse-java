// Copyright 2023 V Kontakte LLC
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
        for (long i = 0; ; i++) {
            //var t = (System.currentTimeMillis()/1000) - 3600;
            //metric.tag("a").tag("a").tag("a").tag("a").tag("a").tag("a").tag("a").tag("a").tag("a").tag("a").tag("a").tag("a").tag("a").count(i);
            sh.metric("test_jv").tag("get").tag("test").tag("key", "value").tag(StatsHouse.TAG_STRING_TOP, "abc").count(i);
            sh.metric("test_jv_value").tag("get").tag("test").tag("key", "value").tag(StatsHouse.TAG_STRING_TOP, "abc").value(i);
            sh.metric("test_jv_unique").tag("get").tag("test").tag("key", "value").tag(StatsHouse.TAG_STRING_TOP, "abc").unique(new long[]{i});
            Thread.sleep(100);
        }
    }
}