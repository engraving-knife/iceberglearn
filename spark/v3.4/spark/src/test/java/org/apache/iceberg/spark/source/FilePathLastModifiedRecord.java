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

import java.sql.Timestamp;
import java.util.Objects;

/**
 * 文件级说明：测试 FilePathLastModifiedRecord 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 文件路径最后一个modified记录 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class FilePathLastModifiedRecord {
  private String filePath;
  private Timestamp lastModified;

  /** 文件路径最后一个modified记录。 */
  public FilePathLastModifiedRecord() {}

  /** 文件路径最后一个modified记录。 */
  public FilePathLastModifiedRecord(String filePath, Timestamp lastModified) {
    this.filePath = filePath;
    this.lastModified = lastModified;
  }

  /** 获取文件路径。 */
  public String getFilePath() {
    return filePath;
  }

  /** 集合文件路径。 */
  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  /** 获取最后一个modified。 */
  public Timestamp getLastModified() {
    return lastModified;
  }

  /** 集合最后一个modified。 */
  public void setLastModified(Timestamp lastModified) {
    this.lastModified = lastModified;
  }

  /** 辅助方法：equals。 */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FilePathLastModifiedRecord that = (FilePathLastModifiedRecord) o;
    return Objects.equals(filePath, that.filePath)
        && Objects.equals(lastModified, that.lastModified);
  }

  /** 哈希code。 */
  @Override
  public int hashCode() {
    return Objects.hash(filePath, lastModified);
  }

  /** 到字符串。 */
  @Override
  public String toString() {
    return "FilePathLastModifiedRecord{"
        + "filePath='"
        + filePath
        + '\''
        + ", lastModified='"
        + lastModified
        + '\''
        + '}';
  }
}
