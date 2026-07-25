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
 * Iceberg years 变换的 Spark 函数实现。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，functions 子包负责 将 Iceberg 的变换函数注册为 Spark 系统函数）。
 *
 * <p>职责：实现 Iceberg 的 year 变换，将日期或时间戳值转换为自 Unix 纪元（1970-01-01） 起的年份数。例如 {@code SELECT
 * system.years('source_col')}。
 *
 * <p>设计意图：继承 {@link UnaryUnboundFunction}，在 bind 阶段根据输入类型（date、 timestamp、timestamp_ntz）选择对应的
 * BoundFunction 子类。每个子类提供静态 magic method（invoke）供 Spark codegen 内联调用。years 变换常用于按年分区。
 *
 * <p>上下游关系：通过 SparkFunctions 注册到 Spark catalog；依赖 Iceberg 的 {@link
 * org.apache.iceberg.util.DateTimeUtil} 进行日期到年份的转换。
 */
public class YearsFunction extends UnaryUnboundFunction {

  /**
   * 根据输入值类型绑定具体的 years 函数实现。
   *
   * <p>逻辑：date -> DateToYearsFunction，timestamp -> TimestampToYearsFunction， timestamp_ntz ->
   * TimestampNtzToYearsFunction，其他类型抛出异常。
   *
   * @param valueType 输入值类型
   * @return 绑定后的具体函数实现
   * @throws UnsupportedOperationException 输入类型非 date/timestamp 时抛出
   */
  @Override
  protected BoundFunction doBind(DataType valueType) {
    if (valueType instanceof DateType) {
      return new DateToYearsFunction();
    } else if (valueType instanceof TimestampType) {
      return new TimestampToYearsFunction();
    } else if (valueType instanceof TimestampNTZType) {
      return new TimestampNtzToYearsFunction();
    } else {
      throw new UnsupportedOperationException(
          "Expected value to be date or timestamp: " + valueType.catalogString());
    }
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return name()
        + "(col) - Call Iceberg's year transform\n"
        + "  col :: source column (must be date or timestamp)";
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return "years";
  }

  private abstract static class BaseToYearsFunction implements ScalarFunction<Integer> {
    /** 返回名称。 */
    @Override
    public String name() {
      return "years";
    }
    /** 返回结果类型。 */
    @Override
    public DataType resultType() {
      return DataTypes.IntegerType;
    }
  }

  public static class DateToYearsFunction extends BaseToYearsFunction {
    // magic method used in codegen
    public static int invoke(int days) {
      return DateTimeUtil.daysToYears(days);
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.DateType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.years(date)";
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getInt(0));
    }
  }

  public static class TimestampToYearsFunction extends BaseToYearsFunction {
    // magic method used in codegen
    public static int invoke(long micros) {
      return DateTimeUtil.microsToYears(micros);
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.TimestampType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.years(timestamp)";
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }

  public static class TimestampNtzToYearsFunction extends BaseToYearsFunction {
    // magic method used in codegen
    public static int invoke(long micros) {
      return DateTimeUtil.microsToYears(micros);
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.TimestampNTZType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.years(timestamp_ntz)";
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }
}
