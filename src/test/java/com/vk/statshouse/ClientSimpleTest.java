package com.vk.statshouse;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClientSimpleTest {

    @Test
    public void test() {
        Client client = new Client("test_env");
        MetricRef metric = client.getMetric("metric_test_1");

        metric = metric.addParams("host_1", "linux_debian", "rack_1");
        metric = metric.tag("test_teg_1");


        Client.MetricRefImpl metricImpl = (Client.MetricRefImpl) metric;
        Assertions.assertEquals("metric_test_1", metricImpl.getName());
        Assertions.assertArrayEquals(new String[]{"host_1", "linux_debian", "rack_1"}, metricImpl.getParams());
        Assertions.assertEquals(1, metricImpl.getTagsValues().length);
        Assertions.assertEquals("1", metricImpl.getTagsNames()[0]);
        Assertions.assertEquals("test_teg_1", metricImpl.getTagsValues()[0]);


        metric = metric.tag("test_teg_2");

        metricImpl = (Client.MetricRefImpl) metric;
        Assertions.assertEquals("metric_test_1", metricImpl.getName());
        Assertions.assertArrayEquals(new String[]{"host_1", "linux_debian", "rack_1"}, metricImpl.getParams());
        Assertions.assertEquals(2, metricImpl.getTagsValues().length);
        Assertions.assertEquals("1", metricImpl.getTagsNames()[0]);
        Assertions.assertEquals("test_teg_1", metricImpl.getTagsValues()[0]);
        Assertions.assertEquals("2", metricImpl.getTagsNames()[1]);
        Assertions.assertEquals("test_teg_2", metricImpl.getTagsValues()[1]);


        metric = metric.addParams("params_add_1");

        metricImpl = (Client.MetricRefImpl) metric;
        Assertions.assertEquals("metric_test_1", metricImpl.getName());
        Assertions.assertArrayEquals(new String[]{"host_1", "linux_debian", "rack_1", "params_add_1"}, metricImpl.getParams());
        Assertions.assertEquals(2, metricImpl.getTagsValues().length);
        Assertions.assertEquals("1", metricImpl.getTagsNames()[0]);
        Assertions.assertEquals("test_teg_1", metricImpl.getTagsValues()[0]);
        Assertions.assertEquals("2", metricImpl.getTagsNames()[1]);
        Assertions.assertEquals("test_teg_2", metricImpl.getTagsValues()[1]);

    }
}