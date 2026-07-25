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
package org.apache.iceberg.spark.actions;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import org.apache.iceberg.util.ZOrderByteUtils;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.BinaryType;
import org.apache.spark.sql.types.BooleanType;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DateType;
import org.apache.spark.sql.types.DoubleType;
import org.apache.spark.sql.types.FloatType;
import org.apache.spark.sql.types.IntegerType;
import org.apache.spark.sql.types.LongType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StringType;
import org.apache.spark.sql.types.TimestampType;
import scala.collection.JavaConverters;
import scala.collection.Seq;

/**
 * Spark zOrder 排序的用户自定义函数（UDF）集合。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），actions 子包。被 {@link SparkZOrderDataRewriter} 用于在 Spark SQL
 * 中把各列值转为可排序的字节数组，再按位交织得到 zOrder 键。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为每种 Spark 数据类型（byte/short/int/long/float/double/boolean/string/binary/timestamp/date）
 *       提供"转有序字节数组"的 UDF。
 *   <li>提供按位交织（interleaveBits）UDF，把多列有序字节合成 zOrder 键。
 *   <li>按列分配输入 buffer 位置并累计输出字节数（受 maxOutputSize 限制）。
 * </ul>
 *
 * <p>设计意图：用 ThreadLocal 缓存 ByteBuffer/encoder/数组，避免每行重复分配，提升 UDF 性能； 实现 Serializable 以便随 Spark
 * 任务序列化到 executor。各类型 UDF 闭包捕获 position 索引， 保证多列互不干扰。
 *
 * <p>上下游关系：被 {@link SparkZOrderDataRewriter} 构造与调用；依赖 iceberg-core 的 {@link ZOrderByteUtils}
 * 做字节级转换与交织。
 */
class SparkZOrderUDF implements Serializable {
  private static final byte[] PRIMITIVE_EMPTY = new byte[ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE];

  /**
   * Every Spark task runs iteratively on a rows in a single thread so ThreadLocal should protect
   * from concurrent access to any of these structures.
   */
  private transient ThreadLocal<ByteBuffer> outputBuffer;

  private transient ThreadLocal<byte[][]> inputHolder;
  private transient ThreadLocal<ByteBuffer[]> inputBuffers;
  private transient ThreadLocal<CharsetEncoder> encoder;

  private final int numCols;

  private int inputCol = 0;
  private int totalOutputBytes = 0;
  private final int varTypeSize;
  private final int maxOutputSize;

  /**
   * 构造 zOrder UDF 集合。
   *
   * @param numCols 参与排序的列数
   * @param varTypeSize 变长类型（string/binary）截断后的字节数
   * @param maxOutputSize 输出 zOrder 键的最大字节数
   */
  SparkZOrderUDF(int numCols, int varTypeSize, int maxOutputSize) {
    this.numCols = numCols;
    this.varTypeSize = varTypeSize;
    this.maxOutputSize = maxOutputSize;
  }

