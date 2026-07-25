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
package org.apache.iceberg.data.orc;

import java.util.List;
import java.util.stream.Stream;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.orc.ORCSchemaUtil;
import org.apache.iceberg.orc.OrcRowWriter;
import org.apache.iceberg.orc.OrcSchemaWithTypeVisitor;
import org.apache.iceberg.orc.OrcValueWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.orc.TypeDescription;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;

/**
 * 通用 ORC 行写入器：将 Iceberg 的 {@link Record} 行对象编码为 ORC 的 {@link VectorizedRowBatch} 列式数据。
 *
 * <p>所属模块：iceberg-orc（data/orc 子包）。本类是面向通用 {@code Record} 模型的具体写入实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link OrcRowWriter}，按行写入 ORC 批数据。
 *   <li>依据 Iceberg Schema 与 ORC schema 的对应关系，通过 schema 访问器构建 树状的 {@link OrcValueWriter}，按字段类型分发到具体
 *       writer。
 *   <li>暴露 metrics() 用于收集列级统计（如 float/double 的 nan 计数、上下界等）。
 * </ul>
 *
 * <p>设计意图：要求顶层 ORC schema 必须为 STRUCT，以与 Iceberg 的 struct 根一一对应。 通过 {@link OrcSchemaWithTypeVisitor}
 * 同步遍历两套类型树，按 Iceberg 类型选择写入器 （例如 TIMESTAMP 根据 shouldAdjustToUTC 区分带时区与不带时区；DECIMAL 按精度选择
 * Decimal18/38）。
 *
 * <p>上下游关系：被 {@code GenericWriterFactory} 等写入入口调用；内部依赖 {@link GenericOrcWriters} 提供的具体类型 writer 与
 * {@link ORCSchemaUtil} 解析字段 id。
 */
public class GenericOrcWriter implements OrcRowWriter<Record> {
  private final RecordWriter writer;

  /**
   * 构造写入器，依据 Iceberg Schema 与 ORC schema 构建内部字段写入树。
   *
   * @param expectedSchema 期望写入的 Iceberg Schema
   * @param orcSchema ORC 文件 schema，顶层必须为 STRUCT
   * @throws IllegalArgumentException 顶层 ORC schema 非 STRUCT
   */
  private GenericOrcWriter(Schema expectedSchema, TypeDescription orcSchema) {
    Preconditions.checkArgument(
        orcSchema.getCategory() == TypeDescription.Category.STRUCT,
        "Top level must be a struct " + orcSchema);

    writer =
        (RecordWriter)
            OrcSchemaWithTypeVisitor.visit(expectedSchema, orcSchema, new WriteBuilder());
  }

  /**
   * 便捷工厂方法：构建 {@link Record} 类型的行写入器。
   *
   * @param expectedSchema 期望的 Iceberg Schema
   * @param fileSchema ORC 文件 schema
   * @return {@link OrcRowWriter} 实例
   */
  public static OrcRowWriter<Record> buildWriter(
      Schema expectedSchema, TypeDescription fileSchema) {
    return new GenericOrcWriter(expectedSchema, fileSchema);
  }

  /**
   * Schema 同步访问器：在 Iceberg 与 ORC 类型树配对遍历过程中，按节点类型构造对应的 {@link OrcValueWriter}。
   *
   * <p>设计意图：把"Iceberg 类型→writer"映射集中在此处。与读取端不同，写入端以 Iceberg 类型 id 为主分支（而非 ORC category），因为写入时
   * Iceberg 类型是权威来源。
   */
  private static class WriteBuilder extends OrcSchemaWithTypeVisitor<OrcValueWriter<?>> {
    private WriteBuilder() {}

    @Override
    /** 构造 struct writer：返回 {@link RecordWriter}，按字段位置写入子列。 */
    public OrcValueWriter<Record> record(
        Types.StructType iStruct,
        TypeDescription record,
        List<String> names,
        List<OrcValueWriter<?>> fields) {
      return new RecordWriter(fields);
    }

    @Override
    /** 构造 list writer：委托 {@link GenericOrcWriters#list}。 */
    public OrcValueWriter<?> list(
        Types.ListType iList, TypeDescription array, OrcValueWriter<?> element) {
      return GenericOrcWriters.list(element);
    }

