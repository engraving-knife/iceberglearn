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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import org.apache.flink.table.data.ArrayData;
import org.apache.flink.table.data.DecimalData;
import org.apache.flink.table.data.MapData;
import org.apache.flink.table.data.RawValueData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;
import org.apache.flink.table.data.TimestampData;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.types.RowKind;
import org.apache.flink.util.StringUtils;
import org.apache.iceberg.Schema;
import org.apache.iceberg.flink.FlinkSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：基于 Flink {@link RowData} 的投影包装器。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 data 子包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>在不复制数据的前提下，按投影 schema 从原始 RowData 中提取字段。
 *   <li>对嵌套 struct 做递归投影；对 list/map 仅支持整体投影或同类型投影。
 *   <li>实现 {@link RowData} 接口，对接 Flink 各类算子。
 * </ul>
 *
 * <p>设计意图：通过预编译的 FieldGetter 数组，把每次投影访问的开销降到一次方法调用， 避免反射与重复 schema 查找。注意不支持对 list/map 内部嵌套类型的部分投影。
 *
 * <p>上下游关系：上游为 Iceberg 文件扫描结果投影逻辑，下游为 Flink 算子 直接消费 RowData。
 */
public class RowDataProjection implements RowData {
  /**
   * 创建 RowData 投影包装器。
   *
   * <p>说明：本投影不会对 list/map 等重复类型的内部嵌套类型做投影。
   *
   * @param schema 原始行的 schema
   * @param projectedSchema 投影后的目标 schema
   * @return 投影包装器
   */
  public static RowDataProjection create(Schema schema, Schema projectedSchema) {
    return RowDataProjection.create(
        FlinkSchemaUtil.convert(schema), schema.asStruct(), projectedSchema.asStruct());
  }

  /**
   * 创建 RowData 投影包装器，显式传入 Flink 行类型。
   *
   * <p>说明：本投影不会对 list/map 等重复类型的内部嵌套类型做投影。
   *
   * @param rowType Flink 行类型
   * @param schema 原始行的 Iceberg struct 类型
   * @param projectedSchema 投影后的目标 struct 类型
   * @return 投影包装器
   */
  public static RowDataProjection create(
      RowType rowType, Types.StructType schema, Types.StructType projectedSchema) {
    return new RowDataProjection(rowType, schema, projectedSchema);
  }

  private final RowData.FieldGetter[] getters;
  private RowData rowData;

  /**
   * 私有构造，按投影 schema 预编译各字段的 FieldGetter。
   *
   * <p>逻辑：建立字段 ID 到原行下标的映射，对每个投影字段按类型递归构造 getter。
   */
  private RowDataProjection(
      RowType rowType, Types.StructType rowStruct, Types.StructType projectType) {
    Map<Integer, Integer> fieldIdToPosition = Maps.newHashMap();
    for (int i = 0; i < rowStruct.fields().size(); i++) {
      fieldIdToPosition.put(rowStruct.fields().get(i).fieldId(), i);
    }

    this.getters = new RowData.FieldGetter[projectType.fields().size()];
    for (int i = 0; i < getters.length; i++) {
      Types.NestedField projectField = projectType.fields().get(i);
      Types.NestedField rowField = rowStruct.field(projectField.fieldId());

      Preconditions.checkNotNull(
          rowField,
          "Cannot locate the project field <%s> in the iceberg struct <%s>",
          projectField,
          rowStruct);

      getters[i] =
          createFieldGetter(
              rowType, fieldIdToPosition.get(projectField.fieldId()), rowField, projectField);
    }
  }

  /**
   * 按字段类型构造对应的 FieldGetter。
   *
   * <p>逻辑：对 struct 递归构造嵌套投影；对 list/map 校验是否整体投影或同类型投影， 不允许对内部嵌套类型做部分投影；其他类型直接复用 Flink 默认 getter。
   */
  private static RowData.FieldGetter createFieldGetter(
      RowType rowType, int position, Types.NestedField rowField, Types.NestedField projectField) {
    Preconditions.checkArgument(
        rowField.type().typeId() == projectField.type().typeId(),
        "Different iceberg type between row field <%s> and project field <%s>",
        rowField,
        projectField);

    switch (projectField.type().typeId()) {
      case STRUCT:
        RowType nestedRowType = (RowType) rowType.getTypeAt(position);
        return row -> {
          // null nested struct value
          if (row.isNullAt(position)) {
            return null;
          }

          RowData nestedRow = row.getRow(position, nestedRowType.getFieldCount());
          return RowDataProjection.create(
                  nestedRowType, rowField.type().asStructType(), projectField.type().asStructType())
              .wrap(nestedRow);
        };

      case MAP:
        Types.MapType projectedMap = projectField.type().asMapType();
        Types.MapType originalMap = rowField.type().asMapType();

        boolean keyProjectable =
            !projectedMap.keyType().isNestedType()
                || projectedMap.keyType().equals(originalMap.keyType());
        boolean valueProjectable =
            !projectedMap.valueType().isNestedType()
                || projectedMap.valueType().equals(originalMap.valueType());
        Preconditions.checkArgument(
            keyProjectable && valueProjectable,
            "Cannot project a partial map key or value with non-primitive type. Trying to project <%s> out of <%s>",
            projectField,
            rowField);

        return RowData.createFieldGetter(rowType.getTypeAt(position), position);

      case LIST:
        Types.ListType projectedList = projectField.type().asListType();
        Types.ListType originalList = rowField.type().asListType();

        boolean elementProjectable =
            !projectedList.elementType().isNestedType()
                || projectedList.elementType().equals(originalList.elementType());
        Preconditions.checkArgument(
            elementProjectable,
            "Cannot project a partial list element with non-primitive type. Trying to project <%s> out of <%s>",
            projectField,
            rowField);

        return RowData.createFieldGetter(rowType.getTypeAt(position), position);

      default:
        return RowData.createFieldGetter(rowType.getTypeAt(position), position);
    }
  }