  /**
   * 自定义反序列化：重建 ThreadLocal 缓存。
   *
   * <p>逻辑：调用默认反序列化恢复基本字段，再按 numCols/totalOutputBytes 重新初始化
   * inputBuffers/inputHolder/outputBuffer/encoder 的 ThreadLocal，因为 transient 字段不会自动恢复。
   */
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    inputBuffers = ThreadLocal.withInitial(() -> new ByteBuffer[numCols]);
    inputHolder = ThreadLocal.withInitial(() -> new byte[numCols][]);
    outputBuffer = ThreadLocal.withInitial(() -> ByteBuffer.allocate(totalOutputBytes));
    encoder = ThreadLocal.withInitial(() -> StandardCharsets.UTF_8.newEncoder());
  }

  /** 获取指定列位置的输入 ByteBuffer，按需分配并缓存到 ThreadLocal 数组。 */
  private ByteBuffer inputBuffer(int position, int size) {
    ByteBuffer buffer = inputBuffers.get()[position];
    if (buffer == null) {
      buffer = ByteBuffer.allocate(size);
      inputBuffers.get()[position] = buffer;
    }
    return buffer;
  }

  /**
   * 把多列有序字节数组按位交织得到 zOrder 键。
   *
   * <p>逻辑：把 Scala Seq 转为 Java 二维数组，调用 {@link ZOrderByteUtils#interleaveBits} 用 outputBuffer 写出
   * totalOutputBytes 长度的结果。
   *
   * @param scalaBinary 各列有序字节数组组成的 Scala Seq
   * @return 交织后的 zOrder 字节数组
   */
  byte[] interleaveBits(Seq<byte[]> scalaBinary) {
    byte[][] columnsBinary = JavaConverters.seqAsJavaList(scalaBinary).toArray(inputHolder.get());
    return ZOrderByteUtils.interleaveBits(columnsBinary, totalOutputBytes, outputBuffer.get());
  }

  /** 构造 Byte 转 zOrder 有序字节数组的 UDF。 */
  private UserDefinedFunction tinyToOrderedBytesUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (Byte value) -> {
                  if (value == null) {
                    return PRIMITIVE_EMPTY;
                  }
                  return ZOrderByteUtils.tinyintToOrderedBytes(
                          value, inputBuffer(position, ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE))
                      .array();
                },
                DataTypes.BinaryType)
            .withName("TINY_ORDERED_BYTES");

    this.inputCol++;
    increaseOutputSize(ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);

    return udf;
  }

  /** 构造 Short 转 zOrder 有序字节数组的 UDF。 */
  private UserDefinedFunction shortToOrderedBytesUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (Short value) -> {
                  if (value == null) {
                    return PRIMITIVE_EMPTY;
                  }
                  return ZOrderByteUtils.shortToOrderedBytes(
                          value, inputBuffer(position, ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE))
                      .array();
                },
                DataTypes.BinaryType)
            .withName("SHORT_ORDERED_BYTES");

    this.inputCol++;
    increaseOutputSize(ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);

    return udf;
  }

  /** 构造 Integer 转 zOrder 有序字节数组的 UDF。 */
  private UserDefinedFunction intToOrderedBytesUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (Integer value) -> {
                  if (value == null) {
                    return PRIMITIVE_EMPTY;
                  }
                  return ZOrderByteUtils.intToOrderedBytes(
                          value, inputBuffer(position, ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE))
                      .array();
                },
                DataTypes.BinaryType)
            .withName("INT_ORDERED_BYTES");

    this.inputCol++;
    increaseOutputSize(ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);

    return udf;
  }

  /** 构造 Long 转 zOrder 有序字节数组的 UDF。 */
  private UserDefinedFunction longToOrderedBytesUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (Long value) -> {
                  if (value == null) {
                    return PRIMITIVE_EMPTY;
                  }
                  return ZOrderByteUtils.longToOrderedBytes(
                          value, inputBuffer(position, ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE))
                      .array();
                },
                DataTypes.BinaryType)
            .withName("LONG_ORDERED_BYTES");

    this.inputCol++;
    increaseOutputSize(ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);

    return udf;
  }

  /** 构造 Float 转 zOrder 有序字节数组的 UDF。 */
  private UserDefinedFunction floatToOrderedBytesUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (Float value) -> {
                  if (value == null) {
                    return PRIMITIVE_EMPTY;
                  }
                  return ZOrderByteUtils.floatToOrderedBytes(
                          value, inputBuffer(position, ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE))
                      .array();
                },
                DataTypes.BinaryType)
            .withName("FLOAT_ORDERED_BYTES");

    this.inputCol++;
    increaseOutputSize(ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);

    return udf;
  }

  /** 构造 Double 转 zOrder 有序字节数组的 UDF。 */
  private UserDefinedFunction doubleToOrderedBytesUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (Double value) -> {
                  if (value == null) {
                    return PRIMITIVE_EMPTY;
                  }
                  return ZOrderByteUtils.doubleToOrderedBytes(
                          value, inputBuffer(position, ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE))
                      .array();
                },
                DataTypes.BinaryType)
            .withName("DOUBLE_ORDERED_BYTES");

    this.inputCol++;
    increaseOutputSize(ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);

    return udf;
  }

  /** 构造 Boolean 转 zOrder 有序字节数组的 UDF（true 为 -127，false 为 0）。 */
  private UserDefinedFunction booleanToOrderedBytesUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (Boolean value) -> {
                  ByteBuffer buffer = inputBuffer(position, ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);
                  buffer.put(0, (byte) (value ? -127 : 0));
                  return buffer.array();
                },
                DataTypes.BinaryType)
            .withName("BOOLEAN-LEXICAL-BYTES");

    this.inputCol++;
    increaseOutputSize(ZOrderByteUtils.PRIMITIVE_BUFFER_SIZE);
    return udf;
  }

  /** 构造 String 转 zOrder 有序字节数组的 UDF，按 varTypeSize 截断。 */
  private UserDefinedFunction stringToOrderedBytesUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (String value) ->
                    ZOrderByteUtils.stringToOrderedBytes(
                            value, varTypeSize, inputBuffer(position, varTypeSize), encoder.get())
                        .array(),
                DataTypes.BinaryType)
            .withName("STRING-LEXICAL-BYTES");

    this.inputCol++;
    increaseOutputSize(varTypeSize);

    return udf;
  }

  /** 构造 Binary 转 zOrder 有序字节数组的 UDF，按 varTypeSize 截断或填充。 */
  private UserDefinedFunction bytesTruncateUDF() {
    int position = inputCol;
    UserDefinedFunction udf =
        functions
            .udf(
                (byte[] value) ->
                    ZOrderByteUtils.byteTruncateOrFill(
                            value, varTypeSize, inputBuffer(position, varTypeSize))
                        .array(),
                DataTypes.BinaryType)
            .withName("BYTE-TRUNCATE");

    this.inputCol++;
    increaseOutputSize(varTypeSize);

    return udf;
  }

  private final UserDefinedFunction interleaveUDF =
      functions
          .udf((Seq<byte[]> arrayBinary) -> interleaveBits(arrayBinary), DataTypes.BinaryType)
          .withName("INTERLEAVE_BYTES");

  /** 返回对输入列数组应用 interleaveUDF 得到的 Spark Column。 */
  Column interleaveBytes(Column arrayBinary) {
    return interleaveUDF.apply(arrayBinary);
  }

  /**
   * 根据列的 Spark 数据类型选择对应的"转有序字节"UDF 并应用到该列。
   *
   * <p>逻辑：按类型分支调用对应 UDF；timestamp/date 先 cast 为 Long 再用 long UDF； 不支持的类型抛出
   * IllegalArgumentException。
   *
   * @param column 待排序的列
   * @param type 列的 Spark 数据类型
   * @return 应用 UDF 后的列
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  Column sortedLexicographically(Column column, DataType type) {
    if (type instanceof ByteType) {
      return tinyToOrderedBytesUDF().apply(column);
    } else if (type instanceof ShortType) {
      return shortToOrderedBytesUDF().apply(column);
    } else if (type instanceof IntegerType) {
      return intToOrderedBytesUDF().apply(column);
    } else if (type instanceof LongType) {
      return longToOrderedBytesUDF().apply(column);
    } else if (type instanceof FloatType) {
      return floatToOrderedBytesUDF().apply(column);
    } else if (type instanceof DoubleType) {
      return doubleToOrderedBytesUDF().apply(column);
    } else if (type instanceof StringType) {
      return stringToOrderedBytesUDF().apply(column);
    } else if (type instanceof BinaryType) {
      return bytesTruncateUDF().apply(column);
    } else if (type instanceof BooleanType) {
      return booleanToOrderedBytesUDF().apply(column);
    } else if (type instanceof TimestampType) {
      return longToOrderedBytesUDF().apply(column.cast(DataTypes.LongType));
    } else if (type instanceof DateType) {
      return longToOrderedBytesUDF().apply(column.cast(DataTypes.LongType));
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Cannot use column %s of type %s in ZOrdering, the type is unsupported",
              column, type));
    }
  }

  /** 累计输出字节数，但不超过 maxOutputSize。 */
  private void increaseOutputSize(int bytes) {
    totalOutputBytes = Math.min(totalOutputBytes + bytes, maxOutputSize);
  }
}
