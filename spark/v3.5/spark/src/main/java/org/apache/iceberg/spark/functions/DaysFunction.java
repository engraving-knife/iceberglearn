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
import org.apache.spark.sql.types.TimestampNTZType;
import org.apache.spark.sql.types.TimestampType;

/**
 * Iceberg day 变换的 Spark 函数实现。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），functions 子包。
 *
 * <p>职责：把 date/timestamp 列转换为按天分区的日期值（自 1970-01-01 起的天数）， 对应 Iceberg 的 day transform，用于分区计算。
 *
 * <p>设计意图：作为未绑定函数（{@link UnaryUnboundFunction}），在 bind 阶段根据输入类型 返回对应的
 * BoundFunction（Date/Timestamp/TimestampNTZ），各自提供 magic method 用于 Spark codegen。 Spark 与 Iceberg 内部
 * date 表示一致，date 分支直接透传；timestamp 用 {@link DateTimeUtil#microsToDays} 转换。
 *
 * <p>上下游关系：由 {@link SparkFunctions} 注册并通过 system 命名空间暴露；被 Spark SQL 调用。
 *
 * <p>示例：{@code SELECT system.days('source_col')}
 */
public class DaysFunction extends UnaryUnboundFunction {

  /**
   * 根据输入类型绑定具体实现。
   *
   * <p>逻辑：DateType -> DateToDaysFunction，TimestampType -> TimestampToDaysFunction， TimestampNTZType
   * -> TimestampNtzToDaysFunction，其余抛出 UnsupportedOperationException。
   *
   * @param valueType 输入列类型
   * @return 对应的 BoundFunction
   */
  @Override
  protected BoundFunction doBind(DataType valueType) {
    if (valueType instanceof DateType) {
      return new DateToDaysFunction();
    } else if (valueType instanceof TimestampType) {
      return new TimestampToDaysFunction();
    } else if (valueType instanceof TimestampNTZType) {
      return new TimestampNtzToDaysFunction();
    } else {
      throw new UnsupportedOperationException(
          "Expected value to be date or timestamp: " + valueType.catalogString());
    }
  }

  /** 返回函数描述，含用法说明。 */
  @Override
  public String description() {
    return name()
        + "(col) - Call Iceberg's day transform\n"
        + "  col :: source column (must be date or timestamp)";
  }

  /** 返回函数名 "days"。 */
  @Override
  public String name() {
    return "days";
  }

  /** days 函数的抽象基类，统一 name 与 resultType（DateType）。 */
  private abstract static class BaseToDaysFunction implements ScalarFunction<Integer> {
    /** 返回名称。 */
    @Override
    public String name() {
      return "days";
    }
    /** 返回结果类型。 */
    @Override
    public DataType resultType() {
      return DataTypes.DateType;
    }
  }

  /**
   * date -> days 的绑定实现。
   *
   * <p>设计要点：Spark 与 Iceberg 内部 date 表示一致，invoke 直接透传；提供 magic method {@link #invoke(int)} 供 Spark
   * codegen 内联调用。
   */
  // Spark and Iceberg internal representations of dates match so no transformation is required
  public static class DateToDaysFunction extends BaseToDaysFunction {
    // magic method used in codegen
    public static int invoke(int days) {
      return days;
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.DateType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.days(date)";
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : input.getInt(0);
    }
  }

  /** timestamp(带时区) -> days 的绑定实现，用 {@link DateTimeUtil#microsToDays} 转换。 */
  public static class TimestampToDaysFunction extends BaseToDaysFunction {
    // magic method used in codegen
    public static int invoke(long micros) {
      return DateTimeUtil.microsToDays(micros);
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.TimestampType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.days(timestamp)";
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }

  /** timestamp_ntz(无时区) -> days 的绑定实现，同样用 {@link DateTimeUtil#microsToDays} 转换。 */
  public static class TimestampNtzToDaysFunction extends BaseToDaysFunction {
    // magic method used in codegen
    public static int invoke(long micros) {
      return DateTimeUtil.microsToDays(micros);
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.TimestampNTZType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.days(timestamp_ntz)";
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }
}
