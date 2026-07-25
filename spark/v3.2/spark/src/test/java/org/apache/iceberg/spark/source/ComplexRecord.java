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

import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Objects;

/**
 * 文件级说明：测试 ComplexRecord 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 复合记录 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class ComplexRecord {
  private long id;
  private NestedRecord struct;

  /** 复合记录。 */
  public ComplexRecord() {}

  /** 复合记录。 */
  public ComplexRecord(long id, NestedRecord struct) {
    this.id = id;
    this.struct = struct;
  }

  /** 获取id。 */
  public long getId() {
    return id;
  }

  /** 集合id。 */
  public void setId(long id) {
    this.id = id;
  }

  /** 获取结构体。 */
  public NestedRecord getStruct() {
    return struct;
  }

  /** 集合结构体。 */
  public void setStruct(NestedRecord struct) {
    this.struct = struct;
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

    ComplexRecord record = (ComplexRecord) o;
    return id == record.id && Objects.equal(struct, record.struct);
  }

  /** 哈希code。 */
  @Override
  public int hashCode() {
    return Objects.hashCode(id, struct);
  }

  /** 到字符串。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("id", id).add("struct", struct).toString();
  }
}
