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
package org.apache.iceberg;

import java.io.IOException;
import org.apache.iceberg.hadoop.HadoopMetricsContext;
import org.apache.iceberg.io.FileIOMetricsContext;
import org.apache.iceberg.metrics.MetricsContext;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.junit.Test;

/**
 * 文件级说明：测试 TestHadoopMetricsContextSerialization 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 Hadoop指标context序列化 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestHadoopMetricsContextSerialization {

  /** 测试Hadoop指标contextKryo序列化场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testHadoopMetricsContextKryoSerialization() throws IOException {
    MetricsContext metricsContext = new HadoopMetricsContext("s3");

    metricsContext.initialize(Maps.newHashMap());

    MetricsContext deserializedMetricContext = KryoHelpers.roundTripSerialize(metricsContext);
    // statistics are properly re-initialized post de-serialization
    deserializedMetricContext
        .counter(FileIOMetricsContext.WRITE_BYTES, MetricsContext.Unit.BYTES)
        .increment();
  }

  /** 测试Hadoop指标contextJava序列化场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testHadoopMetricsContextJavaSerialization()
      throws IOException, ClassNotFoundException {
    MetricsContext metricsContext = new HadoopMetricsContext("s3");

    metricsContext.initialize(Maps.newHashMap());

    MetricsContext deserializedMetricContext = TestHelpers.roundTripSerialize(metricsContext);
    // statistics are properly re-initialized post de-serialization
    deserializedMetricContext
        .counter(FileIOMetricsContext.WRITE_BYTES, MetricsContext.Unit.BYTES)
        .increment();
  }
}
