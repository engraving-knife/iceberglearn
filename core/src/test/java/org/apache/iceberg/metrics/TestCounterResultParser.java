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
package org.apache.iceberg.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.iceberg.metrics.MetricsContext.Unit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestCounterResultParser，用于验证 Counter Result Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Counter Result Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestCounterResultParser {

  /**
   * 测试场景：null counter。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void nullCounter() {
    Assertions.assertThatThrownBy(() -> CounterResultParser.fromJson((JsonNode) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse counter from null object");

    Assertions.assertThatThrownBy(() -> CounterResultParser.toJson(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid counter: null");
  }

  /**
   * 测试场景：missing fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void missingFields() {
    Assertions.assertThatThrownBy(() -> CounterResultParser.fromJson("{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: unit");

    Assertions.assertThatThrownBy(() -> CounterResultParser.fromJson("{\"unit\":\"bytes\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing long: value");
  }

  /**
   * 测试场景：extra fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void extraFields() {
    Assertions.assertThat(
            CounterResultParser.fromJson("{\"unit\":\"bytes\",\"value\":23,\"extra\": \"value\"}"))
        .isEqualTo(CounterResult.of(Unit.BYTES, 23L));
  }

  /**
   * 测试场景：unsupported unit。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void unsupportedUnit() {
    Assertions.assertThatThrownBy(
            () -> CounterResultParser.fromJson("{\"unit\":\"unknown\",\"value\":23}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid unit: unknown");
  }

  /**
   * 测试场景：invalid value。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void invalidValue() {
    Assertions.assertThatThrownBy(
            () -> CounterResultParser.fromJson("{\"unit\":\"count\",\"value\":\"illegal\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse to a long value: value: \"illegal\"");
  }

  /**
   * 测试场景：round trip serde。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void roundTripSerde() {
    CounterResult counter = CounterResult.of(Unit.BYTES, Long.MAX_VALUE);

    String json = CounterResultParser.toJson(counter);
    Assertions.assertThat(CounterResultParser.fromJson(json)).isEqualTo(counter);
    Assertions.assertThat(json).isEqualTo("{\"unit\":\"bytes\",\"value\":9223372036854775807}");
  }
}
