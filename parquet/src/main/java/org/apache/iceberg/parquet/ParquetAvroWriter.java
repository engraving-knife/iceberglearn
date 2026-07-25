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
package org.apache.iceberg.parquet;

import java.util.List;
import org.apache.avro.generic.GenericData.Fixed;
import org.apache.avro.generic.IndexedRecord;
import org.apache.iceberg.parquet.ParquetValueWriters.PrimitiveWriter;
import org.apache.iceberg.parquet.ParquetValueWriters.StructWriter;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation.DecimalLogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;

/**
 * 文件级说明：面向 Avro {@link IndexedRecord} 的 Parquet 写入器构造入口。
 *
 * <p>所属模块：iceberg-parquet（与 Avro 集成的写入侧实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历 Parquet {@link MessageType}，递归构造适配 Avro IndexedRecord 的写入器树。
 *   <li>处理 struct/list/map 各层的定义级别与重复级别编码。
 *   <li>按 Parquet OriginalType（UTF8/DATE/DECIMAL 等）选择合适的原始类型写入器。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>独立于 {@code BaseParquetWriter}：Avro 的 Fixed 类型与字段访问方式与 Iceberg Record 不同，故单独实现一套
 *       WriteBuilder。
 *   <li>访问者模式：复用 {@link ParquetTypeVisitor} 遍历，与 Iceberg 通用 writer 共享结构。
 * </ul>
 *
 * <p>上下游关系：被需要直接写 Avro Record 到 Parquet 的上层调用；依赖 {@link ParquetValueWriters} 工厂。
 */
public class ParquetAvroWriter {
  private ParquetAvroWriter() {}

  /**
   * 根据 Parquet 消息类型构造 Avro Record 写入器。
   *
   * @param type Parquet 消息类型
   * @param <T> 记录类型
   * @return Avro Record 写入器根节点
   */
  @SuppressWarnings("unchecked")
  public static <T> ParquetValueWriter<T> buildWriter(MessageType type) {
    return (ParquetValueWriter<T>) ParquetTypeVisitor.visit(type, new WriteBuilder(type));
  }

  /**
   * 内部访问者：按 Parquet schema 节点构造 Avro 适配的写入器。
   *
   * <p>设计意图：持有 {@link MessageType} 以查询字段定义级别/重复级别。
   */
  private static class WriteBuilder extends ParquetTypeVisitor<ParquetValueWriter<?>> {
    private final MessageType type;

    WriteBuilder(MessageType type) {
      this.type = type;
    }

    /** 处理顶层 message 节点，等价于 struct。 */
    @Override
    public ParquetValueWriter<?> message(
        MessageType message, List<ParquetValueWriter<?>> fieldWriters) {
      return struct(message.asGroupType(), fieldWriters);
    }

    /**
     * 构造 struct 写入器，逐字段计算定义级别并包装可选字段。
     *
     * <p>逻辑：遍历字段，查询各字段最大定义级别，用 option 包装处理 null， 最后构造 {@link RecordWriter}。
     */
    @Override
    public ParquetValueWriter<?> struct(
        GroupType struct, List<ParquetValueWriter<?>> fieldWriters) {
      List<Type> fields = struct.getFields();
      List<ParquetValueWriter<?>> writers = Lists.newArrayListWithExpectedSize(fieldWriters.size());
      for (int i = 0; i < fields.size(); i += 1) {
        Type fieldType = struct.getType(i);
        int fieldD = type.getMaxDefinitionLevel(path(fieldType.getName()));
        writers.add(ParquetValueWriters.option(fieldType, fieldD, fieldWriters.get(i)));
      }

      return new RecordWriter(writers);
    }

    /**
     * 构造 list 写入器，计算重复层级与元素定义级别。
     *
     * @param array list group 类型
     * @param elementWriter 元素写入器
     * @return list 写入器
     */
    @Override
    public ParquetValueWriter<?> list(GroupType array, ParquetValueWriter<?> elementWriter) {
      GroupType repeated = array.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath);
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath);

      Type elementType = repeated.getType(0);
      int elementD = type.getMaxDefinitionLevel(path(elementType.getName()));

