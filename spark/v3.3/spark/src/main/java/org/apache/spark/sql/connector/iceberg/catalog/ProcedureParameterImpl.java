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
package org.apache.spark.sql.connector.iceberg.catalog;

import java.util.Objects;
import org.apache.spark.sql.types.DataType;

/**
 * Spark DataSource V2 连接器扩展，封装为可通过 SQL CALL 调用的存储过程。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 ProcedureParameterImpl。
 *
 * <p>设计意图：实现类，提供具体行为。
 *
 * <p>上下游：由 DataSource V2 框架调用，桥接 Spark 与 Iceberg。
 */
class ProcedureParameterImpl implements ProcedureParameter {
  private final String name;
  private final DataType dataType;
  private final boolean required;

  ProcedureParameterImpl(String name, DataType dataType, boolean required) {
    this.name = name;
    this.dataType = dataType;
    this.required = required;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String name() {
    return name;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public DataType dataType() {
    return dataType;
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public boolean required() {
    return required;
  }

  /** 判断是否与给定对象相等。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other == null || getClass() != other.getClass()) {
      return false;
    }

    ProcedureParameterImpl that = (ProcedureParameterImpl) other;
    return required == that.required
        && Objects.equals(name, that.name)
        && Objects.equals(dataType, that.dataType);
  }

  /** 返回该对象的哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(name, dataType, required);
  }

  /** 返回该对象的字符串表示。 */
  @Override
  public String toString() {
    return String.format(
        "ProcedureParameter(name='%s', type=%s, required=%b)", name, dataType, required);
  }
}