    @Override
    /** 构造 map writer：委托 {@link GenericOrcWriters#map}。 */
    public OrcValueWriter<?> map(
        Types.MapType iMap, TypeDescription map, OrcValueWriter<?> key, OrcValueWriter<?> value) {
      return GenericOrcWriters.map(key, value);
    }

    @Override
    /**
     * 构造叶子类型 writer：按 Iceberg typeId 分发。
     *
     * <p>逻辑：FLOAT/DOUBLE 会传入字段 id（来自 {@link ORCSchemaUtil#fieldId}）用于构造 带统计的 writer；TIMESTAMP 根据
     * shouldAdjustToUTC 选择带时区/不带时区 writer； DECIMAL 按精度选择 Decimal18Writer（≤18）或
     * Decimal38Writer（≤38）。
     *
     * @param iPrimitive Iceberg 侧的原始类型
     * @param primitive ORC 侧的原始类型描述
     * @return 对应的 {@link OrcValueWriter}
     * @throws IllegalArgumentException 出现未覆盖的 Iceberg 类型
     */
    public OrcValueWriter<?> primitive(Type.PrimitiveType iPrimitive, TypeDescription primitive) {
      switch (iPrimitive.typeId()) {
        case BOOLEAN:
          return GenericOrcWriters.booleans();
        case INTEGER:
          return GenericOrcWriters.ints();
        case LONG:
          return GenericOrcWriters.longs();
        case FLOAT:
          return GenericOrcWriters.floats(ORCSchemaUtil.fieldId(primitive));
        case DOUBLE:
          return GenericOrcWriters.doubles(ORCSchemaUtil.fieldId(primitive));
        case DATE:
          return GenericOrcWriters.dates();
        case TIME:
          return GenericOrcWriters.times();
        case TIMESTAMP:
          Types.TimestampType timestampType = (Types.TimestampType) iPrimitive;
          if (timestampType.shouldAdjustToUTC()) {
            return GenericOrcWriters.timestampTz();
          } else {
            return GenericOrcWriters.timestamp();
          }
        case STRING:
          return GenericOrcWriters.strings();
        case UUID:
          return GenericOrcWriters.uuids();
        case FIXED:
          return GenericOrcWriters.byteArrays();
        case BINARY:
          return GenericOrcWriters.byteBuffers();
        case DECIMAL:
          Types.DecimalType decimalType = (Types.DecimalType) iPrimitive;
          return GenericOrcWriters.decimal(decimalType.precision(), decimalType.scale());
        default:
          throw new IllegalArgumentException(
              String.format(
                  "Invalid iceberg type %s corresponding to ORC type %s", iPrimitive, primitive));
      }
    }
  }

  @Override
  /**
   * 写入一行 {@link Record} 到 ORC 批数据。
   *
   * @param value 待写入的记录，不可为 null
   * @param output ORC 列式批量输出
   * @throws IllegalArgumentException value 为 null
   */
  public void write(Record value, VectorizedRowBatch output) {
    Preconditions.checkArgument(value != null, "value must not be null");
    writer.writeRow(value, output);
  }

  @Override
  /** 返回所有字段 writer，便于外部收集统计与调试。 */
  public List<OrcValueWriter<?>> writers() {
    return writer.writers();
  }

  @Override
  /** 返回所有字段的 {@link FieldMetrics} 流，用于写完后生成文件 metrics。 */
  public Stream<FieldMetrics<?>> metrics() {
    return writer.metrics();
  }

  /**
   * 根 struct writer：把 {@link Record} 按字段位置取出值并写入对应列向量。
   *
   * <p>设计意图：复用 {@link GenericOrcWriters.StructWriter} 的通用 struct 写入逻辑， 仅实现 get() 从 Record 取字段值。
   */
  private static class RecordWriter extends GenericOrcWriters.StructWriter<Record> {

    RecordWriter(List<OrcValueWriter<?>> writers) {
      super(writers);
    }

    @Override
    protected Object get(Record struct, int index) {
      return struct.get(index);
    }
  }
}