      return ParquetValueWriters.collections(
          repeatedD, repeatedR, ParquetValueWriters.option(elementType, elementD, elementWriter));
    }

    /**
     * 构造 map 写入器，分别计算 key、value 的定义级别。
     *
     * @param map map group 类型
     * @param keyWriter key 写入器
     * @param valueWriter value 写入器
     * @return map 写入器
     */
    @Override
    public ParquetValueWriter<?> map(
        GroupType map, ParquetValueWriter<?> keyWriter, ParquetValueWriter<?> valueWriter) {
      GroupType repeatedKeyValue = map.getFields().get(0).asGroupType();
      String[] repeatedPath = currentPath();

      int repeatedD = type.getMaxDefinitionLevel(repeatedPath);
      int repeatedR = type.getMaxRepetitionLevel(repeatedPath);

      Type keyType = repeatedKeyValue.getType(0);
      int keyD = type.getMaxDefinitionLevel(path(keyType.getName()));
      Type valueType = repeatedKeyValue.getType(1);
      int valueD = type.getMaxDefinitionLevel(path(valueType.getName()));

      return ParquetValueWriters.maps(
          repeatedD,
          repeatedR,
          ParquetValueWriters.option(keyType, keyD, keyWriter),
          ParquetValueWriters.option(valueType, valueD, valueWriter));
    }

    /**
     * 构造原始类型写入器，优先按 OriginalType 选择语义化写入器。
     *
     * <p>逻辑：若有 OriginalType，按 UTF8/ENUM/JSON->String、DATE/INT_8/INT_16/INT_32->int、
     * INT_64/TIME_MICROS/TIMESTAMP_MICROS->long、DECIMAL->按物理类型选择 decimal writer、 BSON->ByteBuffer
     * 分支；否则按物理类型 fallback 到通用 writer。
     *
     * @param primitive Parquet 原始类型
     * @return 原始类型写入器
     * @throws UnsupportedOperationException 不支持的类型
     */
    @Override
    public ParquetValueWriter<?> primitive(PrimitiveType primitive) {
      ColumnDescriptor desc = type.getColumnDescription(currentPath());

      if (primitive.getOriginalType() != null) {
        switch (primitive.getOriginalType()) {
          case ENUM:
          case JSON:
          case UTF8:
            return ParquetValueWriters.strings(desc);
          case DATE:
          case INT_8:
          case INT_16:
          case INT_32:
            return ParquetValueWriters.ints(desc);
          case INT_64:
          case TIME_MICROS:
          case TIMESTAMP_MICROS:
            return ParquetValueWriters.longs(desc);
          case DECIMAL:
            DecimalLogicalTypeAnnotation decimal =
                (DecimalLogicalTypeAnnotation) primitive.getLogicalTypeAnnotation();
            switch (primitive.getPrimitiveTypeName()) {
              case INT32:
                return ParquetValueWriters.decimalAsInteger(
                    desc, decimal.getPrecision(), decimal.getScale());
              case INT64:
                return ParquetValueWriters.decimalAsLong(
                    desc, decimal.getPrecision(), decimal.getScale());
              case BINARY:
              case FIXED_LEN_BYTE_ARRAY:
                return ParquetValueWriters.decimalAsFixed(
                    desc, decimal.getPrecision(), decimal.getScale());
              default:
                throw new UnsupportedOperationException(
                    "Unsupported base type for decimal: " + primitive.getPrimitiveTypeName());
            }
          case BSON:
            return ParquetValueWriters.byteBuffers(desc);
          default:
            throw new UnsupportedOperationException(
                "Unsupported logical type: " + primitive.getOriginalType());
        }
      }

      switch (primitive.getPrimitiveTypeName()) {
        case FIXED_LEN_BYTE_ARRAY:
          return new FixedWriter(desc);
        case BINARY:
          return ParquetValueWriters.byteBuffers(desc);
        case BOOLEAN:
          return ParquetValueWriters.booleans(desc);
        case INT32:
          return ParquetValueWriters.ints(desc);
        case INT64:
          return ParquetValueWriters.longs(desc);
        case FLOAT:
          return ParquetValueWriters.floats(desc);
        case DOUBLE:
          return ParquetValueWriters.doubles(desc);
        default:
          throw new UnsupportedOperationException("Unsupported type: " + primitive);
      }
    }
  }

  /** 将 Avro {@link Fixed} 写为 Parquet BINARY。 */
  private static class FixedWriter extends PrimitiveWriter<Fixed> {
    private FixedWriter(ColumnDescriptor desc) {
      super(desc);
    }

    @Override
    public void write(int repetitionLevel, Fixed buffer) {
      column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(buffer.bytes()));
    }
  }

  /** Avro {@link IndexedRecord} 的 struct 写入器，按字段索引从 Record 取值。 */
  private static class RecordWriter extends StructWriter<IndexedRecord> {
    private RecordWriter(List<ParquetValueWriter<?>> writers) {
      super(writers);
    }

    /**
     * 按 {@code index} 从 Avro Record 取字段值。
     *
     * @param struct 当前 Avro Record
     * @param index 字段索引
     * @return 字段值
     */
    @Override
    protected Object get(IndexedRecord struct, int index) {
      return struct.get(index);
    }
  }
}
