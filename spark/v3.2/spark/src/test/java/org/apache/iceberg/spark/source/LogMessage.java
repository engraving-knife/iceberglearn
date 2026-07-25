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
package org.apache.iceberg.spark.source;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件级说明：测试 LogMessage 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 日志message 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class LogMessage {
  private static AtomicInteger idCounter = new AtomicInteger(0);

  /** 辅助方法：debug。 */
  static LogMessage debug(String date, String message) {
    return new LogMessage(idCounter.getAndIncrement(), date, "DEBUG", message);
  }

  /** 辅助方法：debug。 */
  static LogMessage debug(String date, String message, Instant timestamp) {
    return new LogMessage(idCounter.getAndIncrement(), date, "DEBUG", message, timestamp);
  }

  /** 辅助方法：info。 */
  static LogMessage info(String date, String message) {
    return new LogMessage(idCounter.getAndIncrement(), date, "INFO", message);
  }

  /** 辅助方法：info。 */
  static LogMessage info(String date, String message, Instant timestamp) {
    return new LogMessage(idCounter.getAndIncrement(), date, "INFO", message, timestamp);
  }

  /** 辅助方法：error。 */
  static LogMessage error(String date, String message) {
    return new LogMessage(idCounter.getAndIncrement(), date, "ERROR", message);
  }

  /** 辅助方法：error。 */
  static LogMessage error(String date, String message, Instant timestamp) {
    return new LogMessage(idCounter.getAndIncrement(), date, "ERROR", message, timestamp);
  }

  /** 辅助方法：warn。 */
  static LogMessage warn(String date, String message) {
    return new LogMessage(idCounter.getAndIncrement(), date, "WARN", message);
  }

  /** 辅助方法：warn。 */
  static LogMessage warn(String date, String message, Instant timestamp) {
    return new LogMessage(idCounter.getAndIncrement(), date, "WARN", message, timestamp);
  }

  private int id;
  private String date;
  private String level;
  private String message;
  private Instant timestamp;

  /** 日志message。 */
  private LogMessage(int id, String date, String level, String message) {
    this.id = id;
    this.date = date;
    this.level = level;
    this.message = message;
  }

  /** 日志message。 */
  private LogMessage(int id, String date, String level, String message, Instant timestamp) {
    this.id = id;
    this.date = date;
    this.level = level;
    this.message = message;
    this.timestamp = timestamp;
  }

  /** 获取id。 */
  public int getId() {
    return id;
  }

  /** 集合id。 */
  public void setId(int id) {
    this.id = id;
  }

  /** 获取日期。 */
  public String getDate() {
    return date;
  }

  /** 集合日期。 */
  public void setDate(String date) {
    this.date = date;
  }

  /** 获取级别。 */
  public String getLevel() {
    return level;
  }

  /** 集合级别。 */
  public void setLevel(String level) {
    this.level = level;
  }

  /** 获取message。 */
  public String getMessage() {
    return message;
  }

  /** 集合message。 */
  public void setMessage(String message) {
    this.message = message;
  }

  /** 获取时间戳。 */
  public Instant getTimestamp() {
    return timestamp;
  }

  /** 集合时间戳。 */
  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
