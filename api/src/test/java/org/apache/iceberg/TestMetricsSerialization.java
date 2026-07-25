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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestMetricsSerialization 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestMetricsSerialization 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestMetricsSerialization {

  /**
   * 测试场景：Serialization。
   *
   * <p>验证该方法在 Serialization 条件下的行为是否符合预期。
   */
  @Test
  public void testSerialization() throws IOException, ClassNotFoundException {
    Metrics original = generateMetrics();

    byte[] serialized = serialize(original);
    Metrics result = deserialize(serialized);

    assertEquals(original, result);
  }

  /**
   * 测试场景：Serialization With Nulls。
   *
   * <p>验证该方法在 Serialization With Nulls 条件下的行为是否符合预期。
   */
  @Test
  public void testSerializationWithNulls() throws IOException, ClassNotFoundException {
    Metrics original = generateMetricsWithNulls();

    byte[] serialized = serialize(original);
    Metrics result = deserialize(serialized);

    assertEquals(original, result);
  }

  /** 辅助方法：serialize。 */
  private static byte[] serialize(Metrics metrics) throws IOException {
    try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
      ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
      objectOutputStream.writeObject(metrics);
      objectOutputStream.flush();

      return byteArrayOutputStream.toByteArray();
    }
  }

  /** 辅助方法：deserialize。 */
  private static Metrics deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes)) {
      ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

      return (Metrics) objectInputStream.readObject();
    }
  }

  /** 辅助方法：generateMetrics。 */
  private static Metrics generateMetrics() {
    Map<Integer, Long> longMap1 = Maps.newHashMap();
    longMap1.put(1, 2L);
    longMap1.put(3, 4L);

    Map<Integer, Long> longMap2 = Maps.newHashMap();
    longMap2.put(5, 6L);

    Map<Integer, Long> longMap3 = Maps.newHashMap();
    longMap3.put(7, 8L);

    Map<Integer, ByteBuffer> byteMap1 = Maps.newHashMap();
    byteMap1.put(1, ByteBuffer.wrap(new byte[] {1, 2, 3}));
    byteMap1.put(2, ByteBuffer.wrap(new byte[] {1, 2, 3, 4}));

    Map<Integer, ByteBuffer> byteMap2 = Maps.newHashMap();
    byteMap1.put(3, ByteBuffer.wrap(new byte[] {1, 2}));

    return new Metrics(0L, longMap1, longMap2, longMap3, null, byteMap1, byteMap2);
  }

  /** 辅助方法：generateMetricsWithNulls。 */
  private static Metrics generateMetricsWithNulls() {
    Map<Integer, Long> longMap = Maps.newHashMap();
    longMap.put(null, 1L);
    longMap.put(2, null);

    Map<Integer, ByteBuffer> byteMap = Maps.newHashMap();
    byteMap.put(null, ByteBuffer.wrap(new byte[] {1, 2, 3}));
    byteMap.put(4, null);

    return new Metrics(null, null, longMap, longMap, null, null, byteMap);
  }

  /** 辅助方法：assertEquals。 */
  private static void assertEquals(Metrics expected, Metrics actual) {
    assertThat(actual.recordCount()).isEqualTo(expected.recordCount());
    assertThat(actual.columnSizes()).isEqualTo(expected.columnSizes());
    assertThat(actual.valueCounts()).isEqualTo(expected.valueCounts());
    assertThat(actual.nullValueCounts()).isEqualTo(expected.nullValueCounts());

    assertEquals(expected.lowerBounds(), actual.lowerBounds());
    assertEquals(expected.upperBounds(), actual.upperBounds());
  }

  /** 辅助方法：assertEquals。 */
  private static void assertEquals(
      Map<Integer, ByteBuffer> expected, Map<Integer, ByteBuffer> actual) {
    if (expected == null) {
      assertThat(actual).isNull();
    } else {
      assertThat(actual).hasSameSizeAs(expected);
      expected.keySet().forEach(key -> assertThat(actual.get(key)).isEqualTo(expected.get(key)));
    }
  }
}
