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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.relocated.com.google.common.annotations.VisibleForTesting;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.util.JsonUtil;

/**
 * {@link TimerResult} 的 JSON 序列化/反序列化器（包级可见）。
 *
 * <p>所属模块：iceberg-core，度量包内负责计时结果在对象与 JSON 之间转换。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 {@link TimerResult} 序列化为含 {@code count}/{@code time-unit}/{@code total-duration} 字段的 JSON
 *       对象。
 *   <li>提供两种反序列化入口：独立 JSON 对象，以及在父对象中按计时器名定位的子节点。
 *   <li>提供 {@link Duration} 与 {@link TimeUnit} 之间的换算辅助方法。
 * </ul>
 *
 * <p>设计意图：时间单位序列化时转为小写名称（如 nanoseconds），反序列化时还原为 {@link TimeUnit}； 时长以指定单位换算为 long 写出，便于跨语言读取。第二种
 * {@code fromJson(String, JsonNode)} 用于父级已持有计时器名的场景（如扫描/提交度量结果），避免重复读写名称字段。
 *
 * <p>上下游关系：被 {@link ScanMetricsResultParser}、{@link CommitMetricsResultParser} 调用。
 */
class TimerResultParser {
  private static final String MISSING_FIELD_ERROR_MSG =
      "Cannot parse timer from '%s': Missing field '%s'";

  private static final String TIME_UNIT = "time-unit";
  private static final String COUNT = "count";
  private static final String TOTAL_DURATION = "total-duration";

  private TimerResultParser() {}

  /**
   * 将计时结果序列化为紧凑 JSON 字符串。
   *
   * @param timer 计时结果
   * @return JSON 字符串
   */
  static String toJson(TimerResult timer) {
    return toJson(timer, false);
  }

  /**
   * 将计时结果序列化为 JSON 字符串，可选择是否美化输出。
   *
   * @param timer 计时结果
   * @param pretty 是否美化（缩进）输出
   * @return JSON 字符串
   */
  static String toJson(TimerResult timer, boolean pretty) {
    return JsonUtil.generate(gen -> toJson(timer, gen), pretty);
  }

  /**
   * 将计时结果写入 {@link JsonGenerator}，输出 count、time-unit（小写名称）、total-duration 三个字段。
   *
   * @param timer 计时结果，不能为 null
   * @param gen JSON 生成器
   * @throws IOException 写入失败时抛出
   */
  static void toJson(TimerResult timer, JsonGenerator gen) throws IOException {
    Preconditions.checkArgument(null != timer, "Invalid timer: null");

    gen.writeStartObject();
    gen.writeNumberField(COUNT, timer.count());
    gen.writeStringField(TIME_UNIT, timer.timeUnit().name().toLowerCase(Locale.ENGLISH));
    gen.writeNumberField(TOTAL_DURATION, fromDuration(timer.totalDuration(), timer.timeUnit()));
    gen.writeEndObject();
  }

  /**
   * 从 JSON 字符串解析 {@link TimerResult}。
   *
   * @param json JSON 字符串
   * @return 计时结果
   */
  static TimerResult fromJson(String json) {
    return JsonUtil.parse(json, TimerResultParser::fromJson);
  }

  /**
   * 从独立 {@link JsonNode} 解析 {@link TimerResult}。
   *
   * <p>逻辑：校验为对象后读取 count、time-unit（小写名称）、total-duration， 通过 {@link #toTimeUnit(String)} 还原单位、{@link
   * #toDuration(long, TimeUnit)} 还原时长，并构造结果。
   *
   * @param json JSON 节点，不能为 null 且必须为对象
   * @return 计时结果
   */
  static TimerResult fromJson(JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse timer from null object");
    Preconditions.checkArgument(json.isObject(), "Cannot parse timer from non-object: %s", json);

    long count = JsonUtil.getLong(COUNT, json);
    TimeUnit unit = toTimeUnit(JsonUtil.getString(TIME_UNIT, json));
    long duration = JsonUtil.getLong(TOTAL_DURATION, json);
    return TimerResult.of(unit, toDuration(duration, unit), count);
  }

