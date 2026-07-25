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
package org.apache.iceberg.spark.functions;

import org.apache.iceberg.IcebergBuild;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.functions.BoundFunction;
import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Spark SQL 函数：返回当前运行时 Iceberg 版本号。
 *
 * <p>所属模块：iceberg-spark（functions 子包，提供可在 Spark SQL 中调用的 Iceberg 函数）。
 *
 * <p>职责：作为 {@link UnboundFunction}，绑定后返回无参标量函数，执行 {@code system.iceberg_version()} 即可得到形如 "0.14.0"
 * 或 "0.15.0-SNAPSHOT" 的版本字符串。
 *
 * <p>设计意图：版本号在实现类中以静态常量缓存，避免每次调用重复计算； 同时提供名为 {@code invoke} 的静态方法，供 Spark whole-stage codegen
 * 直接内联调用， 提升执行效率。实现类不能为 private，否则 Spark 代码生成阶段无法访问静态方法。
 *
 * <p>上下游关系：通过 Iceberg 的 Spark 函数目录注册，供用户在 SQL 中调用。
 */
public class IcebergVersionFunction implements UnboundFunction {
  /**
   * 绑定函数：校验无输入参数后返回标量函数实现。
   *
   * @throws UnsupportedOperationException 当传入参数不为空时抛出
   */
  @Override
  public BoundFunction bind(StructType inputType) {
    if (inputType.fields().length > 0) {
      throw new UnsupportedOperationException(
          String.format("Cannot bind: %s does not accept arguments", name()));
    }

    return new IcebergVersionFunctionImpl();
  }

  /** 返回函数描述，用于 SQL 中 {@code DESCRIBE FUNCTION}。 */
  @Override
  public String description() {
    return name() + " - Returns the runtime Iceberg version";
  }

  /** 返回函数名。 */
  @Override
  public String name() {
    return "iceberg_version";
  }

  /**
   * 已绑定的标量函数实现：返回 Iceberg 版本字符串。
   *
   * <p>设计要点：实现类不能为 private，否则 Spark 代码生成阶段无法访问静态 invoke 方法； {@code invoke} 为 whole-stage codegen
   * 使用的 magic 方法，必须以此命名。
   */
  // Implementing class cannot be private, otherwise Spark is unable to access the static invoke
  // function during code-gen and calling the function fails
  static class IcebergVersionFunctionImpl implements ScalarFunction<UTF8String> {
    private static final UTF8String VERSION = UTF8String.fromString(IcebergBuild.version());

    // magic function used in code-gen. must be named `invoke`.
    public static UTF8String invoke() {
      return VERSION;
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[0];
    }
    /** 返回结果类型。 */
    @Override
    public DataType resultType() {
      return DataTypes.StringType;
    }
    /** 判断是否 ResultNullable。 */
    @Override
    public boolean isResultNullable() {
      return false;
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg." + name();
    }
    /** 返回名称。 */
    @Override
    public String name() {
      return "iceberg_version";
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public UTF8String produceResult(InternalRow input) {
      return invoke();
    }
  }
}
