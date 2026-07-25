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
package org.apache.iceberg.spark.extensions;

import java.util.Objects;

/**
 * 文件级说明：测试 Employee 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.2）。职责：验证 Employee 在 Spark 引擎下的行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class Employee {
  private Integer id;
  private String dep;

  /** 辅助方法：Employee。 */
  public Employee() {}

  /** 辅助方法：Employee。 */
  public Employee(Integer id, String dep) {
    this.id = id;
    this.dep = dep;
  }

  /** 获取id。 */
  public Integer getId() {
    return id;
  }

  /** 集合id。 */
  public void setId(Integer id) {
    this.id = id;
  }

  /** 获取dep。 */
  public String getDep() {
    return dep;
  }

  /** 集合dep。 */
  public void setDep(String dep) {
    this.dep = dep;
  }

  /** 辅助方法：equals。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other == null || getClass() != other.getClass()) {
      return false;
    }

    Employee employee = (Employee) other;
    return Objects.equals(id, employee.id) && Objects.equals(dep, employee.dep);
  }

  /** 哈希code。 */
  @Override
  public int hashCode() {
    return Objects.hash(id, dep);
  }
}
