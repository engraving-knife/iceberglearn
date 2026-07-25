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
package org.apache.iceberg.parquet;

import org.apache.parquet.column.ColumnDescriptor;
import org.apache.parquet.column.ColumnWriteStore;
import org.apache.parquet.io.api.Binary;

/**
 * 文件级说明：Parquet 列写入器（抽象），实现 {@link TripleWriter} 接口。
 *
 * <p>所属模块：iceberg-parquet（Parquet 列式写入底层基础设施）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>封装 Parquet {@link org.apache.parquet.column.ColumnWriter}，按列写入值三元组 （value + repetition
 *       level + definition level）。
 *   <li>提供类型安全的写入接口（writeBoolean/Integer/Long/Float/Double/Binary）， 并通过工厂方法 {@link #newWriter}
 *       按原始类型分派到具体实现。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>工厂 + 匿名子类：{@link #newWriter} 根据 PrimitiveTypeName 创建带类型特化的 匿名 ColumnWriter，使 {@code
 *       write(rl, T)} 方法能正确路由到 writeBoolean/Integer 等方法。
 *   <li>统一 D 层级：非 null 值写入时使用 maxDefinitionLevel，null 值由 {@link #writeNull} 指定 D。
 * </ul>
 *
 * <p>上下游关系：实现 {@link TripleWriter}，被 ParquetValueWriters 的列写入器使用； 依赖 Parquet 的 ColumnWriteStore
 * 提供底层 ColumnWriter 实例。
 *
 * @param <T> 写入的值类型
 */
public abstract class ColumnWriter<T> implements TripleWriter<T> {
  /**
   * 工厂方法：按 Parquet 原始类型创建对应的 ColumnWriter。
   *
   * <p>逻辑：根据 desc 的 PrimitiveTypeName（BOOLEAN/INT32/INT64/FLOAT/DOUBLE/BINARY 等） 创建匿名子类，将泛型 write
   * 方法路由到具体类型的写入方法。
   *
   * @param desc Parquet 列描述符
   * @param <T> 值类型
   * @return 类型匹配的 ColumnWriter
   * @throws UnsupportedOperationException 若类型不受支持
   */
  @SuppressWarnings("unchecked")
  static <T> ColumnWriter<T> newWriter(ColumnDescriptor desc) {
    switch (desc.getPrimitiveType().getPrimitiveTypeName()) {
      case BOOLEAN:
        return (ColumnWriter<T>)
            new ColumnWriter<Boolean>(desc) {
              @Override
              public void write(int rl, Boolean value) {
                writeBoolean(rl, value);
              }
            };
      case INT32:
        return (ColumnWriter<T>)
            new ColumnWriter<Integer>(desc) {
              @Override
              public void write(int rl, Integer value) {
                writeInteger(rl, value);
              }
            };
      case INT64:
        return (ColumnWriter<T>)
            new ColumnWriter<Long>(desc) {
              @Override
              public void write(int rl, Long value) {
                writeLong(rl, value);
              }
            };
      case FLOAT:
        return (ColumnWriter<T>)
            new ColumnWriter<Float>(desc) {
              @Override
              public void write(int rl, Float value) {
                writeFloat(rl, value);
              }
            };
      case DOUBLE:
        return (ColumnWriter<T>)
            new ColumnWriter<Double>(desc) {
              @Override
              public void write(int rl, Double value) {
                writeDouble(rl, value);
              }
            };
      case FIXED_LEN_BYTE_ARRAY:
      case BINARY:
        return (ColumnWriter<T>)
            new ColumnWriter<Binary>(desc) {
              @Override
              public void write(int rl, Binary value) {
                writeBinary(rl, value);
              }
            };
      default:
        throw new UnsupportedOperationException(
            "Unsupported primitive type: " + desc.getPrimitiveType().getPrimitiveTypeName());
    }
  }

  private final ColumnDescriptor desc;
  private final int maxDefinitionLevel;

  private org.apache.parquet.column.ColumnWriter columnWriter = null;

  private ColumnWriter(ColumnDescriptor desc) {
    this.desc = desc;
    this.maxDefinitionLevel = desc.getMaxDefinitionLevel();
  }

  /** 从 ColumnWriteStore 中获取底层 Parquet ColumnWriter 实例。 */
  public void setColumnStore(ColumnWriteStore columnStore) {
    this.columnWriter = columnStore.getColumnWriter(desc);
  }

  @Override
  public void writeBoolean(int rl, boolean value) {
    columnWriter.write(value, rl, maxDefinitionLevel);
  }

  @Override
  public void writeInteger(int rl, int value) {
    columnWriter.write(value, rl, maxDefinitionLevel);
  }

  @Override
  public void writeLong(int rl, long value) {
    columnWriter.write(value, rl, maxDefinitionLevel);
  }

  @Override
  public void writeFloat(int rl, float value) {
    columnWriter.write(value, rl, maxDefinitionLevel);
  }

  @Override
  public void writeDouble(int rl, double value) {
    columnWriter.write(value, rl, maxDefinitionLevel);
  }

  @Override
  public void writeBinary(int rl, Binary value) {
    columnWriter.write(value, rl, maxDefinitionLevel);
  }

  /**
   * 写入 null 值：按指定的 repetition level 和 definition level 写入空值。
   *
   * @param rl 重复级别
   * @param dl 定义级别（指示 null 在嵌套结构中的位置）
   */
  @Override
  public void writeNull(int rl, int dl) {
    columnWriter.writeNull(rl, dl);
  }
}
