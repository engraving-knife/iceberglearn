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

import java.util.List;
import java.util.Map;
import org.apache.flink.table.data.ArrayData;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RowData;
import org.apache.iceberg.Schema;
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

/**
 * 将 ORC 列式数据读取为 Flink {@link RowData} 的读取器。
 *
 * <p>所属模块：iceberg-flink，基于 iceberg-orc 的 {@link OrcRowReader} 接口实现。
 *
 * <p>职责：依据 Iceberg {@link Schema} 与 ORC {@link TypeDescription} 构建 {@link OrcValueReader} 树， 将 ORC
 * 的 {@link VectorizedRowBatch} 转换为 Flink {@link RowData}。
 *
 * <p>设计意图：复用 iceberg-orc 的 {@link OrcSchemaWithTypeVisitor} 按类型遍历构建 reader 的通用机制， 通过内部 {@link
 * ReadBuilder} 把每种 Iceberg 类型映射到对应的 Flink ORC value reader，实现类型安全的列式读取。
 *
 * <p>上下游关系：被 Flink ORC 读取流程（source 端）调用；下游依赖 {@link FlinkOrcReaders} 与 {@link OrcValueReaders}
 * 提供的具体字段 reader。
 */
public class FlinkOrcReader implements OrcRowReader<RowData> {
  private final OrcValueReader<?> reader;

  /**
   * 构造读取器（不带常量列）。
   *
   * @param iSchema Iceberg schema
   * @param readSchema ORC 读取 schema
   */
  public FlinkOrcReader(Schema iSchema, TypeDescription readSchema) {
    this(iSchema, readSchema, ImmutableMap.of());
  }

  /**
   * 构造读取器，支持传入常量列（按字段 id 映射到固定值，常用于分区列等非文件内字段）。
   *
   * @param iSchema Iceberg schema
   * @param readSchema ORC 读取 schema
   * @param idToConstant 字段 id 到常量值的映射
   */
  public FlinkOrcReader(Schema iSchema, TypeDescription readSchema, Map<Integer, ?> idToConstant) {
    this.reader =
        OrcSchemaWithTypeVisitor.visit(iSchema, readSchema, new ReadBuilder(idToConstant));
  }

  /**
   * 读取指定批次中某一行，返回 Flink {@link RowData}。
   *
   * <p>逻辑：将 ORC 批次的列数组包装为 {@link StructColumnVector}，再委托给内部 reader 读取该行。
   *
   * @param batch ORC 向量化行批次
   * @param row 行索引
   * @return 转换后的 RowData
   */
  @Override
  public RowData read(VectorizedRowBatch batch, int row) {
    return (RowData) reader.read(new StructColumnVector(batch.size, batch.cols), row);
  }

  /**
   * 设置当前批次在文件中的偏移上下文，传递给内部 reader 用于位置追踪。
   *
   * @param batchOffsetInFile 批次在文件中的偏移量
   */
  @Override
  public void setBatchContext(long batchOffsetInFile) {
    reader.setBatchContext(batchOffsetInFile);
  }

  /**
   * 按 Iceberg/ORC 类型结构构建 {@link OrcValueReader} 树的访问器实现。
   *
   * <p>职责：为 record/list/map/primitive 各类型分别构造对应的 Flink ORC value reader； primitive 类型根据 Iceberg 类型
   * id 选择合适的 reader（如日期、时间、时间戳、十进制等）。
   */
  private static class ReadBuilder extends OrcSchemaWithTypeVisitor<OrcValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    /** 构造 struct（record）类型的 reader，组合各字段 reader 与常量列。 */
    @Override
    public OrcValueReader<RowData> record(
        Types.StructType iStruct,
        TypeDescription record,
        List<String> names,
        List<OrcValueReader<?>> fields) {
      return FlinkOrcReaders.struct(fields, iStruct, idToConstant);
    }

    /** 构造 list 类型的 reader，复用元素 reader 读取数组元素。 */
    @Override
    public OrcValueReader<ArrayData> list(
        Types.ListType iList, TypeDescription array, OrcValueReader<?> elementReader) {
      return FlinkOrcReaders.array(elementReader);
    }

    /** 构造 map 类型的 reader，分别复用键、值 reader。 */
    @Override
    public OrcValueReader<MapData> map(
        Types.MapType iMap,
        TypeDescription map,
        OrcValueReader<?> keyReader,
        OrcValueReader<?> valueReader) {
      return FlinkOrcReaders.map(keyReader, valueReader);
    }

    /**
     * 根据 Iceberg 基本类型 id 选择对应的 ORC value reader。
     *
     * <p>逻辑：按 {@code iPrimitive.typeId()} 分发——布尔/整型/长整型/浮点/双精度走通用 reader；
     * 日期、时间、时间戳（区分是否带时区）、字符串、UUID/FIXED/BINARY、十进制分别使用 Flink 专用 reader； 其余类型抛出 {@link
     * IllegalArgumentException}。
     *
     * @param iPrimitive Iceberg 基本类型
     * @param primitive ORC 类型描述
     * @return 对应的 OrcValueReader
     */
    @Override
    public OrcValueReader<?> primitive(Type.PrimitiveType iPrimitive, TypeDescription primitive) {
      switch (iPrimitive.typeId()) {
        case BOOLEAN:
          return OrcValueReaders.booleans();
        case INTEGER:
          return OrcValueReaders.ints();
        case LONG:
          return OrcValueReaders.longs();
        case FLOAT:
          return OrcValueReaders.floats();
        case DOUBLE:
          return OrcValueReaders.doubles();
        case DATE:
          return FlinkOrcReaders.dates();
        case TIME:
          return FlinkOrcReaders.times();
        case TIMESTAMP:
          Types.TimestampType timestampType = (Types.TimestampType) iPrimitive;
          if (timestampType.shouldAdjustToUTC()) {
            return FlinkOrcReaders.timestampTzs();
          } else {
            return FlinkOrcReaders.timestamps();
          }
        case STRING:
          return FlinkOrcReaders.strings();
        case UUID:
        case FIXED:
        case BINARY:
          return OrcValueReaders.bytes();
        case DECIMAL:
          Types.DecimalType decimalType = (Types.DecimalType) iPrimitive;
          return FlinkOrcReaders.decimals(decimalType.precision(), decimalType.scale());
        default:
          throw new IllegalArgumentException(
              String.format(
                  "Invalid iceberg type %s corresponding to ORC type %s", iPrimitive, primitive));
      }
    }
  }
}
