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
 * Iceberg 与 Spark 数据格式之间的读写转换组件的读取器，负责从底层读取数据并转换为 Spark 内部格式。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkOrcReader。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class SparkOrcReader implements OrcRowReader<InternalRow> {
  private final OrcValueReader<?> reader;

  /** 构造 SparkOrcReader 实例。 */
  public SparkOrcReader(org.apache.iceberg.Schema expectedSchema, TypeDescription readSchema) {
    this(expectedSchema, readSchema, ImmutableMap.of());
  }

  /** 构造 SparkOrcReader 实例。 */
  @SuppressWarnings("unchecked")
  public SparkOrcReader(
      org.apache.iceberg.Schema expectedSchema,
      TypeDescription readOrcSchema,
      Map<Integer, ?> idToConstant) {
    this.reader =
        OrcSchemaWithTypeVisitor.visit(
            /** 读取数据。 */
            expectedSchema, readOrcSchema, new ReadBuilder(idToConstant));
  }

  /**
   * 读取数据。
   *
   * @param batch 参数
   * @param row 参数
   * @return 结果对象
   */
  @Override
  public InternalRow read(VectorizedRowBatch batch, int row) {
    return (InternalRow) reader.read(new StructColumnVector(batch.size, batch.cols), row);
  }

  /** 设置batchcontext。 */
  @Override
  public void setBatchContext(long batchOffsetInFile) {
    reader.setBatchContext(batchOffsetInFile);
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 ReadBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class ReadBuilder extends OrcSchemaWithTypeVisitor<OrcValueReader<?>> {
    private final Map<Integer, ?> idToConstant;

    /** 构造 ReadBuilder 实例。 */
    private ReadBuilder(Map<Integer, ?> idToConstant) {
      this.idToConstant = idToConstant;
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param expected 参数
     * @param record 参数
     * @param names 参数
     * @param fields 参数
     * @return 结果对象
     */
    @Override
    public OrcValueReader<?> record(
        Types.StructType expected,
        TypeDescription record,
        List<String> names,
        List<OrcValueReader<?>> fields) {
      return SparkOrcValueReaders.struct(fields, expected, idToConstant);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iList 参数
     * @param array 参数
     * @param elementReader 参数
     * @return 结果对象
     */
    @Override
    public OrcValueReader<?> list(
        Types.ListType iList, TypeDescription array, OrcValueReader<?> elementReader) {
      return SparkOrcValueReaders.array(elementReader);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iMap 参数
     * @param map 参数
     * @param keyReader 参数
     * @param valueReader 参数
     * @return 结果对象
     */
    @Override
    public OrcValueReader<?> map(
        Types.MapType iMap,
        TypeDescription map,
        OrcValueReader<?> keyReader,
        OrcValueReader<?> valueReader) {
      return SparkOrcValueReaders.map(keyReader, valueReader);
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iPrimitive 参数
     * @param primitive 参数
     * @return 结果对象
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
