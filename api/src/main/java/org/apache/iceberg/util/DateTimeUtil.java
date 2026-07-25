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
package org.apache.iceberg.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;

/**
 * 日期时间工具类：在 Iceberg 内部时间表示（自 epoch 起的天数/微秒）与 JDK 时间类型 （{@link LocalDate}/{@link LocalTime}/{@link
 * LocalDateTime}/{@link OffsetDateTime}）及 ISO 字符串之间进行转换。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 date/time/timestamp/timestamptz 类型在 Iceberg 存储格式（int 天/long 微秒）与 JDK 类型之间的双向转换。
 *   <li>支持 ISO 字符串与内部数值表示的互转，便于序列化与展示。
 *   <li>提供把天数/微秒折算为年/月/日/时等粗粒度单位的工具，用于 year/month/day/hour 分区转换。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>Iceberg 规范：date 存为自 1970-01-01 起的天数（int），time/timestamp 存为微秒（long）， 避免时区与精度歧义；本类集中实现这套约定。
 *   <li>负值处理：epoch 之前的时间用负数表示，转换时使用 {@link Math#floorDiv} 等向负无穷取整， 并对年/月折算额外修正 1 个单位的偏差，保证历史时间映射正确。
 *   <li>timestamptz 统一以 UTC 表示，格式化时把 ISO 的 'Z' 替换为 '+00:00' 以保持一致展示。
 * </ul>
 *
 * <p>上下游关系：被 core 模块的时间分区转换、表达式求值、字面量解析等逻辑广泛调用。
 */
public class DateTimeUtil {
  private DateTimeUtil() {}

  /** UTC 时区的 epoch 时刻（1970-01-01T00:00:00Z），作为各类时间换算的基准。 */
  public static final OffsetDateTime EPOCH = Instant.ofEpochSecond(0).atOffset(ZoneOffset.UTC);
  /** epoch 对应的本地日期，作为 date 类型天数换算的基准。 */
  public static final LocalDate EPOCH_DAY = EPOCH.toLocalDate();
  /** 每毫秒的微秒数。 */
  public static final long MICROS_PER_MILLIS = 1000L;
  /** 每秒的微秒数。 */
  public static final long MICROS_PER_SECOND = 1_000_000L;

  /**
   * 将自 epoch 起的天数转换为 {@link LocalDate}。
   *
   * @param daysFromEpoch 自 epoch 起的天数，可为负
   * @return 对应的本地日期
   */
  public static LocalDate dateFromDays(int daysFromEpoch) {
    return ChronoUnit.DAYS.addTo(EPOCH_DAY, daysFromEpoch);
  }

  /**
   * 将 {@link LocalDate} 转换为自 epoch 起的天数。
   *
   * @param date 本地日期
   * @return 自 epoch 起的天数
   */
  public static int daysFromDate(LocalDate date) {
    return (int) ChronoUnit.DAYS.between(EPOCH_DAY, date);
  }

  /**
   * 将 {@link Instant} 转换为自 epoch 起的天数。
   *
   * @param instant 时刻
   * @return 自 epoch 起的天数
   */
  public static int daysFromInstant(Instant instant) {
    return (int) ChronoUnit.DAYS.between(EPOCH, instant.atOffset(ZoneOffset.UTC));
  }

  /**
   * 将自午夜起的微秒数转换为 {@link LocalTime}。
   *
   * @param microFromMidnight 自午夜起的微秒数
   * @return 对应的本地时间
   */
  public static LocalTime timeFromMicros(long microFromMidnight) {
    return LocalTime.ofNanoOfDay(microFromMidnight * 1000);
  }

  /**
   * 将 {@link LocalTime} 转换为自午夜起的微秒数。
   *
   * @param time 本地时间
   * @return 自午夜起的微秒数
   */
  public static long microsFromTime(LocalTime time) {
    return time.toNanoOfDay() / 1000;
  }

  /**
   * 将自 epoch 起的微秒数转换为 {@link LocalDateTime}（按 UTC 解读）。
   *
   * @param microsFromEpoch 自 epoch 起的微秒数
   * @return 对应的本地日期时间
   */
  public static LocalDateTime timestampFromMicros(long microsFromEpoch) {
    return ChronoUnit.MICROS.addTo(EPOCH, microsFromEpoch).toLocalDateTime();
  }

  /**
   * 将 {@link Instant} 转换为自 epoch 起的微秒数。
   *
   * @param instant 时刻
   * @return 自 epoch 起的微秒数
   */
  public static long microsFromInstant(Instant instant) {
    return ChronoUnit.MICROS.between(EPOCH, instant.atOffset(ZoneOffset.UTC));
  }

  /**
   * 将 {@link LocalDateTime}（按 UTC 解读）转换为自 epoch 起的微秒数。
   *
   * @param dateTime 本地日期时间
   * @return 自 epoch 起的微秒数
   */
  public static long microsFromTimestamp(LocalDateTime dateTime) {
    return ChronoUnit.MICROS.between(EPOCH, dateTime.atOffset(ZoneOffset.UTC));
  }