  /**
   * 在父 {@link JsonNode} 中按计时器名定位并解析 {@link TimerResult}。
   *
   * <p>设计意图：主要供 {@link ScanMetricsResultParser}/{@link CommitMetricsResultParser} 使用——
   * 在该场景下计时器名已是父对象的字段名，故此处无需再读写名称，仅解析子节点中的 count、time-unit、total-duration。父对象中不存在该名称时返回 null。
   *
   * @param timerName 计时器名称（父对象中的字段名）
   * @param json 包含所有计时器信息的父 {@link JsonNode}
   * @return 计时结果；若父对象无该字段则返回 null
   */
  static TimerResult fromJson(String timerName, JsonNode json) {
    Preconditions.checkArgument(null != json, "Cannot parse timer from null object");
    Preconditions.checkArgument(json.isObject(), "Cannot parse timer from non-object: %s", json);

    if (!json.has(timerName)) {
      return null;
    }

    JsonNode timer = json.get(timerName);
    Preconditions.checkArgument(timer.has(COUNT), MISSING_FIELD_ERROR_MSG, timerName, COUNT);
    Preconditions.checkArgument(
        timer.has(TIME_UNIT), MISSING_FIELD_ERROR_MSG, timerName, TIME_UNIT);
    Preconditions.checkArgument(
        timer.has(TOTAL_DURATION), MISSING_FIELD_ERROR_MSG, timerName, TOTAL_DURATION);

    long count = JsonUtil.getLong(COUNT, timer);
    TimeUnit unit = toTimeUnit(JsonUtil.getString(TIME_UNIT, timer));
    long duration = JsonUtil.getLong(TOTAL_DURATION, timer);
    return TimerResult.of(unit, toDuration(duration, unit), count);
  }

  /**
   * 将 {@link Duration} 按指定 {@link TimeUnit} 换算为 long 值。
   *
   * <p>逻辑：先把时长转为纳秒，再用目标单位从纳秒转换得到对应数值。
   *
   * @param duration 时长
   * @param unit 目标时间单位
   * @return 换算后的数值
   */
  @VisibleForTesting
  static long fromDuration(Duration duration, TimeUnit unit) {
    return unit.convert(duration.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * 将 long 值按指定 {@link TimeUnit} 还原为 {@link Duration}。
   *
   * @param val 数值
   * @param unit 时间单位
   * @return 还原后的时长
   */
  @VisibleForTesting
  static Duration toDuration(long val, TimeUnit unit) {
    return Duration.of(val, toChronoUnit(unit));
  }

  /**
   * 将时间单位字符串（大小写不敏感）解析为 {@link TimeUnit}。
   *
   * @param timeUnit 时间单位名称字符串
   * @return 对应的 {@link TimeUnit}
   * @throws IllegalArgumentException 当字符串不是合法的 TimeUnit 名称时
   */
  private static TimeUnit toTimeUnit(String timeUnit) {
    try {
      return TimeUnit.valueOf(timeUnit.toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(String.format("Invalid time unit: %s", timeUnit), e);
    }
  }

  /**
   * 将 {@link TimeUnit} 映射为对应的 {@link ChronoUnit}，用于构造 {@link Duration}。
   *
   * @param unit 时间单位
   * @return 对应的 {@link ChronoUnit}
   * @throws IllegalArgumentException 当单位无法识别时
   */
  private static ChronoUnit toChronoUnit(TimeUnit unit) {
    switch (unit) {
      case NANOSECONDS:
        return ChronoUnit.NANOS;
      case MICROSECONDS:
        return ChronoUnit.MICROS;
      case MILLISECONDS:
        return ChronoUnit.MILLIS;
      case SECONDS:
        return ChronoUnit.SECONDS;
      case MINUTES:
        return ChronoUnit.MINUTES;
      case HOURS:
        return ChronoUnit.HOURS;
      case DAYS:
        return ChronoUnit.DAYS;
      default:
        throw new IllegalArgumentException("Cannot determine chrono unit from time unit: " + unit);
    }
  }
}
