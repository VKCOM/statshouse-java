// Copyright 2023 V Kontakte LLC
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at https://mozilla.org/MPL/2.0/.

package com.github.vkcom;

public interface Metric {
    Metric tag(String v);
    Metric tag(String name, String v);
    Metric time(long unixTime);
    void count(double count);

    void value(double value);

    void values(double[] values);

    void unique(long[] value);


}
