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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.util.BinaryUtil;
import org.apache.iceberg.util.ByteBuffers;
import org.apache.iceberg.util.TruncateUtil;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.connector.catalog.functions.BoundFunction;
import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;
import org.apache.spark.sql.types.BinaryType;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Decimal;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.unsafe.types.UTF8String;

/**
 * Iceberg 内置函数在 Spark 中的实现，注册为 Spark SQL 函数。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateFunction。
 *
 * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
 */
public class TruncateFunction implements UnboundFunction {

  private static final int WIDTH_ORDINAL = 0;
  private static final int VALUE_ORDINAL = 1;

  private static final Set<DataType> SUPPORTED_WIDTH_TYPES =
      ImmutableSet.of(DataTypes.ByteType, DataTypes.ShortType, DataTypes.IntegerType);

  /**
   * 执行该方法的具体逻辑。
   *
   * @param inputType 参数
   * @return 结果对象
   */
  @Override
  public BoundFunction bind(StructType inputType) {
    if (inputType.size() != 2) {
      throw new UnsupportedOperationException("Wrong number of inputs (expected width and value)");
    }

    StructField widthField = inputType.fields()[WIDTH_ORDINAL];
    StructField valueField = inputType.fields()[VALUE_ORDINAL];

    if (!SUPPORTED_WIDTH_TYPES.contains(widthField.dataType())) {
      throw new UnsupportedOperationException(
          "Expected truncation width to be tinyint, shortint or int");
    }

    DataType valueType = valueField.dataType();
    if (valueType instanceof ByteType) {
      /** 执行该方法的具体逻辑。 */
      return new TruncateTinyInt();
    } else if (valueType instanceof ShortType) {
      /** 执行该方法的具体逻辑。 */
      return new TruncateSmallInt();
    } else if (valueType instanceof IntegerType) {
      /** 执行该方法的具体逻辑。 */
      return new TruncateInt();
    } else if (valueType instanceof LongType) {
      /** 执行该方法的具体逻辑。 */
      return new TruncateBigInt();
    } else if (valueType instanceof DecimalType) {
      /** 执行该方法的具体逻辑。 */
      return new TruncateDecimal(
          ((DecimalType) valueType).precision(), ((DecimalType) valueType).scale());
    } else if (valueType instanceof StringType) {
      /** 执行该方法的具体逻辑。 */
      return new TruncateString();
    } else if (valueType instanceof BinaryType) {
      /** 执行该方法的具体逻辑。 */
      return new TruncateBinary();
    } else {
      throw new UnsupportedOperationException(
          "Expected truncation col to be tinyint, shortint, int, bigint, decimal, string, or binary");
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
        + "(width, col) - Call Iceberg's truncate transform\n"
        + "  width :: width for truncation, e.g. truncate(10, 255) -> 250 (must be an integer)\n"
        + "  col :: column to truncate (must be an integer, decimal, string, or binary)";
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public String name() {
    return "truncate";
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateBase。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public abstract static class TruncateBase<T> implements ScalarFunction<T> {
    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public String name() {
      return "truncate";
    }
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateTinyInt。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class TruncateTinyInt extends TruncateBase<Byte> {
    /** 执行该方法的具体逻辑。 */
    public static byte invoke(int width, byte value) {
      return TruncateUtil.truncateByte(width, value);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.ByteType};
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType resultType() {
      return DataTypes.ByteType;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg.truncate(tinyint)";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public Byte produceResult(InternalRow input) {
      if (input.isNullAt(WIDTH_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(WIDTH_ORDINAL), input.getByte(VALUE_ORDINAL));
      }
    }
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateSmallInt。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class TruncateSmallInt extends TruncateBase<Short> {
    // magic method used in codegen
    /** 执行该方法的具体逻辑。 */
    public static short invoke(int width, short value) {
      return TruncateUtil.truncateShort(width, value);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.ShortType};
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType resultType() {
      return DataTypes.ShortType;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg.truncate(smallint)";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public Short produceResult(InternalRow input) {
      if (input.isNullAt(WIDTH_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(WIDTH_ORDINAL), input.getShort(VALUE_ORDINAL));
      }
    }
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateInt。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class TruncateInt extends TruncateBase<Integer> {
    // magic method used in codegen
    /** 执行该方法的具体逻辑。 */
    public static int invoke(int width, int value) {
      return TruncateUtil.truncateInt(width, value);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.IntegerType};
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType resultType() {
      return DataTypes.IntegerType;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg.truncate(int)";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public Integer produceResult(InternalRow input) {
      if (input.isNullAt(WIDTH_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(WIDTH_ORDINAL), input.getInt(VALUE_ORDINAL));
      }
    }
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateBigInt。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class TruncateBigInt extends TruncateBase<Long> {
    // magic function for usage with codegen
    /** 执行该方法的具体逻辑。 */
    public static long invoke(int width, long value) {
      return TruncateUtil.truncateLong(width, value);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.LongType};
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType resultType() {
      return DataTypes.LongType;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg.truncate(bigint)";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public Long produceResult(InternalRow input) {
      if (input.isNullAt(WIDTH_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(WIDTH_ORDINAL), input.getLong(VALUE_ORDINAL));
      }
    }
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateString。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class TruncateString extends TruncateBase<UTF8String> {
    // magic function for usage with codegen
    /** 执行该方法的具体逻辑。 */
    public static UTF8String invoke(int width, UTF8String value) {
      if (value == null) {
        return null;
      }

      return value.substring(0, width);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.StringType};
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

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg.truncate(string)";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public UTF8String produceResult(InternalRow input) {
      if (input.isNullAt(WIDTH_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(WIDTH_ORDINAL), input.getUTF8String(VALUE_ORDINAL));
      }
    }
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateBinary。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class TruncateBinary extends TruncateBase<byte[]> {
    // magic method used in codegen
    /** 执行该方法的具体逻辑。 */
    public static byte[] invoke(int width, byte[] value) {
      if (value == null) {
        return null;
      }

      return ByteBuffers.toByteArray(
          BinaryUtil.truncateBinaryUnsafe(ByteBuffer.wrap(value), width));
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.BinaryType};
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType resultType() {
      return DataTypes.BinaryType;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return "iceberg.truncate(binary)";
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public byte[] produceResult(InternalRow input) {
      if (input.isNullAt(WIDTH_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        return invoke(input.getInt(WIDTH_ORDINAL), input.getBinary(VALUE_ORDINAL));
      }
    }
  }

  /**
   * Iceberg 内置函数在 Spark 中的实现。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 TruncateDecimal。
   *
   * <p>上下游：由 SparkCatalog 注册为函数，被 Spark SQL 表达式调用。
   */
  public static class TruncateDecimal extends TruncateBase<Decimal> {
    private final int precision;
    private final int scale;

    /** 构造 TruncateDecimal 实例。 */
    public TruncateDecimal(int precision, int scale) {
      this.precision = precision;
      this.scale = scale;
    }

    // magic method used in codegen
    /** 执行该方法的具体逻辑。 */
    public static Decimal invoke(int width, Decimal value) {
      if (value == null) {
        return null;
      }

      return Decimal.apply(
          TruncateUtil.truncateDecimal(BigInteger.valueOf(width), value.toJavaBigDecimal()));
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType[] inputTypes() {
      return new DataType[] {DataTypes.IntegerType, DataTypes.createDecimalType(precision, scale)};
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 结果对象
     */
    @Override
    public DataType resultType() {
      return DataTypes.createDecimalType(precision, scale);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @return 布尔结果
     */
    @Override
    public String canonicalName() {
      return String.format("iceberg.truncate(decimal(%d,%d))", precision, scale);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param input 参数
     * @return 结果对象
     */
    @Override
    public Decimal produceResult(InternalRow input) {
      if (input.isNullAt(WIDTH_ORDINAL) || input.isNullAt(VALUE_ORDINAL)) {
        return null;
      } else {
        int width = input.getInt(WIDTH_ORDINAL);
        Decimal value = input.getDecimal(VALUE_ORDINAL, precision, scale);
        return invoke(width, value);
      }
    }
  }
}
