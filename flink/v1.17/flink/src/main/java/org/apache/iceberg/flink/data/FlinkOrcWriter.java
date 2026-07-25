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
package org.apache.iceberg.flink.data;

import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.orc.GenericOrcWriters;
import org.apache.iceberg.orc.OrcRowWriter;
import org.apache.iceberg.orc.OrcValueWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;

/**
 * 文件级说明：将 Flink {@link RowData} 写入 ORC 格式的行写入器。
 *
 * <p>所属模块：iceberg-flink（数据写入子包 data），负责 Flink 行数据与 ORC 列式编码之间的适配。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link OrcRowWriter}，将 RowData 写入 ORC {@link VectorizedRowBatch}。
 *   <li>通过 {@link FlinkSchemaVisitor} 遍历 Iceberg Schema 与 Flink {@link RowType}， 构建 OrcValueWriter
 *       树来逐列编码。
 *   <li>收集字段级指标（metrics）用于文件统计。
 * </ul>
 *
 * <p>设计意图：ORC 是列式格式，需要将 Flink 的行模型转换为列式批量写入。 本类使用 Visitor 模式构建 writer 树，由 {@link FlinkOrcWriters}
 * 提供具体列编码实现。
 *
 * <p>上下游关系：被 Iceberg ORC 写入流程调用；委托 {@link FlinkSchemaVisitor} 构建 writer 树。
 */
public class FlinkOrcWriter implements OrcRowWriter<RowData> {
  private final FlinkOrcWriters.RowDataWriter writer;

  /**
   * 私有构造，通过 Visitor 构建 RowDataWriter。
   *
   * @param rowType Flink 行类型
   * @param iSchema Iceberg schema
   */
  private FlinkOrcWriter(RowType rowType, Schema iSchema) {
    this.writer =
        (FlinkOrcWriters.RowDataWriter)
            FlinkSchemaVisitor.visit(rowType, iSchema, new WriteBuilder());
  }

  /**
   * 创建 ORC 行写入器。
   *
   * @param rowType Flink 行类型
   * @param iSchema Iceberg schema
   * @return ORC 行写入器实例
   */
  public static OrcRowWriter<RowData> buildWriter(RowType rowType, Schema iSchema) {
    return new FlinkOrcWriter(rowType, iSchema);
  }

  /**
   * 将一条 RowData 写入 ORC 列式批量。
   *
   * @param row Flink 行数据
   * @param output ORC 向量化行批量
   */
  @Override
  public void write(RowData row, VectorizedRowBatch output) {
    Preconditions.checkArgument(row != null, "value must not be null");
    writer.writeRow(row, output);
  }

  /**
   * 返回所有子列 writer 的列表。
   *
   * @return 子列 writer 列表
   */
  @Override
  public List<OrcValueWriter<?>> writers() {
    return writer.writers();
  }

  /**
   * 返回字段级指标流。
   *
   * @return 字段指标流
   */
  @Override
  public Stream<FieldMetrics<?>> metrics() {
    return writer.metrics();
  }

  /**
   * Schema 访问器：将 Iceberg 类型节点映射为对应的 Flink ORC {@link OrcValueWriter}。
   *
   * <p>设计意图：通过 Visitor 模式递归遍历 Iceberg Schema，在每种类型节点上选择合适的 OrcValueWriter 实现。使用 fieldIds 栈跟踪当前字段的
   * id，供 FLOAT/DOUBLE 类型 的指标收集使用。
   */
  private static class WriteBuilder extends FlinkSchemaVisitor<OrcValueWriter<?>> {
    private final Deque<Integer> fieldIds = Lists.newLinkedList();

    private WriteBuilder() {}

    /** 进入字段时将 fieldId 压栈，用于后续 primitive 节点获取 id。 */
    @Override
    public void beforeField(Types.NestedField field) {
      fieldIds.push(field.fieldId());
    }

    /** 离开字段时弹出 fieldId。 */
    @Override
    public void afterField(Types.NestedField field) {
      fieldIds.pop();
    }

