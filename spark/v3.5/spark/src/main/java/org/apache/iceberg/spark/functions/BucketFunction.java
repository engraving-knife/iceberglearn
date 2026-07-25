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

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.util.BucketUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.functions.BoundFunction;
import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;
import org.apache.spark.sql.types.BinaryType;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.types.TimestampNTZType;
import org.apache.spark.sql.types.TimestampType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Iceberg bucket 变换的 Spark 函数实现。
 *
 * <p>所属模块：iceberg-spark（Iceberg 与 Spark 3.5 的集成层，functions 子包负责 将 Iceberg 的变换函数注册为 Spark 系统函数，供 SQL
 * 直接调用）。
 *
 * <p>职责：实现 Iceberg 的 bucket 分桶变换，根据输入值计算哈希后映射到指定数量的桶中。 例如 {@code SELECT system.bucket(128, 'abc')}
 * 返回 122。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link UnboundFunction} 接口，在 bind 阶段根据输入值类型选择 对应的
 *       BoundFunction（BucketInt/BucketLong/BucketString/BucketBinary/BucketDecimal）。
 *   <li>每个子类提供静态 magic method（invoke），供 Spark codegen 直接内联调用， 避免虚函数调用开销。
 *   <li>注意：出于性能考虑，codegen 路径不校验 numBuckets 是否为正数， 调用方需保证其有效性。
 * </ul>
 *
 * <p>上下游关系：通过 {@link org.apache.iceberg.spark.functions.SparkFunctions} 注册到 Spark catalog；被
 * ReplaceStaticInvoke 规则优化为可下推的表达式； 依赖 Iceberg 的 {@link org.apache.iceberg.util.BucketUtil} 计算哈希。
 */
public class BucketFunction implements UnboundFunction {

  private static final int NUM_BUCKETS_ORDINAL = 0;
  private static final int VALUE_ORDINAL = 1;

  private static final Set<DataType> SUPPORTED_NUM_BUCKETS_TYPES =
      ImmutableSet.of(DataTypes.ByteType, DataTypes.ShortType, DataTypes.IntegerType);

  /**
   * 根据输入类型绑定具体的 bucket 函数实现。
   *
   * <p>逻辑：校验输入为两列（numBuckets 和 value），numBuckets 类型必须为 tinyint/smallint/int，然后根据 value 的类型返回对应的
   * BoundFunction： date/int -> BucketInt，bigint/timestamp -> BucketLong，decimal -> BucketDecimal，
   * string -> BucketString，binary -> BucketBinary。
   *
   * @param inputType 输入类型（包含 numBuckets 和 value 两个字段）
   * @return 绑定后的具体函数实现
   * @throws UnsupportedOperationException 输入参数个数或类型不合法时抛出
   */
  @Override
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public BoundFunction bind(StructType inputType) {
    if (inputType.size() != 2) {
      throw new UnsupportedOperationException(
          "Wrong number of inputs (expected numBuckets and value)");
    }

    StructField numBucketsField = inputType.fields()[NUM_BUCKETS_ORDINAL];
    StructField valueField = inputType.fields()[VALUE_ORDINAL];

    if (!SUPPORTED_NUM_BUCKETS_TYPES.contains(numBucketsField.dataType())) {
      throw new UnsupportedOperationException(
          "Expected number of buckets to be tinyint, shortint or int");
    }

    DataType type = valueField.dataType();
    if (type instanceof DateType) {
      return new BucketInt(type);
    } else if (type instanceof ByteType
        || type instanceof ShortType
        || type instanceof IntegerType) {
      return new BucketInt(DataTypes.IntegerType);
    } else if (type instanceof LongType) {
      return new BucketLong(type);
    } else if (type instanceof TimestampType) {
      return new BucketLong(type);
    } else if (type instanceof TimestampNTZType) {
      return new BucketLong(type);
    } else if (type instanceof DecimalType) {
      return new BucketDecimal(type);
    } else if (type instanceof StringType) {
      return new BucketString();
    } else if (type instanceof BinaryType) {
      return new BucketBinary();
    } else {
      throw new UnsupportedOperationException(
          "Expected column to be date, tinyint, smallint, int, bigint, decimal, timestamp, string, or binary");
    }
  }
  /** 返回描述。 */
  @Override
  public String description() {
    return name()
        + "(numBuckets, col) - Call Iceberg's bucket transform\n"
        + "  numBuckets :: number of buckets to divide the rows into, e.g. bucket(100, 34) -> 79 (must be a tinyint, smallint, or int)\n"
        + "  col :: column to bucket (must be a date, integer, long, timestamp, decimal, string, or binary)";
  }
  /** 返回名称。 */
  @Override
  public String name() {
    return "bucket";
  }

