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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.avro.LogicalType;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.io.Encoder;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.avro.MetricsAwareDatumWriter;
import org.apache.iceberg.avro.ValueWriter;
import org.apache.iceberg.avro.ValueWriters;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.ByteType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.ShortType;
import org.apache.spark.sql.types.StructType;

/**
 * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
 *
 * <p>所属模块：iceberg-spark v3.2。 类型：类 SparkAvroWriter。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class SparkAvroWriter implements MetricsAwareDatumWriter<InternalRow> {
  private final StructType dsSchema;
  private ValueWriter<InternalRow> writer = null;

  /** 构造 SparkAvroWriter 实例。 */
  public SparkAvroWriter(StructType dsSchema) {
    this.dsSchema = dsSchema;
  }

  /** 设置schema。 */
  @Override
  @SuppressWarnings("unchecked")
  public void setSchema(Schema schema) {
    this.writer =
        (ValueWriter<InternalRow>)
            AvroWithSparkSchemaVisitor.visit(dsSchema, schema, new WriteBuilder());
  }

  /**
   * 写入数据。
   *
   * @param datum 参数
   * @param out 参数
   */
  @Override
  public void write(InternalRow datum, Encoder out) throws IOException {
    writer.write(datum, out);
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Stream<FieldMetrics> metrics() {
    return writer.metrics();
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.2。 类型：类 WriteBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class WriteBuilder extends AvroWithSparkSchemaVisitor<ValueWriter<?>> {
    /**
     * 执行该方法的具体逻辑。
     *
     * @param struct 参数
     * @param record 参数
     * @param names 参数
     * @param fields 参数
     * @return 结果对象
     */
    @Override
    public ValueWriter<?> record(
        DataType struct, Schema record, List<String> names, List<ValueWriter<?>> fields) {
      return SparkValueWriters.struct(
          fields,
          IntStream.range(0, names.size())
              .mapToObj(i -> fieldNameAndType(struct, i).second())
              .collect(Collectors.toList()));
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param type 参数
     * @param union 参数
     * @param options 参数
     * @return 结果对象
     */
    @Override
    public ValueWriter<?> union(DataType type, Schema union, List<ValueWriter<?>> options) {
      Preconditions.checkArgument(
          options.contains(ValueWriters.nulls()),
          "Cannot create writer for non-option union: %s",
          union);
      Preconditions.checkArgument(
          options.size() == 2, "Cannot create writer for non-option union: %s", union);
      if (union.getTypes().get(0).getType() == Schema.Type.NULL) {
        return ValueWriters.option(0, options.get(1));
      } else {
        return ValueWriters.option(1, options.get(0));
      }
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sArray 参数
     * @param array 参数
     * @param elementWriter 参数
     * @return 结果对象
     */
    @Override
    public ValueWriter<?> array(DataType sArray, Schema array, ValueWriter<?> elementWriter) {
      return SparkValueWriters.array(elementWriter, arrayElementType(sArray));
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sMap 参数
     * @param map 参数
     * @param valueReader 参数
     * @return 结果对象
     */
    @Override
    public ValueWriter<?> map(DataType sMap, Schema map, ValueWriter<?> valueReader) {
      return SparkValueWriters.map(
          SparkValueWriters.strings(), mapKeyType(sMap), valueReader, mapValueType(sMap));
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param sMap 参数
     * @param map 参数
     * @param keyWriter 参数
     * @param valueWriter 参数
     * @return 结果对象
     */
    @Override
    public ValueWriter<?> map(
        DataType sMap, Schema map, ValueWriter<?> keyWriter, ValueWriter<?> valueWriter) {
      return SparkValueWriters.arrayMap(
          /** 执行该方法的具体逻辑。 */
          keyWriter, mapKeyType(sMap), valueWriter, mapValueType(sMap));
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param type 参数
     * @param primitive 参数
     * @return 结果对象
     */
    @Override
    public ValueWriter<?> primitive(DataType type, Schema primitive) {
      LogicalType logicalType = primitive.getLogicalType();
      if (logicalType != null) {
        switch (logicalType.getName()) {
          case "date":
            // Spark uses the same representation
            return ValueWriters.ints();

          case "timestamp-micros":
            // Spark uses the same representation
            return ValueWriters.longs();

          case "decimal":
            LogicalTypes.Decimal decimal = (LogicalTypes.Decimal) logicalType;
            return SparkValueWriters.decimal(decimal.getPrecision(), decimal.getScale());

          case "uuid":
            return SparkValueWriters.uuids();

          default:
            throw new IllegalArgumentException("Unsupported logical type: " + logicalType);
        }
      }

      switch (primitive.getType()) {
        case NULL:
          return ValueWriters.nulls();
        case BOOLEAN:
          return ValueWriters.booleans();
        case INT:
          if (type instanceof ByteType) {
            return ValueWriters.tinyints();
          } else if (type instanceof ShortType) {
            return ValueWriters.shorts();
          }
          return ValueWriters.ints();
        case LONG:
          return ValueWriters.longs();
        case FLOAT:
          return ValueWriters.floats();
        case DOUBLE:
          return ValueWriters.doubles();
        case STRING:
          return SparkValueWriters.strings();
        case FIXED:
          return ValueWriters.fixed(primitive.getFixedSize());
        case BYTES:
          return ValueWriters.bytes();
        default:
          throw new IllegalArgumentException("Unsupported type: " + primitive);
      }
    }
  }
}