    /**
     * 构建 struct 类型的 ORC writer。
     *
     * @param iStruct Iceberg struct 类型
     * @param results 各字段 writer
     * @param fieldType 各字段 Flink 类型
     * @return RowData writer
     */
    @Override
    public OrcValueWriter<RowData> record(
        Types.StructType iStruct, List<OrcValueWriter<?>> results, List<LogicalType> fieldType) {
      return FlinkOrcWriters.struct(results, fieldType);
    }

    /**
     * 构建 map 类型的 ORC writer。
     *
     * @param iMap Iceberg map 类型
     * @param key key writer
     * @param value value writer
     * @param keyType Flink key 类型
     * @param valueType Flink value 类型
     * @return map writer
     */
    @Override
    public OrcValueWriter<?> map(
        Types.MapType iMap,
        OrcValueWriter<?> key,
        OrcValueWriter<?> value,
        LogicalType keyType,
        LogicalType valueType) {
      return FlinkOrcWriters.map(key, value, keyType, valueType);
    }

    /**
     * 构建 list 类型的 ORC writer。
     *
     * @param iList Iceberg list 类型
     * @param element 元素 writer
     * @param elementType Flink 元素类型
     * @return list writer
     */
    @Override
    public OrcValueWriter<?> list(
        Types.ListType iList, OrcValueWriter<?> element, LogicalType elementType) {
      return FlinkOrcWriters.list(element, elementType);
    }

    /**
     * 构建基础类型的 ORC writer。
     *
     * <p>逻辑：按 Iceberg primitive 类型 ID 分支选择 writer。对 INTEGER 类型额外区分 Flink TINYINT/SMALLINT；对
     * FLOAT/DOUBLE 从 fieldIds 栈顶取 fieldId 用于指标收集； 对 TIMESTAMP 根据 shouldAdjustToUTC 区分时区时间戳与本地时间戳。
     *
     * @param iPrimitive Iceberg 基础类型
     * @param flinkPrimitive Flink 逻辑类型
     * @return 基础类型 ORC writer
     */
    @Override
    public OrcValueWriter<?> primitive(Type.PrimitiveType iPrimitive, LogicalType flinkPrimitive) {
      switch (iPrimitive.typeId()) {
        case BOOLEAN:
          return GenericOrcWriters.booleans();
        case INTEGER:
          switch (flinkPrimitive.getTypeRoot()) {
            case TINYINT:
              return GenericOrcWriters.bytes();
            case SMALLINT:
              return GenericOrcWriters.shorts();
          }
          return GenericOrcWriters.ints();
        case LONG:
          return GenericOrcWriters.longs();
        case FLOAT:
          Preconditions.checkArgument(
              fieldIds.peek() != null,
              String.format(
                  "[BUG] Cannot find field id for primitive field with type %s. This is likely because id "
                      + "information is not properly pushed during schema visiting.",
                  iPrimitive));
          return GenericOrcWriters.floats(fieldIds.peek());
        case DOUBLE:
          Preconditions.checkArgument(
              fieldIds.peek() != null,
              String.format(
                  "[BUG] Cannot find field id for primitive field with type %s. This is likely because id "
                      + "information is not properly pushed during schema visiting.",
                  iPrimitive));
          return GenericOrcWriters.doubles(fieldIds.peek());
        case DATE:
          return FlinkOrcWriters.dates();
        case TIME:
          return FlinkOrcWriters.times();
        case TIMESTAMP:
          Types.TimestampType timestampType = (Types.TimestampType) iPrimitive;
          if (timestampType.shouldAdjustToUTC()) {
            return FlinkOrcWriters.timestampTzs();
          } else {
            return FlinkOrcWriters.timestamps();
          }
        case STRING:
          return FlinkOrcWriters.strings();
        case UUID:
        case FIXED:
        case BINARY:
          return GenericOrcWriters.byteArrays();
        case DECIMAL:
          Types.DecimalType decimalType = (Types.DecimalType) iPrimitive;
          return FlinkOrcWriters.decimals(decimalType.precision(), decimalType.scale());
        default:
          throw new IllegalArgumentException(
              String.format(
                  "Invalid iceberg type %s corresponding to Flink logical type %s",
                  iPrimitive, flinkPrimitive));
      }
    }
  }
}
