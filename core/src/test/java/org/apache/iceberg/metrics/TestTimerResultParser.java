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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestTimerResultParser，用于验证 Timer Result Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Timer Result Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestTimerResultParser {

  /**
   * 测试场景：null timer。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void nullTimer() {
    Assertions.assertThatThrownBy(() -> TimerResultParser.fromJson((JsonNode) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse timer from null object");

    Assertions.assertThatThrownBy(() -> TimerResultParser.toJson(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid timer: null");
  }

  /**
   * 测试场景：missing fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void missingFields() {
    Assertions.assertThatThrownBy(() -> TimerResultParser.fromJson("{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing long: count");

    Assertions.assertThatThrownBy(() -> TimerResultParser.fromJson("{\"count\":44}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: time-unit");

    Assertions.assertThatThrownBy(
            () -> TimerResultParser.fromJson("{\"count\":44,\"time-unit\":\"hours\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing long: total-duration");
  }

  /**
   * 测试场景：extra fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void extraFields() {
    Assertions.assertThat(
            TimerResultParser.fromJson(
                "{\"count\":44,\"time-unit\":\"hours\",\"total-duration\":24,\"extra\": \"value\"}"))
        .isEqualTo(TimerResult.of(TimeUnit.HOURS, Duration.ofHours(24), 44));
  }

  /**
   * 测试场景：unsupported duration。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void unsupportedDuration() {
    Assertions.assertThatThrownBy(
            () ->
                TimerResultParser.fromJson(
                    "{\"count\":44,\"time-unit\":\"hours\",\"total-duration\":\"xx\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse to a long value: total-duration: \"xx\"");
  }

  /**
   * 测试场景：unsupported time unit。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void unsupportedTimeUnit() {
    Assertions.assertThatThrownBy(
            () ->
                TimerResultParser.fromJson(
                    "{\"count\":44,\"time-unit\":\"unknown\",\"total-duration\":24}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid time unit: unknown");
  }

  /**
   * 测试场景：invalid count。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void invalidCount() {
    Assertions.assertThatThrownBy(
            () ->
                TimerResultParser.fromJson(
                    "{\"count\":\"illegal\",\"time-unit\":\"hours\",\"total-duration\":24}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse to a long value: count: \"illegal\"");
  }

  /**
   * 测试场景：round trip serde。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void roundTripSerde() {
    TimerResult timer = TimerResult.of(TimeUnit.HOURS, Duration.ofHours(23), 44);

    String json = TimerResultParser.toJson(timer);
    Assertions.assertThat(TimerResultParser.fromJson(json)).isEqualTo(timer);
    Assertions.assertThat(json)
        .isEqualTo("{\"count\":44,\"time-unit\":\"hours\",\"total-duration\":23}");
  }

  /**
   * 测试场景：to duration。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void toDuration() {
    Assertions.assertThat(TimerResultParser.toDuration(5L, TimeUnit.NANOSECONDS))
        .isEqualTo(Duration.ofNanos(5L));
    Assertions.assertThat(TimerResultParser.toDuration(5L, TimeUnit.MICROSECONDS))
        .isEqualTo(Duration.of(5L, ChronoUnit.MICROS));
    Assertions.assertThat(TimerResultParser.toDuration(5L, TimeUnit.MILLISECONDS))
        .isEqualTo(Duration.ofMillis(5L));
    Assertions.assertThat(TimerResultParser.toDuration(5L, TimeUnit.SECONDS))
        .isEqualTo(Duration.ofSeconds(5L));
    Assertions.assertThat(TimerResultParser.toDuration(5L, TimeUnit.MINUTES))
        .isEqualTo(Duration.ofMinutes(5L));
    Assertions.assertThat(TimerResultParser.toDuration(5L, TimeUnit.HOURS))
        .isEqualTo(Duration.ofHours(5L));
    Assertions.assertThat(TimerResultParser.toDuration(5L, TimeUnit.DAYS))
        .isEqualTo(Duration.ofDays(5L));
  }

  /**
   * 测试场景：from duration。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void fromDuration() {
    Assertions.assertThat(
            TimerResultParser.fromDuration(Duration.ofNanos(5L), TimeUnit.NANOSECONDS))
        .isEqualTo(5L);
    Assertions.assertThat(
            TimerResultParser.fromDuration(
                Duration.of(5L, ChronoUnit.MICROS), TimeUnit.MICROSECONDS))
        .isEqualTo(5L);
    Assertions.assertThat(
            TimerResultParser.fromDuration(Duration.ofMillis(5L), TimeUnit.MILLISECONDS))
        .isEqualTo(5L);
    Assertions.assertThat(TimerResultParser.fromDuration(Duration.ofSeconds(5L), TimeUnit.SECONDS))
        .isEqualTo(5L);
    Assertions.assertThat(TimerResultParser.fromDuration(Duration.ofMinutes(5L), TimeUnit.MINUTES))
        .isEqualTo(5L);
    Assertions.assertThat(TimerResultParser.fromDuration(Duration.ofHours(5L), TimeUnit.HOURS))
        .isEqualTo(5L);
    Assertions.assertThat(TimerResultParser.fromDuration(Duration.ofDays(5L), TimeUnit.DAYS))
        .isEqualTo(5L);
  }
}
