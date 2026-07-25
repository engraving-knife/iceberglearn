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
 * 文件信息 Bean，用于在 Spark 数据集中传递文件路径与类型。
 *
 * <p>所属模块：iceberg-spark（actions 子包）。作为 Spark Dataset 的元素类型，配合 {@link #ENCODER}（基于 {@link
 * Encoders#bean}）进行编解码，常用于孤儿文件扫描等动作 收集待处理文件清单。
 *
 * <p>设计意图：采用 JavaBean 风格（无参构造 + getter/setter）以满足 Spark bean Encoder 要求。
 */
public class FileInfo {
  public static final Encoder<FileInfo> ENCODER = Encoders.bean(FileInfo.class);

  private String path;
  private String type;

  /** 以指定路径与类型构造。 */
  public FileInfo(String path, String type) {
    this.path = path;
    this.type = type;
  }

  /** 无参构造，供 Spark bean Encoder 使用。 */
  public FileInfo() {}

  /** 返回文件路径。 */
  public String getPath() {
    return path;
  }

  /** 设置文件路径。 */
  public void setPath(String path) {
    this.path = path;
  }

  /** 返回文件类型。 */
  public String getType() {
    return type;
  }

  /** 设置文件类型。 */
  public void setType(String type) {
    this.type = type;
  }
}
