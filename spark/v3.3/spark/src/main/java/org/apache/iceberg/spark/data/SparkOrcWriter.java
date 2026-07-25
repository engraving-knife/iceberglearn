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

import java.io.Serializable;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.iceberg.FieldMetrics;
import org.apache.iceberg.Schema;
import org.apache.iceberg.data.orc.GenericOrcWriters;
import org.apache.iceberg.orc.ORCSchemaUtil;
import org.apache.iceberg.orc.OrcRowWriter;
import org.apache.iceberg.orc.OrcSchemaWithTypeVisitor;
import org.apache.iceberg.orc.OrcValueWriter;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.orc.TypeDescription;
import org.apache.orc.storage.ql.exec.vector.VectorizedRowBatch;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.expressions.SpecializedGetters;

/**
 * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
 *
 * <p>所属模块：iceberg-spark v3.3。 类型：类 SparkOrcWriter。
 *
 * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
 */
public class SparkOrcWriter implements OrcRowWriter<InternalRow> {

  private final InternalRowWriter writer;

  /** 构造 SparkOrcWriter 实例。 */
  public SparkOrcWriter(Schema iSchema, TypeDescription orcSchema) {
    Preconditions.checkArgument(
        orcSchema.getCategory() == TypeDescription.Category.STRUCT,
        "Top level must be a struct " + orcSchema);

    writer =
        (InternalRowWriter) OrcSchemaWithTypeVisitor.visit(iSchema, orcSchema, new WriteBuilder());
  }

  /**
   * 写入数据。
   *
   * @param value 参数
   * @param output 参数
   */
  @Override
  public void write(InternalRow value, VectorizedRowBatch output) {
    Preconditions.checkArgument(value != null, "value must not be null");
    writer.writeRow(value, output);
  }

  /**
   * 写入数据。
   *
   * @return 结果对象
   */
  @Override
  public List<OrcValueWriter<?>> writers() {
    return writer.writers();
  }