  /**
   * 将微秒精度的时间戳截断为毫秒精度。
   *
   * <p>逻辑：使用 {@link Math#floorDiv} 向负无穷取整，以正确处理 epoch 之前的负时间戳。 例如 -157700927876544 微秒应截断为
   * -157700927877 毫秒。
   *
   * @param micros 微秒时间戳
   * @return 毫秒时间戳
   */
  public static long microsToMillis(long micros) {
    // When the timestamp is negative, i.e before 1970, we need to adjust the milliseconds portion.
    // Example - 1965-01-01 10:11:12.123456 is represented as (-157700927876544) in micro precision.
    // In millis precision the above needs to be represented as (-157700927877).
    return Math.floorDiv(micros, MICROS_PER_MILLIS);
  }

  /**
   * 将自 epoch 起的微秒数转换为带时区的 {@link OffsetDateTime}（UTC）。
   *
   * @param microsFromEpoch 自 epoch 起的微秒数
   * @return 对应的 UTC 偏移日期时间
   */
  public static OffsetDateTime timestamptzFromMicros(long microsFromEpoch) {
    return ChronoUnit.MICROS.addTo(EPOCH, microsFromEpoch);
  }

  /**
   * 将 {@link OffsetDateTime} 转换为自 epoch 起的微秒数。
   *
   * @param dateTime 带时区偏移的日期时间
   * @return 自 epoch 起的微秒数
   */
  public static long microsFromTimestamptz(OffsetDateTime dateTime) {
    return ChronoUnit.MICROS.between(EPOCH, dateTime);
  }

  /**
   * 将毫秒时间戳格式化为 ISO 字符串（UTC，偏移显示为 +00:00）。
   *
   * @param millis 毫秒时间戳
   * @return ISO 格式字符串
   */
  public static String formatTimestampMillis(long millis) {
    return Instant.ofEpochMilli(millis).toString().replace("Z", "+00:00");
  }

  /**
   * 将自 epoch 起的天数格式化为 ISO 本地日期字符串。
   *
   * @param days 自 epoch 起的天数
   * @return ISO 日期字符串（如 2020-01-01）
   */
  public static String daysToIsoDate(int days) {
    return dateFromDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  /**
   * 将自午夜起的微秒数格式化为 ISO 本地时间字符串。
   *
   * @param micros 自午夜起的微秒数
   * @return ISO 时间字符串
   */
  public static String microsToIsoTime(long micros) {
    return timeFromMicros(micros).format(DateTimeFormatter.ISO_LOCAL_TIME);
  }

  /**
   * 将自 epoch 起的微秒数格式化为带 UTC 偏移的 ISO 时间戳字符串。
   *
   * <p>逻辑：先转为 LocalDateTime，再用自定义 formatter（大小写不敏感、偏移格式 +HH:MM:ss， 零偏移显示为 +00:00）格式化为带偏移的字符串。
   *
   * @param micros 自 epoch 起的微秒数
   * @return 带偏移的 ISO 时间戳字符串
   */
  public static String microsToIsoTimestamptz(long micros) {
    LocalDateTime localDateTime = timestampFromMicros(micros);
    DateTimeFormatter zeroOffsetFormatter =
        new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffset("+HH:MM:ss", "+00:00")
            .toFormatter();
    return localDateTime.atOffset(ZoneOffset.UTC).format(zeroOffsetFormatter);
  }

  /**
   * 将自 epoch 起的微秒数格式化为 ISO 本地日期时间字符串（无时区）。
   *
   * @param micros 自 epoch 起的微秒数
   * @return ISO 本地日期时间字符串
   */
  public static String microsToIsoTimestamp(long micros) {
    LocalDateTime localDateTime = timestampFromMicros(micros);
    return localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  /**
   * 将 ISO 日期字符串解析为自 epoch 起的天数。
   *
   * @param dateString ISO 日期字符串
   * @return 自 epoch 起的天数
   */
  public static int isoDateToDays(String dateString) {
    return daysFromDate(LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE));
  }

  /**
   * 将 ISO 时间字符串解析为自午夜起的微秒数。
   *
   * @param timeString ISO 时间字符串
   * @return 自午夜起的微秒数
   */
  public static long isoTimeToMicros(String timeString) {
    return microsFromTime(LocalTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_TIME));
  }

  /**
   * 将带偏移的 ISO 时间戳字符串解析为自 epoch 起的微秒数。
   *
   * @param timestampString 带偏移的 ISO 时间戳字符串
   * @return 自 epoch 起的微秒数
   */
  public static long isoTimestamptzToMicros(String timestampString) {
    return microsFromTimestamptz(
        OffsetDateTime.parse(timestampString, DateTimeFormatter.ISO_DATE_TIME));
  }

  /**
   * 判断带偏移的 ISO 时间戳字符串是否为 UTC 时区。
   *
   * @param timestampString 带偏移的 ISO 时间戳字符串
   * @return 若偏移为 UTC 则返回 true
   */
  public static boolean isUTCTimestamptz(String timestampString) {
    OffsetDateTime offsetDateTime =
        OffsetDateTime.parse(timestampString, DateTimeFormatter.ISO_DATE_TIME);
    return offsetDateTime.getOffset().equals(ZoneOffset.UTC);
  }

