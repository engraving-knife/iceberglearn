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
package org.apache.iceberg.arrow.vectorized;

import org.apache.arrow.vector.FieldVector;
import org.apache.iceberg.MetadataColumns;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.Dictionary;

/**
 * 文件级说明：单个 Arrow 向量及其读取辅助状态的容器。
 *
 * <p>所属模块：iceberg-arrow（向量化读取链路中列向量的承载体）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有一列的 Arrow {@link FieldVector}、列描述符、字典、空值持有者与 Iceberg 字段定义。
 *   <li>提供是否字典编码、是否 dummy 等状态查询，以及常量/删除/位置列的专用持有者子类。
 * </ul>
 *
 * <p>设计意图：把向量与其读取上下文（空值信息、字典、类型）打包传递，避免散落多处状态； 通过
 * ConstantVectorHolder/DeletedVectorHolder/PositionVectorHolder 等子类以空对象方式 表示不实际读文件的元数据列，使读取流程统一处理。
 *
 * <p>上下游关系：由 {@link VectorizedArrowReader} 产出；被 {@link ArrowBatchReader}、 {@link
 * ColumnVector}、{@link DictEncodedArrowConverter} 消费。
 */
public class VectorHolder {
  private final ColumnDescriptor columnDescriptor;
  private final FieldVector vector;
  private final boolean isDictionaryEncoded;
  private final Dictionary dictionary;
  private final NullabilityHolder nullabilityHolder;
  private final Types.NestedField icebergField;

  /**
   * 构造持有真实向量与读取状态的容器。
   *
   * <p>除 dictionary 外，其余字段不可为 null（dummy 持有者除外）。
   *
   * @param columnDescriptor Parquet 列描述符
   * @param vector Arrow 向量
   * @param isDictionaryEncoded 是否字典编码
   * @param dictionary Parquet 字典（可为 null）
   * @param holder 空值持有者
   * @param icebergField Iceberg 字段定义
   */
  public VectorHolder(
      ColumnDescriptor columnDescriptor,
      FieldVector vector,
      boolean isDictionaryEncoded,
      Dictionary dictionary,
      NullabilityHolder holder,
      Types.NestedField icebergField) {
    // All the fields except dictionary are not nullable unless it is a dummy holder
    Preconditions.checkNotNull(columnDescriptor, "ColumnDescriptor cannot be null");
    Preconditions.checkNotNull(vector, "Vector cannot be null");
    Preconditions.checkNotNull(holder, "NullabilityHolder cannot be null");
    Preconditions.checkNotNull(icebergField, "IcebergField cannot be null");
    this.columnDescriptor = columnDescriptor;
    this.vector = vector;
    this.isDictionaryEncoded = isDictionaryEncoded;
    this.dictionary = dictionary;
    this.nullabilityHolder = holder;
    this.icebergField = icebergField;
  }

  /** dummy 持有者的构造方法。 */
  private VectorHolder() {
    this(null);
  }

  /**
   * 类型化常量持有者的构造方法。
   *
   * @param field Iceberg 字段定义
   */
  private VectorHolder(Types.NestedField field) {
    columnDescriptor = null;
    vector = null;
    isDictionaryEncoded = false;
    dictionary = null;
    nullabilityHolder = null;
    icebergField = field;
  }

  /**
   * 位置向量持有者的构造方法。
   *
   * @param vec Arrow 向量
   * @param field Iceberg 字段定义
   * @param nulls 空值持有者
   */
  private VectorHolder(FieldVector vec, Types.NestedField field, NullabilityHolder nulls) {
    columnDescriptor = null;
    vector = vec;
    isDictionaryEncoded = false;
    dictionary = null;
    nullabilityHolder = nulls;
    icebergField = field;
  }

  /** 返回 Parquet 列描述符。 */
  public ColumnDescriptor descriptor() {
    return columnDescriptor;
  }

  /** 返回 Arrow 向量。 */
  public FieldVector vector() {
    return vector;
  }

  /** 是否字典编码。 */
  public boolean isDictionaryEncoded() {
    return isDictionaryEncoded;
  }

