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

import org.apache.iceberg.util.DateTimeUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.functions.BoundFunction;
import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.TimestampType;

/**
 * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 DaysFunction。
 *
 * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
 */
public class DaysFunction extends UnaryUnboundFunction {

  /** 执行该方法的具体逻辑。 */
  @Override
  protected BoundFunction doBind(DataType valueType) {
    if (valueType instanceof DateType) {
      /** 执行该方法的具体逻辑。 */
      return new DateToDaysFunction();
    } else if (valueType instanceof TimestampType) {
      /** 执行该方法的具体逻辑。 */
      return new TimestampToDaysFunction();
    } else {
      throw new UnsupportedOperationException(
          "Expected value to be date or timestamp: " + valueType.catalogString());
    }
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String description() {
    return name()
        + "(col) - Call Iceberg's day transform\n"
        + "  col :: source column (must be date or timestamp)";
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String name() {
    return "days";
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 BaseToDaysFunction。
   *
   * <p>设计意图：模板方法模式，抽取公共流程供子类复用。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  private abstract static class BaseToDaysFunction implements ScalarFunction<Integer> {
    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public String name() {
      return "days";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType resultType() {
      return DataTypes.DateType;
    }
  }

  // Spark and Iceberg internal representations of dates match so no transformation is required
  /**
   * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 DateToDaysFunction。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class DateToDaysFunction extends BaseToDaysFunction {
    // magic method used in codegen
    /** 执行该方法的具体逻辑。 */
    public static int invoke(int days) {
      return days;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.DateType};
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg.days(date)";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : input.getInt(0);
    }
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TimestampToDaysFunction。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class TimestampToDaysFunction extends BaseToDaysFunction {
    // magic method used in codegen
    /** 执行该方法的具体逻辑。 */
    public static int invoke(long micros) {
      return DateTimeUtil.microsToDays(micros);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.TimestampType};
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg.days(timestamp)";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }
}
