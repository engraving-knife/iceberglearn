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
 * 把 ORC 向量化数据读取为 Flink {@link RowData} 的 OrcRowReader 实现。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：按 Iceberg schema 与 ORC TypeDescription 构造 {@link
 * OrcValueReader}，读取时将 ORC 列向量转换为 Flink RowData。
 *
 * <p>设计意图：适配器模式，把 Iceberg ORC 读取能力适配到 Flink RowData； 上下游：被 Flink ORC 读取算子调用。
 */
public class FlinkOrcReader implements OrcRowReader<RowData> {
  private final OrcValueReader<?> reader;

  /** 构造函数，使用空常量 Map。 */
  public FlinkOrcReader(Schema iSchema, TypeDescription readSchema) {
    this(iSchema, readSchema, ImmutableMap.of());
  }

  /** 构造函数，携带字段常量（如隐藏分区列常量）。 */
  public FlinkOrcReader(Schema iSchema, TypeDescription readSchema, Map<Integer, ?> idToConstant) {
    this.reader =
        OrcSchemaWithTypeVisitor.visit(iSchema, readSchema, new ReadBuilder(idToConstant));
  }

  /** 从 ORC 向量化批次中读取一行数据。 */
  @Override
  public RowData read(VectorizedRowBatch batch, int row) {
    return (RowData) reader.read(new StructColumnVector(batch.size, batch.cols), row);
  }

  /** 设置批上下文（文件内偏移），用于读取 position。 */
  @Override
  public void setBatchContext(long batchOffsetInFile) {
    reader.setBatchContext(batchOffsetInFile);
  }

  /** ORC schema 访问者实现，按 Iceberg 类型构造 Flink RowData 的 OrcValueReader。 */
  private static class ReadBuilder extends OrcSchemaWithTypeVisitor<OrcValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    /** 构造 struct 类型的 OrcValueReader。 */
    @Override
    public OrcValueReader<RowData> record(
        Types.StructType iStruct,
        TypeDescription record,
        List<String> names,
        List<OrcValueReader<?>> fields) {
      return FlinkOrcReaders.struct(fields, iStruct, idToConstant);
    }

    /** 构造数组类型的 OrcValueReader。 */
    @Override
    public OrcValueReader<ArrayData> list(
        Types.ListType iList, TypeDescription array, OrcValueReader<?> elementReader) {
      return FlinkOrcReaders.array(elementReader);
    }

    /** 构造 map 类型的 OrcValueReader。 */
    @Override
    public OrcValueReader<MapData> map(
        Types.MapType iMap,
        TypeDescription map,
        OrcValueReader<?> keyReader,
        OrcValueReader<?> valueReader) {
      return FlinkOrcReaders.map(keyReader, valueReader);
    }

    /**
     * 构造基本类型的 OrcValueReader。
     *
     * <p>逻辑：按 Iceberg 类型 ID 分派到 boolean/int/long/float/double/date/time/timestamp/string/decimal
     * 等读取器。
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
