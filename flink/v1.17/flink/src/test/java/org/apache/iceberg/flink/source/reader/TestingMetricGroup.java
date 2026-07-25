/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.flink.source.reader;

import java.util.Map;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Gauge;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.metrics.SimpleCounter;
import org.apache.flink.metrics.groups.OperatorIOMetricGroup;
import org.apache.flink.metrics.groups.SourceReaderMetricGroup;
import org.apache.flink.metrics.groups.UnregisteredMetricsGroup;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：测试 TestingMetricGroup 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.17）。职责：验证 TestingMetricGroup 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
class TestingMetricGroup extends UnregisteredMetricsGroup implements SourceReaderMetricGroup {
  private final Map<String, Counter> counters;

  TestingMetricGroup() {
    this.counters = Maps.newHashMap();
  }

  /** Pass along the reference to share the map for child metric groups. */
  private TestingMetricGroup(Map<String, Counter> counters) {
    this.counters = counters;
  }

  Map<String, Counter> counters() {
    return counters;
  }

  /** 辅助方法：counter，counter。 */
  @Override
  public Counter counter(String name) {
    Counter counter = new SimpleCounter();
    counters.put(name, counter);
    return counter;
  }

  /** 辅助方法：addGroup，add Group。 */
  @Override
  public MetricGroup addGroup(String name) {
    return new TestingMetricGroup(counters);
  }

  /** 辅助方法：addGroup，add Group。 */
  @Override
  public MetricGroup addGroup(String key, String value) {
    return new TestingMetricGroup(counters);
  }

  /** 辅助方法：getIOMetricGroup，get IO Metric Group。 */
  @Override
  public OperatorIOMetricGroup getIOMetricGroup() {
    return new TestingOperatorIOMetricGroup();
  }

  /** 辅助方法：getNumRecordsInErrorsCounter，get Num Records In Errors Counter。 */
  @Override
  public Counter getNumRecordsInErrorsCounter() {
    return new SimpleCounter();
  }

  /** 辅助方法：setPendingBytesGauge，set Pending Bytes Gauge。 */
  @Override
  public void setPendingBytesGauge(Gauge<Long> pendingBytesGauge) {}

  /** 辅助方法：setPendingRecordsGauge，set Pending Records Gauge。 */
  @Override
  public void setPendingRecordsGauge(Gauge<Long> pendingRecordsGauge) {}

  private static class TestingOperatorIOMetricGroup extends UnregisteredMetricsGroup
      implements OperatorIOMetricGroup {
    /** 辅助方法：getNumRecordsInCounter，get Num Records In Counter。 */
    @Override
    public Counter getNumRecordsInCounter() {
      return new SimpleCounter();
    }

    /** 辅助方法：getNumRecordsOutCounter，get Num Records Out Counter。 */
    @Override
    public Counter getNumRecordsOutCounter() {
      return new SimpleCounter();
    }

    /** 辅助方法：getNumBytesInCounter，get Num Bytes In Counter。 */
    @Override
    public Counter getNumBytesInCounter() {
      return new SimpleCounter();
    }

    /** 辅助方法：getNumBytesOutCounter，get Num Bytes Out Counter。 */
    @Override
    public Counter getNumBytesOutCounter() {
      return new SimpleCounter();
    }
  }
}
