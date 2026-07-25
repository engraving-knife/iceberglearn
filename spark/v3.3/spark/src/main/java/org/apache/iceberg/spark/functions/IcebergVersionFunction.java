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
 * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 IcebergVersionFunction。
 *
 * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
 */
public class IcebergVersionFunction implements UnboundFunction {
  /**
   * 执行该方法的具体逻辑。
   *
   * @param inputType 参数
   * @return 结果对象
   */
  @Override
  public BoundFunction bind(StructType inputType) {
    if (inputType.fields().length > 0) {
      throw new UnsupportedOperationException(
          String.format("Cannot bind: %s does not accept arguments", name()));
    }

    /** 执行该方法的具体逻辑。 */
    return new IcebergVersionFunctionImpl();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return name() + " - Returns the runtime Iceberg version";
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String name() {
    return "iceberg_version";
  }

  // Implementing class cannot be private, otherwise Spark is unable to access the static invoke
  // function during code-gen and calling the function fails
  /**
   * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 IcebergVersionFunctionImpl。
   *
   * <p>设计意图：实现类，提供具体行为。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  static class IcebergVersionFunctionImpl implements ScalarFunction<UTF8String> {
    private static final UTF8String VERSION = UTF8String.fromString(IcebergBuild.version());

    // magic function used in code-gen. must be named `invoke`.
    /** 执行该方法的具体逻辑。 */
    public static UTF8String invoke() {
      return VERSION;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[0];
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType resultType() {
      return DataTypes.StringType;
    }

    /** 判断是否resultnullable。 */
    @Override
    public boolean isResultNullable() {
      return false;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg." + name();
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public String name() {
      return "iceberg_version";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public UTF8String produceResult(InternalRow input) {
      return invoke();
    }
  }
}