  /** 返回 Parquet 字典（可能为 null）。 */
  public Dictionary dictionary() {
    return dictionary;
  }

  /** 返回空值持有者。 */
  public NullabilityHolder nullabilityHolder() {
    return nullabilityHolder;
  }

  /** 返回 Iceberg 类型（字段为 null 时返回 null）。 */
  public Type icebergType() {
    return icebergField != null ? icebergField.type() : null;
  }

  /** 返回 Iceberg 字段定义。 */
  public Types.NestedField icebergField() {
    return icebergField;
  }

  /** 返回向量中的值数。 */
  public int numValues() {
    return vector.getValueCount();
  }

  /**
   * 创建类型化常量向量持有者。
   *
   * @param icebergField Iceberg 字段定义
   * @param numRows 行数
   * @param constantValue 常量值
   * @param <T> 常量类型
   * @return 常量持有者
   */
  public static <T> VectorHolder constantHolder(
      Types.NestedField icebergField, int numRows, T constantValue) {
    return new ConstantVectorHolder<>(icebergField, numRows, constantValue);
  }

  /**
   * 已废弃：创建无类型常量持有者，请改用类型化版本。
   *
   * @param numRows 行数
   * @param constantValue 常量值
   * @param <T> 常量类型
   * @return 常量持有者
   * @deprecated since 1.4.0，将在 1.5.0 移除，请使用类型化常量持有者
   */
  @Deprecated
  public static <T> VectorHolder constantHolder(int numRows, T constantValue) {
    return new ConstantVectorHolder<>(numRows, constantValue);
  }

  /** 创建删除标记列的持有者。 */
  public static VectorHolder deletedVectorHolder(int numRows) {
    return new DeletedVectorHolder(numRows);
  }

  /** 创建 dummy 占位持有者。 */
  public static VectorHolder dummyHolder(int numRows) {
    return new ConstantVectorHolder<>(numRows);
  }

  /** 是否为 dummy 占位（向量为 null）。 */
  public boolean isDummy() {
    return vector == null;
  }

  /**
   * 常量向量持有者：不实际产出向量值，消费者应使用 constantValue 填充列。
   *
   * @param <T> 常量值类型
   */
  public static class ConstantVectorHolder<T> extends VectorHolder {
    private final T constantValue;
    private final int numRows;

    /** 构造无值的 dummy 常量持有者。 */
    public ConstantVectorHolder(int numRows) {
      this.numRows = numRows;
      this.constantValue = null;
    }

    /**
     * 已废弃：构造无类型常量持有者。
     *
     * @deprecated since 1.4.0，将在 1.5.0 移除，请使用类型化构造方法
     */
    @Deprecated
    public ConstantVectorHolder(int numRows, T constantValue) {
      this.numRows = numRows;
      this.constantValue = constantValue;
    }

    /**
     * 构造类型化常量持有者。
     *
     * @param icebergField Iceberg 字段定义
     * @param numRows 行数
     * @param constantValue 常量值
     */
    public ConstantVectorHolder(Types.NestedField icebergField, int numRows, T constantValue) {
      super(icebergField);
      this.numRows = numRows;
      this.constantValue = constantValue;
    }

    @Override
    public int numValues() {
      return this.numRows;
    }

    /** 返回常量值。 */
    public Object getConstant() {
      return constantValue;
    }
  }

  /** 行位置列的持有者，包装位置向量与空值信息。 */
  public static class PositionVectorHolder extends VectorHolder {
    /** 构造位置向量持有者。 */
    public PositionVectorHolder(
        FieldVector vector, Types.NestedField icebergField, NullabilityHolder nulls) {
      super(vector, icebergField, nulls);
    }
  }

  /** 删除标记列的持有者，标记 IS_DELETED 元数据列。 */
  public static class DeletedVectorHolder extends VectorHolder {
    private final int numRows;

    /** 构造删除标记持有者，关联 {@link MetadataColumns#IS_DELETED} 字段。 */
    public DeletedVectorHolder(int numRows) {
      super(MetadataColumns.IS_DELETED);
      this.numRows = numRows;
    }

    @Override
    public int numValues() {
      return numRows;
    }
  }
}
