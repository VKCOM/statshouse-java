// Copyright 2023 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.vk.statshouse;

import java.io.IOException;

public interface MetricRef {
    MetricRef tag(String v);

    MetricRef tag(String name, String v);

    MetricRef tags(String... values);

    MetricRef addParams(String... v);

    MetricRef time(long unixTime);

    void count(double count) throws IOException;

    void value(double value) throws IOException;

    void values(double[] values) throws IOException;

    void unique(long value) throws IOException;

    void uniques(long[] value) throws IOException;
}