  /**
   * 执行该方法的具体逻辑。
   *
   * @return 结果对象
   */
  @Override
  public Stream<FieldMetrics<?>> metrics() {
    return writer.metrics();
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的构建器，负责分步骤构造目标对象。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 WriteBuilder。
   *
   * <p>设计意图：建造者模式，分离复杂对象的构造与表示。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class WriteBuilder extends OrcSchemaWithTypeVisitor<OrcValueWriter<?>> {
    /** 构造 WriteBuilder 实例。 */
    private WriteBuilder() {}

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iStruct 参数
     * @param record 参数
     * @param names 参数
     * @param fields 参数
     * @return 结果对象
     */
    @Override
    public OrcValueWriter<?> record(
        Types.StructType iStruct,
        TypeDescription record,
        List<String> names,
        List<OrcValueWriter<?>> fields) {
      /** 执行该方法的具体逻辑。 */
      return new InternalRowWriter(fields, record.getChildren());
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iList 参数
     * @param array 参数
     * @param element 参数
     * @return 结果对象
     */
    @Override
    public OrcValueWriter<?> list(
        Types.ListType iList, TypeDescription array, OrcValueWriter<?> element) {
      return SparkOrcValueWriters.list(element, array.getChildren());
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iMap 参数
     * @param map 参数
     * @param key 参数
     * @param value 参数
     * @return 结果对象
     */
    @Override
    public OrcValueWriter<?> map(
        Types.MapType iMap, TypeDescription map, OrcValueWriter<?> key, OrcValueWriter<?> value) {
      return SparkOrcValueWriters.map(key, value, map.getChildren());
    }

    /**
     * 执行该方法的具体逻辑。
     *
     * @param iPrimitive 参数
     * @param primitive 参数
     * @return 结果对象
     */
    @Override
    public OrcValueWriter<?> primitive(Type.PrimitiveType iPrimitive, TypeDescription primitive) {
      switch (primitive.getCategory()) {
        case BOOLEAN:
          return GenericOrcWriters.booleans();
        case BYTE:
          return GenericOrcWriters.bytes();
        case SHORT:
          return GenericOrcWriters.shorts();
        case DATE:
        case INT:
          return GenericOrcWriters.ints();
        case LONG:
          return GenericOrcWriters.longs();
        case FLOAT:
          return GenericOrcWriters.floats(ORCSchemaUtil.fieldId(primitive));
        case DOUBLE:
          return GenericOrcWriters.doubles(ORCSchemaUtil.fieldId(primitive));
        case BINARY:
          if (Type.TypeID.UUID == iPrimitive.typeId()) {
            return SparkOrcValueWriters.uuids();
          }
          return GenericOrcWriters.byteArrays();
        case STRING:
        case CHAR:
        case VARCHAR:
          return SparkOrcValueWriters.strings();
        case DECIMAL:
          return SparkOrcValueWriters.decimal(primitive.getPrecision(), primitive.getScale());
        case TIMESTAMP_INSTANT:
        case TIMESTAMP:
          return SparkOrcValueWriters.timestampTz();
        default:
          throw new IllegalArgumentException("Unhandled type " + primitive);
      }
    }
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件的写入器，负责把 Spark 内部数据写入 Iceberg 底层存储。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：类 InternalRowWriter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  private static class InternalRowWriter extends GenericOrcWriters.StructWriter<InternalRow> {
    private final List<FieldGetter<?>> fieldGetters;

    InternalRowWriter(List<OrcValueWriter<?>> writers, List<TypeDescription> orcTypes) {
      super(writers);
      this.fieldGetters = Lists.newArrayListWithExpectedSize(orcTypes.size());

      for (TypeDescription orcType : orcTypes) {
        fieldGetters.add(createFieldGetter(orcType));
      }
    }

    /** 执行该方法的具体逻辑。 */
    @Override
    protected Object get(InternalRow struct, int index) {
      return fieldGetters.get(index).getFieldOrNull(struct, index);
    }
  }

  /** 创建并返回新实例。 */
  static FieldGetter<?> createFieldGetter(TypeDescription fieldType) {
    final FieldGetter<?> fieldGetter;
    switch (fieldType.getCategory()) {
      case BOOLEAN:
        fieldGetter = SpecializedGetters::getBoolean;
        break;
      case BYTE:
        fieldGetter = SpecializedGetters::getByte;
        break;
      case SHORT:
        fieldGetter = SpecializedGetters::getShort;
        break;
      case DATE:
      case INT:
        fieldGetter = SpecializedGetters::getInt;
        break;
      case LONG:
      case TIMESTAMP:
      case TIMESTAMP_INSTANT:
        fieldGetter = SpecializedGetters::getLong;
        break;
      case FLOAT:
        fieldGetter = SpecializedGetters::getFloat;
        break;
      case DOUBLE:
        fieldGetter = SpecializedGetters::getDouble;
        break;
      case BINARY:
        if (ORCSchemaUtil.BinaryType.UUID
            .toString()
            .equalsIgnoreCase(
                fieldType.getAttributeValue(ORCSchemaUtil.ICEBERG_BINARY_TYPE_ATTRIBUTE))) {
          fieldGetter = SpecializedGetters::getUTF8String;
        } else {
          fieldGetter = SpecializedGetters::getBinary;
        }
        // getBinary always makes a copy, so we don't need to worry about it
        // being changed behind our back.
        break;
      case DECIMAL:
        fieldGetter =
            (row, ordinal) ->
                row.getDecimal(ordinal, fieldType.getPrecision(), fieldType.getScale());
        break;
      case STRING:
      case CHAR:
      case VARCHAR:
        fieldGetter = SpecializedGetters::getUTF8String;
        break;
      case STRUCT:
        fieldGetter = (row, ordinal) -> row.getStruct(ordinal, fieldType.getChildren().size());
        break;
      case LIST:
        fieldGetter = SpecializedGetters::getArray;
        break;
      case MAP:
        fieldGetter = SpecializedGetters::getMap;
        break;
      default:
        throw new IllegalArgumentException(
            "Encountered an unsupported ORC type during a write from Spark.");
    }

    return (row, ordinal) -> {
      if (row.isNullAt(ordinal)) {
        return null;
      }
      return fieldGetter.getFieldOrNull(row, ordinal);
    };
  }

  /**
   * Iceberg 与 Spark 数据格式之间的读写转换组件。
   *
   * <p>所属模块：iceberg-spark v3.3。 类型：接口 FieldGetter。
   *
   * <p>上下游：被 SparkScan/SparkWrite 调用，依赖 Iceberg 文件格式读取/写入 API。
   */
  interface FieldGetter<T> extends Serializable {

    /** 返回fieldornull。 */
    @Nullable
    T getFieldOrNull(SpecializedGetters row, int ordinal);
  }
}
