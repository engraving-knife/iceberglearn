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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Iceberg hours 转换的 Spark 标量函数，将时间戳值转为自纪元以来的小时数。
 *
 * <p>设计意图：实现 Iceberg hours transform，用于按小时分区。
 *
 * <p>上下游关系：由 SparkFunctions / SparkFunctionCatalog 注册。
 */
public class HoursFunction extends UnaryUnboundFunction {
  /** 执行 doBind 相关操作。 */
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
  /** 返回描述。 */
  @Override
  public String description() {
    return name()
        + "(col) - Call Iceberg's hour transform\n"
        + "  col :: source column (must be timestamp)";
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return "hours";
  }

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
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }

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
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in codegen
      return input.isNullAt(0) ? null : invoke(input.getLong(0));
    }
  }
}
