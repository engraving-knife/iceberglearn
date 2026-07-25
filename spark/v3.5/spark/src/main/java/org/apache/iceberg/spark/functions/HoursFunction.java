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
import org.apache.spark.sql.types.TimestampNTZType;
import org.apache.spark.sql.types.TimestampType;

/**
 * Iceberg hour 变换的 Spark 函数实现。
 *
 * <p>所属模块：iceberg-spark（functions 子包）。将时间戳列转换为小时值（自 Unix 纪元起的小时数）， 供分区变换与查询使用。用法示例：{@code SELECT
 * system.hours('source_col')}。
 *
 * <p>设计意图：作为未绑定函数，在 bind 时按输入类型（TIMESTAMP/TIMESTAMP_NTZ）选择对应的 已绑定实现，以支持不同时间戳类型；通过 magic method（静态
 * invoke）支持 Spark codegen。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.spark.SparkFunctionCatalog} 暴露；底层调用 {@link
 * DateTimeUtil#microsToHours} 完成换算。
 */
public class HoursFunction extends UnaryUnboundFunction {

  /**
   * 按输入类型绑定：TIMESTAMP→{@link TimestampToHoursFunction}，TIMESTAMP_NTZ→{@link
   * TimestampNtzToHoursFunction}，其余抛异常。
   */
  @Override
  protected BoundFunction doBind(DataType valueType) {
    if (valueType instanceof TimestampType) {
      return new TimestampToHoursFunction();
    } else if (valueType instanceof TimestampNTZType) {
      return new TimestampNtzToHoursFunction();
    } else {
      throw new UnsupportedOperationException(
          "Expected value to be timestamp: " + valueType.catalogString());
    }
  }

  /** 返回函数描述（含用法说明）。 */
  @Override
  public String description() {
    return name()
        + "(col) - Call Iceberg's hour transform\n"
        + "  col :: source column (must be timestamp)";
  }

  /** 返回函数名 hours。 */
  @Override
  public String name() {
    return "hours";
  }

  /** TIMESTAMP→hours 的已绑定标量函数实现。 */
  public static class TimestampToHoursFunction implements ScalarFunction<Integer> {
    // magic method used in codegen
    public static int invoke(long micros) {
      return DateTimeUtil.microsToHours(micros);
    }
    /** 返回名称。 */
    @Override
    public String name() {
      return "hours";
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.TimestampType};
    }
    /** 返回结果类型。 */
    @Override
    public DataType resultType() {
      return DataTypes.IntegerType;
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.hours(timestamp)";
    }

    /** 产出结果：空输入返回 null（与 Spark codegen 行为一致），否则取微秒值调用 {@link #invoke}。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }

  /** TIMESTAMP_NTZ→hours 的已绑定标量函数实现。 */
  public static class TimestampNtzToHoursFunction implements ScalarFunction<Integer> {
    // magic method used in codegen
    public static int invoke(long micros) {
      return DateTimeUtil.microsToHours(micros);
    }
    /** 返回名称。 */
    @Override
    public String name() {
      return "hours";
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.TimestampNTZType};
    }
    /** 返回结果类型。 */
    @Override
    public DataType resultType() {
      return DataTypes.IntegerType;
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.hours(timestamp_ntz)";
    }

    /** 产出结果：空输入返回 null（与 Spark codegen 行为一致），否则取微秒值调用 {@link #invoke}。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }
}
