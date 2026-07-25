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
package org.apache.iceberg.mr.hive.serde.objectinspector;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorUtils;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：Iceberg {@link Record} 在 Hive 侧的 struct ObjectInspector。
 *
 * <p>所属模块：iceberg-mr（hive 子包 serde/objectinspector 下）；是 Hive SerDe 读取 Iceberg Record 时访问字段值的入口）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link StructObjectInspector}，让 Hive 能按字段名/位置访问 Iceberg Record。
 *   <li>字段名统一转小写，匹配 Hive 大小写不敏感的列名约定。
 *   <li>提供空 schema 的单例 {@link #empty()}，用于 null schema 场景。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>每个字段用 {@link IcebergRecordStructField} 包装，记录其在 Record 中的位置 position， 避免按名查找的开销。
 *   <li>重写 equals/hashCode，基于 structFields 列表，便于 ObjectInspector 复用与缓存。
 * </ul>
 *
 * <p>上下游关系：上游由 {@link IcebergObjectInspector#struct} 创建；被 Hive SerDe 在读取 struct 字段时调用。
 */
public final class IcebergRecordObjectInspector extends StructObjectInspector {

  private static final IcebergRecordObjectInspector EMPTY =
      new IcebergRecordObjectInspector(Types.StructType.of(), Collections.emptyList());

  private final List<IcebergRecordStructField> structFields;

  /**
   * 构造 Record ObjectInspector。
   *
   * <p>逻辑：遍历 structType 字段，把字段名转小写后与对应 ObjectInspector 一起包装成 {@link IcebergRecordStructField}，记录位置
   * position。
   *
   * @param structType Iceberg struct 类型
   * @param objectInspectors 各字段对应的 ObjectInspector 列表，需与 structType 字段数一致
   */
  public IcebergRecordObjectInspector(
      Types.StructType structType, List<ObjectInspector> objectInspectors) {
    Preconditions.checkArgument(structType.fields().size() == objectInspectors.size());

    this.structFields = Lists.newArrayListWithExpectedSize(structType.fields().size());

    int position = 0;

    for (Types.NestedField field : structType.fields()) {
      ObjectInspector oi = objectInspectors.get(position);
      Types.NestedField fieldInLowercase =
          Types.NestedField.of(
              field.fieldId(),
              field.isOptional(),
              field.name().toLowerCase(),
              field.type(),
              field.doc());
      IcebergRecordStructField structField =
          new IcebergRecordStructField(fieldInLowercase, oi, position);
      structFields.add(structField);
      position++;
    }
  }

  /** 返回空 schema 的单例 ObjectInspector。 */
  public static IcebergRecordObjectInspector empty() {
    return EMPTY;
  }

  /** 返回所有 struct 字段列表。 */
  @Override
  public List<? extends StructField> getAllStructFieldRefs() {
    return structFields;
  }

  /** 按名称查找 struct 字段，委托给 Hive 标准实现（大小写不敏感匹配）。 */
  @Override
  public StructField getStructFieldRef(String name) {
    return ObjectInspectorUtils.getStandardStructFieldRef(name, structFields);
  }

  /**
   * 取出 Record 中指定字段的值。
   *
   * @param o Iceberg Record
   * @param structField 字段引用（须为 {@link IcebergRecordStructField}）
   * @return 字段值；o 为 null 时返回 null
   */
  @Override
  public Object getStructFieldData(Object o, StructField structField) {
    if (o == null) {
      return null;
    }

    return ((Record) o).get(((IcebergRecordStructField) structField).position());
  }

  /**
   * 把 Record 转为字段值列表。
   *
   * @param o Iceberg Record
   * @return 字段值列表；o 为 null 时返回 null
   */
  @Override
  public List<Object> getStructFieldsDataAsList(Object o) {
    if (o == null) {
      return null;
    }

    Record record = (Record) o;
    return structFields.stream().map(f -> record.get(f.position())).collect(Collectors.toList());
  }

  /** 返回 struct 类型名（委托 Hive 标准实现）。 */
  @Override
  public String getTypeName() {
    return ObjectInspectorUtils.getStandardStructTypeName(this);
  }

  /** 返回 ObjectInspector 类别，固定为 STRUCT。 */
  @Override
  public Category getCategory() {
    return Category.STRUCT;
  }

  /** 相等性：基于 structFields 列表。 */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    IcebergRecordObjectInspector that = (IcebergRecordObjectInspector) o;
    return structFields.equals(that.structFields);
  }

  /** 哈希：基于 structFields。 */
  @Override
  public int hashCode() {
    return structFields.hashCode();
  }

  /**
   * Iceberg struct 字段在 Hive 侧的 {@link StructField} 实现。
   *
   * <p>持有 Iceberg {@link Types.NestedField}、字段 ObjectInspector、在 Record 中的位置 position。
   */
  private static class IcebergRecordStructField implements StructField {

    private final Types.NestedField field;
    private final ObjectInspector oi;
    private final int position;

    IcebergRecordStructField(Types.NestedField field, ObjectInspector oi, int position) {
      this.field = field;
      this.oi = oi;
      this.position = position; // position in the record
    }

    /** 返回字段名（已转小写）。 */
    @Override
    public String getFieldName() {
      return field.name();
    }

    /** 返回字段对应的 ObjectInspector。 */
    @Override
    public ObjectInspector getFieldObjectInspector() {
      return oi;
    }

    /** 返回 Iceberg 字段 ID。 */
    @Override
    public int getFieldID() {
      return field.fieldId();
    }

    /** 返回字段注释（Iceberg 字段 doc）。 */
    @Override
    public String getFieldComment() {
      return field.doc();
    }

    /** 返回字段在 Record 中的位置索引。 */
    int position() {
      return position;
    }

    /** 相等性：基于 field、oi、position 三者。 */
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      IcebergRecordStructField that = (IcebergRecordStructField) o;
      return field.equals(that.field) && oi.equals(that.oi) && position == that.position;
    }

    /** 哈希：基于 field、oi、position。 */
    @Override
    public int hashCode() {
      return Objects.hash(field, oi, position);
    }
  }
}
