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
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：ProcedureParameter 的默认实现类。
 *
 * <p>设计意图：提供不可变参数实例的构造与基本访问。
 *
 * <p>上下游关系：由各 Procedure 在声明参数时使用。
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
  /** 返回名称。 */
  @Override
  public String name() {
    return name;
  }
  /** 执行 dataType 相关操作。 */
  @Override
  public DataType dataType() {
    return dataType;
  }
  /** 执行 required 相关操作。 */
  @Override
  public boolean required() {
    return required;
  }
  /** 判断是否相等。 */
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
  /** 返回哈希码。 */
  @Override
  public int hashCode() {
    return Objects.hash(name, dataType, required);
  }
  /** 返回字符串表示。 */
  @Override
  public String toString() {
    return String.format(
        "ProcedureParameter(name='%s', type=%s, required=%b)", name, dataType, required);
  }
}
