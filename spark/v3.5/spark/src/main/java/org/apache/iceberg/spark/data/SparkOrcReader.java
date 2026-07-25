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
package org.apache.iceberg.spark.data;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.orc.OrcRowReader;
import org.apache.iceberg.orc.OrcSchemaWithTypeVisitor;
import org.apache.iceberg.orc.OrcValueReader;
import org.apache.iceberg.orc.OrcValueReaders;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.orc.TypeDescription;
import org.apache.orc.storage.ql.exec.vector.StructColumnVector;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;
import org.apache.spark.sql.catalyst.InternalRow;

/**
 * 将 ORC 的 {@link VectorizedRowBatch} 转换为 Spark {@link InternalRow} 的行读取器。
 *
 * <p>所属模块：iceberg-spark（data 子包，负责 Iceberg 表数据向 Spark 内部行的转换）。
 *
 * <p>职责：基于 Iceberg 期望 schema 与 ORC 读 schema 构建类型化的值读取器， 将每行 ORC 列向量数据读取为 Spark InternalRow。
 *
 * <p>设计意图：通过 {@link OrcSchemaWithTypeVisitor} 按 schema 结构遍历，为每种类型 构造对应的值读取器；读取过程中复用对象以减少内存分配。支持通过
 * idToConstant 注入常量列（如隐藏分区字段），避免从文件中重复读取。
 *
 * <p>上下游关系：实现 {@link OrcRowReader}，被 Iceberg 的 ORC 读取流水线在 Spark 端调用。
 */
public class SparkOrcReader implements OrcRowReader<InternalRow> {
  private final OrcValueReader<?> reader;

  /**
   * 构造读取器，不注入任何常量列。
   *
   * @param expectedSchema Iceberg 期望读取的 schema
   * @param readSchema ORC 文件实际读取 schema
   */
  public SparkOrcReader(org.apache.iceberg.Schema expectedSchema, TypeDescription readSchema) {
    this(expectedSchema, readSchema, ImmutableMap.of());
  }

  /**
   * 构造读取器，并注入字段 ID 到常量值的映射。
   *
   * @param expectedSchema Iceberg 期望读取的 schema
   * @param readOrcSchema ORC 文件实际读取 schema
   * @param idToConstant 字段 ID 到常量值的映射，用于注入隐藏分区等常量列
   */
  @SuppressWarnings("unchecked")
  public SparkOrcReader(
      org.apache.iceberg.Schema expectedSchema,
      TypeDescription readOrcSchema,
      Map<Integer, ?> idToConstant) {
    this.reader =
        OrcSchemaWithTypeVisitor.visit(
            expectedSchema, readOrcSchema, new ReadBuilder(idToConstant));
  }

  /**
   * 读取批次中指定行的数据，返回 Spark {@link InternalRow}。
   *
   * @param batch ORC 列式行批次
   * @param row 行在批次中的序号
   * @return 转换后的 Spark 内部行
   */
  @Override
  public InternalRow read(VectorizedRowBatch batch, int row) {
    return (InternalRow) reader.read(new StructColumnVector(batch.size, batch.cols), row);
  }

  /**
   * 设置当前批次的上下文（批在文件中的偏移），传递给底层读取器用于行号定位。
   *
   * @param batchOffsetInFile 批在文件中的起始行偏移
   */
  @Override
  public void setBatchContext(long batchOffsetInFile) {
    reader.setBatchContext(batchOffsetInFile);
  }

  /** 按 ORC schema 结构构建值读取器的访问器，为 struct/list/map/primitive 各类型生成对应的 {@link OrcValueReader}。 */
  private static class ReadBuilder extends OrcSchemaWithTypeVisitor<OrcValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }
    /** 执行 record 相关操作。 */
    @Override
    public OrcValueReader<?> record(
        Types.StructType expected,
        TypeDescription record,
        List<String> names,
        List<OrcValueReader<?>> fields) {
      return SparkOrcValueReaders.struct(fields, expected, idToConstant);
    }
    /** 执行 list 相关操作。 */
    @Override
    public OrcValueReader<?> list(
        Types.ListType iList, TypeDescription array, OrcValueReader<?> elementReader) {
      return SparkOrcValueReaders.array(elementReader);
    }
    /** 执行 map 相关操作。 */
    @Override
    public OrcValueReader<?> map(
        Types.MapType iMap,
        TypeDescription map,
        OrcValueReader<?> keyReader,
        OrcValueReader<?> valueReader) {
      return SparkOrcValueReaders.map(keyReader, valueReader);
    }

    /**
     * 为 ORC 基本类型构造对应的值读取器。
     *
     * <p>逻辑：按 ORC 类别分派，映射到对应的 Iceberg/Spark 值读取器； Iceberg 没有 byte/short 类型，统一按 int 处理；UUID 以 binary
     * 形式存储但按 UUID 读取。
     *
     * @throws IllegalArgumentException 当遇到未处理的类型时抛出
     */
    @Override
    public OrcValueReader<?> primitive(Type.PrimitiveType iPrimitive, TypeDescription primitive) {
      switch (primitive.getCategory()) {
        case BOOLEAN:
          return OrcValueReaders.booleans();
        case BYTE:
          // Iceberg does not have a byte type. Use int
        case SHORT:
          // Iceberg does not have a short type. Use int
        case DATE:
        case INT:
          return OrcValueReaders.ints();
        case LONG:
          return OrcValueReaders.longs();
        case FLOAT:
          return OrcValueReaders.floats();
        case DOUBLE:
          return OrcValueReaders.doubles();
        case TIMESTAMP_INSTANT:
        case TIMESTAMP:
          return SparkOrcValueReaders.timestampTzs();
        case DECIMAL:
          return SparkOrcValueReaders.decimals(primitive.getPrecision(), primitive.getScale());
        case CHAR:
        case VARCHAR:
        case STRING:
          return SparkOrcValueReaders.utf8String();
        case BINARY:
          if (Type.TypeID.UUID == iPrimitive.typeId()) {
            return SparkOrcValueReaders.uuids();
          }
          return OrcValueReaders.bytes();
        default:
          throw new IllegalArgumentException("Unhandled type " + primitive);
      }
    }
  }
}
