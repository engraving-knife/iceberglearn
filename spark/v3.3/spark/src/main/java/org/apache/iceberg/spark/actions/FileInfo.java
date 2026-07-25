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
package org.apache.iceberg.spark.actions;

import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;

/**
 * 基于 Spark 执行的 Iceberg 表维护动作。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 FileInfo。
 *
 * <p>上下游：由 SparkActions 创建，委托 Spark 作业执行实际数据处理。
 */
public class FileInfo {
  public static final Encoder<FileInfo> ENCODER = Encoders.bean(FileInfo.class);

  private String path;
  private String type;

  /** 构造 FileInfo 实例。 */
  public FileInfo(String path, String type) {
    this.path = path;
    this.type = type;
  }

  /** 构造 FileInfo 实例。 */
  public FileInfo() {}

  /** 返回path。 */
  public String getPath() {
    return path;
  }

  /** 设置path。 */
  public void setPath(String path) {
    this.path = path;
  }

  /** 返回type。 */
  public String getType() {
    return type;
  }

  /** 设置type。 */
  public void setType(String type) {
    this.type = type;
  }
}
