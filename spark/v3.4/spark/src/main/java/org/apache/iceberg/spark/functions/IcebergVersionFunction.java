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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：返回当前 Iceberg 版本号的 Spark 标量函数。
 *
 * <p>设计意图：提供运行时查询 Iceberg 版本的能力，便于排查环境。
 *
 * <p>上下游关系：由 SparkFunctions / SparkFunctionCatalog 注册。
 */
public class IcebergVersionFunction implements UnboundFunction {
  /** 绑定输入类型。 */
  @Override
  public BoundFunction bind(StructType inputType) {
    if (inputType.fields().length > 0) {
      throw new UnsupportedOperationException(
          String.format("Cannot bind: %s does not accept arguments", name()));
    }

    return new IcebergVersionFunctionImpl();
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return name() + " - Returns the runtime Iceberg version";
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return "iceberg_version";
  }

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