  public abstract static class BucketBase implements ScalarFunction<Integer> {
    /** 应用转换。 */
    public static int apply(int numBuckets, int hashedValue) {
      return (hashedValue & Integer.MAX_VALUE) % numBuckets;
    }
    /** 返回名称。 */
    @Override
    public String name() {
      return "bucket";
    }
    /** 返回结果类型。 */
    @Override
    public DataType resultType() {
      return DataTypes.IntegerType;
    }
  }

  // Used for both int and date - tinyint and smallint are upcasted to int by Spark.
  public static class BucketInt extends BucketBase {
    private final DataType sqlType;

    // magic method used in codegen
    public static int invoke(int numBuckets, int value) {
      return apply(numBuckets, hash(value));
    }

    // Visible for testing
    public static int hash(int value) {
      return BucketUtil.hash(value);
    }

    public BucketInt(DataType sqlType) {
      this.sqlType = sqlType;
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, sqlType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return String.format("iceberg.bucket(%s)", sqlType.catalogString());
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      // return null for null input to match what Spark does in the code-generated versions.
      if (input.isNullAt(NUM_BUCKETS_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(NUM_BUCKETS_ORDINAL), input.getInt(VALUE_ORDINAL));
      }
    }
  }

  // Used for both BigInt and Timestamp
  public static class BucketLong extends BucketBase {
    private final DataType sqlType;

    // magic function for usage with codegen - needs to be static
    public static int invoke(int numBuckets, long value) {
      return apply(numBuckets, hash(value));
    }

    // Visible for testing
    public static int hash(long value) {
      return BucketUtil.hash(value);
    }

    public BucketLong(DataType sqlType) {
      this.sqlType = sqlType;
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, sqlType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return String.format("iceberg.bucket(%s)", sqlType.catalogString());
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      if (input.isNullAt(NUM_BUCKETS_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(NUM_BUCKETS_ORDINAL), input.getLong(VALUE_ORDINAL));
      }
    }
  }

  public static class BucketString extends BucketBase {
    // magic function for usage with codegen
    public static Integer invoke(int numBuckets, UTF8String value) {
      if (value == null) {
        return null;
      }

      // TODO - We can probably hash the bytes directly given they're already UTF-8 input.
      return apply(numBuckets, hash(value.toString()));
    }

    // Visible for testing
    public static int hash(String value) {
      return BucketUtil.hash(value);
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.StringType};
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.bucket(string)";
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      if (input.isNullAt(NUM_BUCKETS_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(NUM_BUCKETS_ORDINAL), input.getUTF8String(VALUE_ORDINAL));
      }
    }
  }

  public static class BucketBinary extends BucketBase {
    /** 执行 invoke 相关操作。 */
    public static Integer invoke(int numBuckets, byte[] value) {
      if (value == null) {
        return null;
      }

      return apply(numBuckets, hash(ByteBuffer.wrap(value)));
    }

    // Visible for testing
    public static int hash(ByteBuffer value) {
      return BucketUtil.hash(value);
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.BinaryType};
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      if (input.isNullAt(NUM_BUCKETS_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(NUM_BUCKETS_ORDINAL), input.getBinary(VALUE_ORDINAL));
      }
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.bucket(binary)";
    }
  }

  public static class BucketDecimal extends BucketBase {
    private final DataType sqlType;
    private final int precision;
    private final int scale;

    // magic method used in codegen
    public static Integer invoke(int numBuckets, Decimal value) {
      if (value == null) {
        return null;
      }

      return apply(numBuckets, hash(value.toJavaBigDecimal()));
    }

    // Visible for testing
    public static int hash(BigDecimal value) {
      return BucketUtil.hash(value);
    }

    public BucketDecimal(DataType sqlType) {
      this.sqlType = sqlType;
      this.precision = ((DecimalType) sqlType).precision();
      this.scale = ((DecimalType) sqlType).scale();
    }
    /** 返回输入类型列表。 */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, sqlType};
    }
    /** 执行 produceResult 相关操作。 */
    @Override
    public Integer produceResult(InternalRow input) {
      if (input.isNullAt(NUM_BUCKETS_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        int numBuckets = input.getInt(NUM_BUCKETS_ORDINAL);
        Decimal value = input.getDecimal(VALUE_ORDINAL, precision, scale);
        return invoke(numBuckets, value);
      }
    }
    /** 执行 canonicalName 相关操作。 */
    @Override
    public String canonicalName() {
      return "iceberg.bucket(decimal)";
    }
  }
}
