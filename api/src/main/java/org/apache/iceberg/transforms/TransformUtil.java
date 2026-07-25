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
package org.apache.iceberg.transforms;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 变换工具类：提供变换值的人类可读字符串格式化与 base64 编码。
 *
 * <p>所属模块：iceberg-api（被各 Transform 实现用于把分区值格式化为可读字符串）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>把 year/month/day/hour/time/timestamp 等粒度的整数值格式化为 ISO 字符串。
 *   <li>把二进制（ByteBuffer）做 base64 编码为字符串。
 * </ul>
 *
 * <p>设计意图：所有格式化逻辑集中于此，保证跨实现一致；以 epoch（1970-01-01 UTC）为基准计算， 避免时区歧义；base64 编码用 ISO-8859-1 直接解码（因
 * base64 输出必为 ASCII）。
 *
 * <p>上下游关系：被 {@link Transform#toHumanString}、{@link Dates}、{@link Timestamps}、 {@link
 * Identity}、{@link Bucket} 等调用。
 */
class TransformUtil {

  private TransformUtil() {}

  private static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);
  private static final int EPOCH_YEAR = EPOCH.getYear();

  /**
   * 把年份序号（自 epoch 起的年数）格式化为 4 位年份字符串。
   *
   * @param yearOrdinal 自 epoch 起的年数
   * @return 4 位年份字符串，如 "2020"
   */
  static String humanYear(int yearOrdinal) {
    return String.format("%04d", EPOCH_YEAR + yearOrdinal);
  }

  /**
   * 把月份序号格式化为 "YYYY-MM" 字符串。
   *
   * <p>逻辑：用 floorDiv/floorMod 处理负数序号（epoch 之前的月份），保证月份在 1-12 范围。
   *
   * @param monthOrdinal 自 epoch 起的月数
   * @return "YYYY-MM" 字符串
   */
  static String humanMonth(int monthOrdinal) {
    return String.format(
        "%04d-%02d",
        EPOCH_YEAR + Math.floorDiv(monthOrdinal, 12), 1 + Math.floorMod(monthOrdinal, 12));
  }

  /**
   * 把天数序号格式化为 "YYYY-MM-DD" 字符串。
   *
   * @param dayOrdinal 自 epoch 起的天数
   * @return "YYYY-MM-DD" 字符串
   */
  static String humanDay(int dayOrdinal) {
    OffsetDateTime day = EPOCH.plusDays(dayOrdinal);
    return String.format(
        "%04d-%02d-%02d", day.getYear(), day.getMonth().getValue(), day.getDayOfMonth());
  }

  /**
   * 把自午夜起的微秒数格式化为本地时间字符串。
   *
   * @param microsFromMidnight 自午夜起的微秒数
   * @return 本地时间字符串
   */
  static String humanTime(Long microsFromMidnight) {
    return LocalTime.ofNanoOfDay(microsFromMidnight * 1000).toString();
  }

  /**
   * 把微秒时间戳格式化为带时区的 ISO 字符串。
   *
   * @param timestampMicros 自 epoch 起的微秒数
   * @return 带 UTC 时区的 ISO 字符串
   */
  static String humanTimestampWithZone(Long timestampMicros) {
    return ChronoUnit.MICROS.addTo(EPOCH, timestampMicros).toString();
  }

  /**
   * 把微秒时间戳格式化为不带时区的本地日期时间字符串。
   *
   * @param timestampMicros 自 epoch 起的微秒数
   * @return 不带时区的 ISO 字符串
   */
  static String humanTimestampWithoutZone(Long timestampMicros) {
    return ChronoUnit.MICROS.addTo(EPOCH, timestampMicros).toLocalDateTime().toString();
  }

  /**
   * 把小时序号格式化为 "YYYY-MM-DD-HH" 字符串。
   *
   * @param hourOrdinal 自 epoch 起的小时数
   * @return "YYYY-MM-DD-HH" 字符串
   */
  static String humanHour(int hourOrdinal) {
    OffsetDateTime time = EPOCH.plusHours(hourOrdinal);
    return String.format(
        "%04d-%02d-%02d-%02d",
        time.getYear(), time.getMonth().getValue(), time.getDayOfMonth(), time.getHour());
  }

  /**
   * 把 ByteBuffer 做 base64 编码为字符串。
   *
   * <p>设计要点：base64 输出必为 ASCII，故用 ISO-8859-1 直接解码为 String，避免 UTF-8 解码开销。
   *
   * @param buffer 待编码的二进制
   * @return base64 字符串
   */
  static String base64encode(ByteBuffer buffer) {
    // use direct encoding because all of the encoded bytes are in ASCII
    return StandardCharsets.ISO_8859_1.decode(Base64.getEncoder().encode(buffer)).toString();
  }
}
