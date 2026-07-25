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
 * 文件级说明：测试 NestedRecord 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Iceberg 表在 Spark 引擎下 嵌套记录 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class NestedRecord {
  private long innerId;
  private String innerName;

  /** 嵌套记录。 */
  public NestedRecord() {}

  /** 嵌套记录。 */
  public NestedRecord(long innerId, String innerName) {
    this.innerId = innerId;
    this.innerName = innerName;
  }

  /** 获取内id。 */
  public long getInnerId() {
    return innerId;
  }

  /** 获取内name。 */
  public String getInnerName() {
    return innerName;
  }

  /** 集合内id。 */
  public void setInnerId(long iId) {
    innerId = iId;
  }

  /** 集合内name。 */
  public void setInnerName(String name) {
    innerName = name;
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

    NestedRecord that = (NestedRecord) o;
    return innerId == that.innerId && Objects.equal(innerName, that.innerName);
  }

  /** 哈希code。 */
  @Override
  public int hashCode() {
    return Objects.hashCode(innerId, innerName);
  }

  /** 到字符串。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("innerId", innerId)
        .add("innerName", innerName)
        .toString();
  }
}
