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
 * 所属模块：iceberg-spark v3.4
 *
 * <p>职责：Iceberg ORC 文件的 Spark 读取器，将 ORC 行转换为 Spark InternalRow。
 *
 * <p>设计意图：基于 SparkOrcValueReaders 构建按列读取器，支持列裁剪与下推。
 *
 * <p>上下游关系：由 BaseRowReader 在读取 ORC 数据文件时使用。
 */
public class SparkOrcReader implements OrcRowReader<InternalRow> {
  private final OrcValueReader<?> reader;

  public SparkOrcReader(org.apache.iceberg.Schema expectedSchema, TypeDescription readSchema) {
    this(expectedSchema, readSchema, ImmutableMap.of());
  }

  @SuppressWarnings("unchecked")
  public SparkOrcReader(
      org.apache.iceberg.Schema expectedSchema,
      TypeDescription readOrcSchema,
      Map<Integer, ?> idToConstant) {
    this.reader =
        OrcSchemaWithTypeVisitor.visit(
            expectedSchema, readOrcSchema, new ReadBuilder(idToConstant));
  }
  /** 读取数据。 */
  @Override
  public InternalRow read(VectorizedRowBatch batch, int row) {
    return (InternalRow) reader.read(new StructColumnVector(batch.size, batch.cols), row);
  }
  /** 设置 BatchContext 属性。 */
  @Override
  public void setBatchContext(long batchOffsetInFile) {
    reader.setBatchContext(batchOffsetInFile);
  }

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
    /** 执行 primitive 相关操作。 */
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
