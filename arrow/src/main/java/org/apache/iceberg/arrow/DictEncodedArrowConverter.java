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
package org.apache.iceberg.arrow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.function.IntConsumer;
import org.apache.arrow.vector.BaseFixedWidthVector;
import org.apache.arrow.vector.BaseVariableWidthVector;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.Float8Vector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.TimeStampMicroVector;
import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.iceberg.arrow.vectorized.ArrowVectorAccessor;
import org.apache.iceberg.arrow.vectorized.VectorHolder;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：将字典编码（dictionary-encoded）的 Arrow 向量转换为正确类型的 Arrow 向量。
 *
 * <p>所属模块：iceberg-arrow（Arrow 列式内存与 Iceberg 读取链路的桥接模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>当 Parquet 列以字典编码方式读入 Arrow 后，某些 Iceberg 类型（Decimal、Timestamp、
 *       Long、Float、Double、String、Binary、Time）需要被解码为对应的强类型 Arrow 向量， 本类负责这一转换。
 *   <li>通过 {@link ArrowVectorAccessor} 从字典编码向量中按行读取值，再写入新建的目标向量， 并正确处理空值（依据 {@link
 *       VectorHolder#nullabilityHolder()}）。
 * </ul>
 *
 * <p>设计意图：字典编码在 Parquet 中是常见的高压缩比编码，但下游计算引擎往往需要原生类型的 向量。本类作为读取后处理步骤，把字典形态统一物化为强类型向量，屏蔽编码差异。各类型转换
 * 拆分为独立私有方法以降低圈复杂度，并通过 {@link #init(FieldVector, VectorHolder, IntConsumer, int)} 抽取公共的逐行写入逻辑。
 *
 * <p>上下游关系：上游为向量化读取器产出的 {@link VectorHolder}（含字典编码向量与空值信息）； 下游交给计算引擎消费强类型 Arrow 向量。
 */
public class DictEncodedArrowConverter {

  private DictEncodedArrowConverter() {}

  /**
   * 将字典编码的向量转换为对应类型的强类型 Arrow 向量。
   *
   * <p>逻辑：先校验 holder 与 accessor 非空；若 holder 标记为字典编码，则按 Iceberg 类型 分发到对应的 {@code toXxxVector}
   * 方法（Decimal/Timestamp/Long/Float/Double/String/ Binary/Time）；不属于字典编码则直接返回 holder
   * 中的原始向量；若类型不在支持列表内则抛出 {@link IllegalArgumentException}。
   *
   * @param vectorHolder 持有字典编码向量及空值信息的容器
   * @param accessor 用于从字典编码向量读取值的访问器
   * @return 转换后的强类型 {@link FieldVector}（非字典编码时为原向量）
   * @throws IllegalArgumentException 若 holder/accessor 为 null，或字典编码类型不被支持
   */
  @SuppressWarnings("checkstyle:CyclomaticComplexity")
  public static FieldVector toArrowVector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    Preconditions.checkArgument(null != vectorHolder, "Invalid vector holder: null");
    Preconditions.checkArgument(null != accessor, "Invalid arrow vector accessor: null");

    if (vectorHolder.isDictionaryEncoded()) {
      if (Type.TypeID.DECIMAL.equals(vectorHolder.icebergType().typeId())) {
        return toDecimalVector(vectorHolder, accessor);
      } else if (Type.TypeID.TIMESTAMP.equals(vectorHolder.icebergType().typeId())) {
        return toTimestampVector(vectorHolder, accessor);
      } else if (Type.TypeID.LONG.equals(vectorHolder.icebergType().typeId())) {
        return toBigIntVector(vectorHolder, accessor);
      } else if (Type.TypeID.FLOAT.equals(vectorHolder.icebergType().typeId())) {
        return toFloat4Vector(vectorHolder, accessor);
      } else if (Type.TypeID.DOUBLE.equals(vectorHolder.icebergType().typeId())) {
        return toFloat8Vector(vectorHolder, accessor);
      } else if (Type.TypeID.STRING.equals(vectorHolder.icebergType().typeId())) {
        return toVarCharVector(vectorHolder, accessor);
      } else if (Type.TypeID.BINARY.equals(vectorHolder.icebergType().typeId())) {
        return toVarBinaryVector(vectorHolder, accessor);
      } else if (Type.TypeID.TIME.equals(vectorHolder.icebergType().typeId())) {
        return toTimeMicroVector(vectorHolder, accessor);
      }

      throw new IllegalArgumentException(
          String.format(
              "Cannot convert dict encoded field '%s' of type '%s' to Arrow "
                  + "vector as it is currently not supported",
              vectorHolder.icebergField().name(), vectorHolder.icebergType().typeId()));
    }

    return vectorHolder.vector();
  }

  /**
   * 将字典编码向量转换为 {@link DecimalVector}，依据 Iceberg Decimal 类型的精度与标度。
   *
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param accessor 值访问器
   * @return 填充好的 DecimalVector
   */
  private static DecimalVector toDecimalVector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    int precision = ((Types.DecimalType) vectorHolder.icebergType()).precision();
    int scale = ((Types.DecimalType) vectorHolder.icebergType()).scale();

    DecimalVector vector =
        new DecimalVector(
            vectorHolder.vector().getName(),
            ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
            vectorHolder.vector().getAllocator());

    initVector(
        vector,
        vectorHolder,
        idx -> vector.set(idx, (BigDecimal) accessor.getDecimal(idx, precision, scale)));
    return vector;
  }

  /**
   * 将字典编码向量转换为时间戳向量。
   *
   * <p>逻辑：依据 Iceberg TimestampType 是否按 UTC 调整，选择带时区的 {@link TimeStampMicroTZVector} 或不带时区的 {@link
   * TimeStampMicroVector}，再逐行写入。
   *
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param accessor 值访问器
   * @return 填充好的时间戳向量
   */
  private static TimeStampVector toTimestampVector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    TimeStampVector vector;
    if (((Types.TimestampType) vectorHolder.icebergType()).shouldAdjustToUTC()) {
      vector =
          new TimeStampMicroTZVector(
              vectorHolder.vector().getName(),
              ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
              vectorHolder.vector().getAllocator());
    } else {
      vector =
          new TimeStampMicroVector(
              vectorHolder.vector().getName(),
              ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
              vectorHolder.vector().getAllocator());
    }

    initVector(vector, vectorHolder, idx -> vector.set(idx, accessor.getLong(idx)));
    return vector;
  }

  /**
   * 将字典编码向量转换为 {@link BigIntVector}（64 位整型）。
   *
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param accessor 值访问器
   * @return 填充好的 BigIntVector
   */
  private static BigIntVector toBigIntVector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    BigIntVector vector =
        new BigIntVector(
            vectorHolder.vector().getName(),
            ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
            vectorHolder.vector().getAllocator());

    initVector(vector, vectorHolder, idx -> vector.set(idx, accessor.getLong(idx)));
    return vector;
  }

  /**
   * 将字典编码向量转换为 {@link Float4Vector}（单精度浮点）。
   *
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param accessor 值访问器
   * @return 填充好的 Float4Vector
   */
  private static Float4Vector toFloat4Vector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    Float4Vector vector =
        new Float4Vector(
            vectorHolder.vector().getName(),
            ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
            vectorHolder.vector().getAllocator());

    initVector(vector, vectorHolder, idx -> vector.set(idx, accessor.getFloat(idx)));
    return vector;
  }

  /**
   * 将字典编码向量转换为 {@link Float8Vector}（双精度浮点）。
   *
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param accessor 值访问器
   * @return 填充好的 Float8Vector
   */
  private static Float8Vector toFloat8Vector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    Float8Vector vector =
        new Float8Vector(
            vectorHolder.vector().getName(),
            ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
            vectorHolder.vector().getAllocator());

    initVector(vector, vectorHolder, idx -> vector.set(idx, accessor.getDouble(idx)));
    return vector;
  }

  /**
   * 将字典编码向量转换为 {@link VarCharVector}（UTF-8 字符串），使用 setSafe 安全写入。
   *
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param accessor 值访问器
   * @return 填充好的 VarCharVector
   */
  private static VarCharVector toVarCharVector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    VarCharVector vector =
        new VarCharVector(
            vectorHolder.vector().getName(),
            ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
            vectorHolder.vector().getAllocator());

    initVector(
        vector,
        vectorHolder,
        idx -> vector.setSafe(idx, accessor.getUTF8String(idx).getBytes(StandardCharsets.UTF_8)));
    return vector;
  }

  /**
   * 将字典编码向量转换为 {@link VarBinaryVector}（变长二进制），使用 setSafe 安全写入。
   *
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param accessor 值访问器
   * @return 填充好的 VarBinaryVector
   */
  private static VarBinaryVector toVarBinaryVector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    VarBinaryVector vector =
        new VarBinaryVector(
            vectorHolder.vector().getName(),
            ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
            vectorHolder.vector().getAllocator());

    initVector(vector, vectorHolder, idx -> vector.setSafe(idx, accessor.getBinary(idx)));
    return vector;
  }

  /**
   * 将字典编码向量转换为 {@link TimeMicroVector}（微秒时间）。
   *
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param accessor 值访问器
   * @return 填充好的 TimeMicroVector
   */
  private static TimeMicroVector toTimeMicroVector(
      VectorHolder vectorHolder, ArrowVectorAccessor<?, String, ?, ?> accessor) {
    TimeMicroVector vector =
        new TimeMicroVector(
            vectorHolder.vector().getName(),
            ArrowSchemaUtil.convert(vectorHolder.icebergField()).getFieldType(),
            vectorHolder.vector().getAllocator());

    initVector(vector, vectorHolder, idx -> vector.set(idx, accessor.getLong(idx)));
    return vector;
  }

  /**
   * 初始化定宽向量：按原向量行数分配内存，再委托 {@link #init} 逐行写入。
   *
   * @param vector 目标定宽向量
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param consumer 逐行写入值的回调
   */
  private static void initVector(
      BaseFixedWidthVector vector, VectorHolder vectorHolder, IntConsumer consumer) {
    vector.allocateNew(vectorHolder.vector().getValueCount());
    init(vector, vectorHolder, consumer, vectorHolder.vector().getValueCount());
  }

  /**
   * 初始化变宽向量：按原向量行数分配内存，再委托 {@link #init} 逐行写入。
   *
   * @param vector 目标变宽向量
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param consumer 逐行写入值的回调
   */
  private static void initVector(
      BaseVariableWidthVector vector, VectorHolder vectorHolder, IntConsumer consumer) {
    vector.allocateNew(vectorHolder.vector().getValueCount());
    init(vector, vectorHolder, consumer, vectorHolder.vector().getValueCount());
  }

  /**
   * 逐行写入向量值的核心逻辑。
   *
   * <p>逻辑：遍历每一行，若该行为空则调用 {@code setNull}，否则通过 consumer 写入值； 全部写完后设置向量行数（valueCount），使向量进入可读状态。
   *
   * @param vector 目标向量
   * @param vectorHolder 持有字典编码向量及空值信息
   * @param consumer 逐行写入值的回调
   * @param valueCount 总行数
   */
  private static void init(
      FieldVector vector, VectorHolder vectorHolder, IntConsumer consumer, int valueCount) {
    for (int i = 0; i < valueCount; i++) {
      if (isNullAt(vectorHolder, i)) {
        vector.setNull(i);
      } else {
        consumer.accept(i);
      }
    }

    vector.setValueCount(valueCount);
  }

  /**
   * 判断指定行是否为空。
   *
   * @param vectorHolder 持有空值信息的容器
   * @param idx 行下标
   * @return 为空返回 true
   */
  private static boolean isNullAt(VectorHolder vectorHolder, int idx) {
    return vectorHolder.nullabilityHolder().isNullAt(idx) == 1;
  }
}
