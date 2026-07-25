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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：文件信息 Bean，以 Spark 可序列化行形式描述一个数据文件的路径、格式、大小等属性。
 *
 * <p>设计意图：作为动作结果集中文件记录的载体，便于 Spark Dataset 处理。
 *
 * <p>上下游关系：由 DeleteOrphanFilesSparkAction / DeleteReachableFilesSparkAction 作为返回行使用。
 */
public class FileInfo {
  public static final Encoder<FileInfo> ENCODER = Encoders.bean(FileInfo.class);

  private String path;
  private String type;

  public FileInfo(String path, String type) {
    this.path = path;
    this.type = type;
  }

  public FileInfo() {}
  /** 返回 Path 属性。 */
  public String getPath() {
    return path;
  }
  /** 设置 Path 属性。 */
  public void setPath(String path) {
    this.path = path;
  }
  /** 返回 Type 属性。 */
  public String getType() {
    return type;
  }
  /** 设置 Type 属性。 */
  public void setType(String type) {
    this.type = type;
  }
}
