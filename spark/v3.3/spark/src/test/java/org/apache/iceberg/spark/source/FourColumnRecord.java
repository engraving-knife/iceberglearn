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

import java.util.Objects;

/**
 * 文件级说明：测试 FourColumnRecord 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.3）。职责：验证 Iceberg 表在 Spark 引擎下 four列记录 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class FourColumnRecord {
  private Integer c1;
  private String c2;
  private String c3;
  private String c4;

  /** four列记录。 */
  public FourColumnRecord() {}

  /** four列记录。 */
  public FourColumnRecord(Integer c1, String c2, String c3, String c4) {
    this.c1 = c1;
    this.c2 = c2;
    this.c3 = c3;
    this.c4 = c4;
  }

  /** 获取c1。 */
  public Integer getC1() {
    return c1;
  }

  /** 集合c1。 */
  public void setC1(Integer c1) {
    this.c1 = c1;
  }

  /** 获取c2。 */
  public String getC2() {
    return c2;
  }

  /** 集合c2。 */
  public void setC2(String c2) {
    this.c2 = c2;
  }

  /** 获取c3。 */
  public String getC3() {
    return c3;
  }

  /** 集合c3。 */
  public void setC3(String c3) {
    this.c3 = c3;
  }

  /** 获取c4。 */
  public String getC4() {
    return c4;
  }

  /** 集合c4。 */
  public void setC4(String c4) {
    this.c4 = c4;
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
    FourColumnRecord that = (FourColumnRecord) o;
    return Objects.equals(c1, that.c1)
        && Objects.equals(c2, that.c2)
        && Objects.equals(c3, that.c3)
        && Objects.equals(c3, that.c4);
  }

  /** 哈希code。 */
  @Override
  public int hashCode() {
    return Objects.hash(c1, c2, c3, c4);
  }

  /** 到字符串。 */
  @Override
  public String toString() {
    return "ThreeColumnRecord{"
        + "c1="
        + c1
        + ", c2='"
        + c2
        + '\''
        + ", c3='"
        + c3
        + '\''
        + ", c4='"
        + c4
        + '\''
        + '}';
  }
}
