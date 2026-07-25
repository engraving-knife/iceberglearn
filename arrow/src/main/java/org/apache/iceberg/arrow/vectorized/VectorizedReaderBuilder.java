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
package org.apache.iceberg.arrow.vectorized;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.Schema;
import org.apache.iceberg.arrow.ArrowAllocation;
import org.apache.iceberg.arrow.vectorized.VectorizedArrowReader.ConstantVectorReader;
import org.apache.iceberg.arrow.vectorized.VectorizedArrowReader.DeletedVectorReader;
import org.apache.iceberg.parquet.TypeWithSchemaVisitor;
import org.apache.iceberg.parquet.VectorizedReader;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：基于 Iceberg schema 与 Parquet schema 构建向量化读取器的访问者。所属模块：iceberg-arrow。职责：按字段 ID 将 Parquet
 * 列读取器重排为 Iceberg schema 顺序，处理常量列/行位置/删除标记等元数据列，并为 primitive 列创建 VectorizedArrowReader。继承
 * TypeWithSchemaVisitor。被 ArrowReader.buildReader 调用。设计意图：通过 schema 访问者模式解耦 schema
 * 遍历与读取器构造，readerFactory 注入允许上层自定义批读取器类型。
 */
public class VectorizedReaderBuilder extends TypeWithSchemaVisitor<VectorizedReader<?>> {
  private final MessageType parquetSchema;
  private final Schema icebergSchema;
  private final BufferAllocator rootAllocator;
  private final Map<Integer, ?> idToConstant;
  private final boolean setArrowValidityVector;
  private final Function<List<VectorizedReader<?>>, VectorizedReader<?>> readerFactory;

  /**
   * 构造读取器构建器。
   *
   * @param expectedSchema 预期 Iceberg schema
   * @param parquetSchema Parquet 文件 schema
   * @param setArrowValidityVector 是否设置 Arrow 有效性向量
   * @param idToConstant 字段 ID 到常量值的映射（常量列）
   * @param readerFactory 由列读取器列表构造批读取器的工厂
   */
  public VectorizedReaderBuilder(
      Schema expectedSchema,
      MessageType parquetSchema,
      boolean setArrowValidityVector,
      Map<Integer, ?> idToConstant,
      Function<List<VectorizedReader<?>>, VectorizedReader<?>> readerFactory) {
    this.parquetSchema = parquetSchema;
    this.icebergSchema = expectedSchema;
    this.rootAllocator =
        ArrowAllocation.rootAllocator()
            .newChildAllocator("VectorizedReadBuilder", 0, Long.MAX_VALUE);
    this.setArrowValidityVector = setArrowValidityVector;
    this.idToConstant = idToConstant;
    this.readerFactory = readerFactory;
  }

  @Override
  /**
   * 构建整个消息（顶层 struct）的读取器，按 Iceberg 字段顺序重排列读取器。
   *
   * <p>逻辑：按 Parquet 字段 ID 建立读取器映射；遍历 Iceberg 字段，依次处理常量列、 行位置列、删除标记列，其余按 ID 取对应读取器，缺失则用 nulls()
   * 占位；最后通过 readerFactory 组装。
   *
   * @param expected 预期 struct 类型
   * @param message Parquet 消息类型
   * @param fieldReaders 各字段读取器
   * @return 组装后的读取器
   */
  public VectorizedReader<?> message(
      Types.StructType expected, MessageType message, List<VectorizedReader<?>> fieldReaders) {
    GroupType groupType = message.asGroupType();
    Map<Integer, VectorizedReader<?>> readersById = Maps.newHashMap();
    List<Type> fields = groupType.getFields();

    IntStream.range(0, fields.size())
        .filter(pos -> fields.get(pos).getId() != null)
        .forEach(pos -> readersById.put(fields.get(pos).getId().intValue(), fieldReaders.get(pos)));

    List<Types.NestedField> icebergFields =
        expected != null ? expected.fields() : ImmutableList.of();

    List<VectorizedReader<?>> reorderedFields =
        Lists.newArrayListWithExpectedSize(icebergFields.size());

    for (Types.NestedField field : icebergFields) {
      int id = field.fieldId();
      VectorizedReader<?> reader = readersById.get(id);
      if (idToConstant.containsKey(id)) {
        reorderedFields.add(new ConstantVectorReader<>(field, idToConstant.get(id)));
      } else if (id == MetadataColumns.ROW_POSITION.fieldId()) {
        if (setArrowValidityVector) {
          reorderedFields.add(VectorizedArrowReader.positionsWithSetArrowValidityVector());
        } else {
          reorderedFields.add(VectorizedArrowReader.positions());
        }
      } else if (id == MetadataColumns.IS_DELETED.fieldId()) {
        reorderedFields.add(new DeletedVectorReader());
      } else if (reader != null) {
        reorderedFields.add(reader);
      } else {
        reorderedFields.add(VectorizedArrowReader.nulls());
      }
    }
    return vectorizedReader(reorderedFields);
  }

  /**
   * 用 readerFactory 将重排后的列读取器列表组装为批读取器。
   *
   * @param reorderedFields 重排后的列读取器
   * @return 批读取器
   */
  protected VectorizedReader<?> vectorizedReader(List<VectorizedReader<?>> reorderedFields) {
    return readerFactory.apply(reorderedFields);
  }

  @Override
  /**
   * 构建 struct 字段读取器，当前不支持嵌套 struct 的向量化读取。
   *
   * @param expected 预期 struct 类型
   * @param groupType Parquet group 类型
   * @param fieldReaders 字段读取器
   * @return 不支持时抛异常，否则 null
   * @throws UnsupportedOperationException 当 expected 非 null（即实际为 struct 字段）
   */
  public VectorizedReader<?> struct(
      Types.StructType expected, GroupType groupType, List<VectorizedReader<?>> fieldReaders) {
    if (expected != null) {
      throw new UnsupportedOperationException(
          "Vectorized reads are not supported yet for struct fields");
    }
    return null;
  }

  @Override
  /**
   * 为 primitive 列构建 {@link VectorizedArrowReader}。
   *
   * <p>逻辑：无字段 ID 或为嵌套列（repetition level > 0）时返回 null；否则按字段 ID 找到 Iceberg 字段并创建
   * VectorizedArrowReader。
   *
   * @param expected 预期 Iceberg primitive 类型
   * @param primitive Parquet primitive 类型
   * @return 列读取器，或不支持时 null
   */
  public VectorizedReader<?> primitive(
      org.apache.iceberg.types.Type.PrimitiveType expected, PrimitiveType primitive) {

    // Create arrow vector for this field
    if (primitive.getId() == null) {
      return null;
    }
    int parquetFieldId = primitive.getId().intValue();
    ColumnDescriptor desc = parquetSchema.getColumnDescription(currentPath());
    // Nested types not yet supported for vectorized reads
    if (desc.getMaxRepetitionLevel() > 0) {
      return null;
    }
    Types.NestedField icebergField = icebergSchema.findField(parquetFieldId);
    if (icebergField == null) {
      return null;
    }
    // Set the validity buffer if null checking is enabled in arrow
    return new VectorizedArrowReader(desc, icebergField, rootAllocator, setArrowValidityVector);
  }
}
