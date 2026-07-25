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

import org.apache.iceberg.relocated.com.google.common.base.Objects;

/**
 * 文件级说明：测试 SimpleRecord 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 simple记录 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class SimpleRecord {
  private Integer id;
  private String data;

  /** simple记录。 */
  public SimpleRecord() {}

  /** simple记录。 */
  public SimpleRecord(Integer id, String data) {
    this.id = id;
    this.data = data;
  }

  /** 获取id。 */
  public Integer getId() {
    return id;
  }

  /** 集合id。 */
  public void setId(Integer id) {
    this.id = id;
  }

  /** 获取数据。 */
  public String getData() {
    return data;
  }

  /** 集合数据。 */
  public void setData(String data) {
    this.data = data;
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

    SimpleRecord record = (SimpleRecord) o;
    return Objects.equal(id, record.id) && Objects.equal(data, record.data);
  }

  /** 哈希code。 */
  @Override
  public int hashCode() {
    return Objects.hashCode(id, data);
  }

  /** 到字符串。 */
  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("{\"id\"=");
    buffer.append(id);
    buffer.append(",\"data\"=\"");
    buffer.append(data);
    buffer.append("\"}");
    return buffer.toString();
  }
}