  /**
   * 将无时区的 ISO 时间戳字符串解析为自 epoch 起的微秒数（按 UTC 解读）。
   *
   * @param timestampString ISO 本地日期时间字符串
   * @return 自 epoch 起的微秒数
   */
  public static long isoTimestampToMicros(String timestampString) {
    return microsFromTimestamp(
        LocalDateTime.parse(timestampString, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
  }

  /**
   * 将自 epoch 起的天数折算为年数（用于 year 分区转换）。
   *
   * @param days 自 epoch 起的天数
   * @return 对应的年数
   */
  public static int daysToYears(int days) {
    return convertDays(days, ChronoUnit.YEARS);
  }

  /**
   * 将自 epoch 起的天数折算为月数（用于 month 分区转换）。
   *
   * @param days 自 epoch 起的天数
   * @return 对应的月数
   */
  public static int daysToMonths(int days) {
    return convertDays(days, ChronoUnit.MONTHS);
  }

  /**
   * 将自 epoch 起的天数折算为指定粒度（年/月）的整数值，正确处理 epoch 之前的负值。
   *
   * <p>逻辑：非负天数直接 plusDays 后计算粒度差；负天数则先 plusDays(days+1) 再将结果减 1， 以修正"恰好差 1 个单位"的边界偏差（因为负值结果会被统一减
   * 1）。
   *
   * @param days 自 epoch 起的天数
   * @param granularity 目标粒度（YEARS 或 MONTHS）
   * @return 折算后的整数
   */
  private static int convertDays(int days, ChronoUnit granularity) {
    if (days >= 0) {
      LocalDate date = EPOCH_DAY.plusDays(days);
      return (int) granularity.between(EPOCH_DAY, date);
    } else {
      // add 1 day to the value to account for the case where there is exactly 1 unit between the
      // date and epoch because the result will always be decremented.
      LocalDate date = EPOCH_DAY.plusDays(days + 1);
      return (int) granularity.between(EPOCH_DAY, date) - 1;
    }
  }

  /**
   * 将微秒时间戳折算为年数。
   *
   * @param micros 微秒时间戳
   * @return 对应的年数
   */
  public static int microsToYears(long micros) {
    return convertMicros(micros, ChronoUnit.YEARS);
  }

  /**
   * 将微秒时间戳折算为月数。
   *
   * @param micros 微秒时间戳
   * @return 对应的月数
   */
  public static int microsToMonths(long micros) {
    return convertMicros(micros, ChronoUnit.MONTHS);
  }

  /**
   * 将微秒时间戳折算为天数。
   *
   * @param micros 微秒时间戳
   * @return 对应的天数
   */
  public static int microsToDays(long micros) {
    return convertMicros(micros, ChronoUnit.DAYS);
  }

  /**
   * 将微秒时间戳折算为小时数。
   *
   * @param micros 微秒时间戳
   * @return 对应的小时数
   */
  public static int microsToHours(long micros) {
    return convertMicros(micros, ChronoUnit.HOURS);
  }

  /**
   * 将微秒时间戳折算为指定粒度（年/月/日/时）的整数值，正确处理 epoch 之前的负值。
   *
   * <p>逻辑：非负值用 floorDiv/floorMod 拆为秒+纳秒，构造 OffsetDateTime 后计算粒度差； 负值则对 micros+1 做相同拆分并将结果减
   * 1，修正边界偏差。
   *
   * @param micros 微秒时间戳
   * @param granularity 目标粒度
   * @return 折算后的整数
   */
  private static int convertMicros(long micros, ChronoUnit granularity) {
    if (micros >= 0) {
      long epochSecond = Math.floorDiv(micros, MICROS_PER_SECOND);
      long nanoAdjustment = Math.floorMod(micros, MICROS_PER_SECOND) * 1000;
      return (int) granularity.between(EPOCH, toOffsetDateTime(epochSecond, nanoAdjustment));
    } else {
      // add 1 micro to the value to account for the case where there is exactly 1 unit between
      // the timestamp and epoch because the result will always be decremented.
      long epochSecond = Math.floorDiv(micros, MICROS_PER_SECOND);
      long nanoAdjustment = Math.floorMod(micros + 1, MICROS_PER_SECOND) * 1000;
      return (int) granularity.between(EPOCH, toOffsetDateTime(epochSecond, nanoAdjustment)) - 1;
    }
  }

  /**
   * 由 epoch 秒与纳秒调整量构造 UTC 的 {@link OffsetDateTime}。
   *
   * @param epochSecond 自 epoch 起的秒数
   * @param nanoAdjustment 纳秒调整量
   * @return UTC 偏移日期时间
   */
  private static OffsetDateTime toOffsetDateTime(long epochSecond, long nanoAdjustment) {
    return Instant.ofEpochSecond(epochSecond, nanoAdjustment).atOffset(ZoneOffset.UTC);
  }
}