  public RowData wrap(RowData row) {
    // StructProjection allow wrapping null root struct object.
    // See more discussions in https://github.com/apache/iceberg/pull/7517.
    // RowDataProjection never allowed null root object to be wrapped.
    // Hence, it is fine to enforce strict Preconditions check here.
    Preconditions.checkArgument(row != null, "Invalid row data: null");
    this.rowData = row;
    return this;
  }

  private Object getValue(int pos) {
    Preconditions.checkState(rowData != null, "Row data not wrapped");
    return getters[pos].getFieldOrNull(rowData);
  }

  @Override
  public int getArity() {
    return getters.length;
  }

  @Override
  public RowKind getRowKind() {
    Preconditions.checkState(rowData != null, "Row data not wrapped");
    return rowData.getRowKind();
  }

  @Override
  public void setRowKind(RowKind kind) {
    throw new UnsupportedOperationException("Cannot set row kind in the RowDataProjection");
  }

  @Override
  public boolean isNullAt(int pos) {
    return getValue(pos) == null;
  }

  @Override
  public boolean getBoolean(int pos) {
    return (boolean) getValue(pos);
  }

  @Override
  public byte getByte(int pos) {
    return (byte) getValue(pos);
  }

  @Override
  public short getShort(int pos) {
    return (short) getValue(pos);
  }

  @Override
  public int getInt(int pos) {
    return (int) getValue(pos);
  }

  @Override
  public long getLong(int pos) {
    return (long) getValue(pos);
  }

  @Override
  public float getFloat(int pos) {
    return (float) getValue(pos);
  }

  @Override
  public double getDouble(int pos) {
    return (double) getValue(pos);
  }

  @Override
  public StringData getString(int pos) {
    return (StringData) getValue(pos);
  }

  @Override
  public DecimalData getDecimal(int pos, int precision, int scale) {
    return (DecimalData) getValue(pos);
  }

  @Override
  public TimestampData getTimestamp(int pos, int precision) {
    return (TimestampData) getValue(pos);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> RawValueData<T> getRawValue(int pos) {
    return (RawValueData<T>) getValue(pos);
  }

  @Override
  public byte[] getBinary(int pos) {
    return (byte[]) getValue(pos);
  }

  @Override
  public ArrayData getArray(int pos) {
    return (ArrayData) getValue(pos);
  }

  @Override
  public MapData getMap(int pos) {
    return (MapData) getValue(pos);
  }

  @Override
  public RowData getRow(int pos, int numFields) {
    return (RowData) getValue(pos);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof RowDataProjection)) {
      return false;
    }

    RowDataProjection that = (RowDataProjection) o;
    return deepEquals(that);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(getRowKind());
    for (int pos = 0; pos < getArity(); pos++) {
      if (!isNullAt(pos)) {
        // Arrays.deepHashCode handles array object properly
        result = 31 * result + Arrays.deepHashCode(new Object[] {getValue(pos)});
      }
    }

    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getRowKind().shortString()).append("(");
    for (int pos = 0; pos < getArity(); pos++) {
      if (pos != 0) {
        sb.append(",");
      }
      // copied the behavior from Flink GenericRowData
      sb.append(StringUtils.arrayAwareToString(getValue(pos)));
    }

    sb.append(")");
    return sb.toString();
  }

  private boolean deepEquals(RowDataProjection other) {
    if (getRowKind() != other.getRowKind()) {
      return false;
    }

    if (getArity() != other.getArity()) {
      return false;
    }

    for (int pos = 0; pos < getArity(); ++pos) {
      if (isNullAt(pos) && other.isNullAt(pos)) {
        continue;
      }

      if ((isNullAt(pos) && !other.isNullAt(pos)) || (!isNullAt(pos) && other.isNullAt(pos))) {
        return false;
      }

      // Objects.deepEquals handles array object properly
      if (!Objects.deepEquals(getValue(pos), other.getValue(pos))) {
        return false;
      }
    }

    return true;
  }
}
