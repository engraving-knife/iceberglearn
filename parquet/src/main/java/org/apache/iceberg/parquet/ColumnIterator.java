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
import org.apache.parquet.io.api.Binary;

/**
 * 文件级说明：Parquet 列迭代器，按原始类型逐值（Triple）读取一列数据。
 *
 * <p>所属模块：iceberg-parquet（Parquet 列式读取底座，向 ParquetValueReader 提供值流）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>继承 BaseColumnIterator 复用 row group/分页推进逻辑。
 *   <li>实现 TripleIterator 接口，按 Parquet 原始类型提供 nextBoolean/nextInteger/
 *       nextLong/nextFloat/nextDouble/nextBinary 等取值方法。
 *   <li>每次取值前递增已读计数并触发 advance() 自动推进页。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>工厂 + 匿名子类：newIterator 根据原始类型创建带类型返回值的子类实例， 使调用方拿到强类型的 ColumnIterator，避免装箱歧义。
 *   <li>委托页迭代器：具体解码（含 def/rep level、字典解码）交给 PageIterator， 本类只负责跨页推进与计数。
 * </ul>
 *
 * <p>上下游关系：依赖 PageIterator 与 BaseColumnIterator；被 ParquetValueReader 实现使用以读取原始值。
 */
public abstract class ColumnIterator<T> extends BaseColumnIterator implements TripleIterator<T> {
  /**
   * 按列的 Parquet 原始类型创建对应的强类型 ColumnIterator。
   *
   * <p>逻辑：根据 desc 的 PrimitiveTypeName 分支，为 BOOLEAN/INT32/INT64/INT96/FLOAT/DOUBLE/ BINARY
   * 等类型构造匿名子类，子类重写 next() 调用对应的 nextXxx() 方法， 从而返回正确 Java 类型。
   *
   * @param desc 列描述符
   * @param writerVersion Parquet writer 版本，用于选择页迭代器实现
   * @param <T> 值的 Java 类型
   * @return 强类型列迭代器
   * @throws UnsupportedOperationException 若原始类型不支持
   */
  @SuppressWarnings("unchecked")
  static <T> ColumnIterator<T> newIterator(ColumnDescriptor desc, String writerVersion) {
    switch (desc.getPrimitiveType().getPrimitiveTypeName()) {
      case BOOLEAN:
        return (ColumnIterator<T>)
            new ColumnIterator<Boolean>(desc, writerVersion) {
              @Override
              public Boolean next() {
                return nextBoolean();
              }
            };
      case INT32:
        return (ColumnIterator<T>)
            new ColumnIterator<Integer>(desc, writerVersion) {
              @Override
              public Integer next() {
                return nextInteger();
              }
            };
      case INT64:
        return (ColumnIterator<T>)
            new ColumnIterator<Long>(desc, writerVersion) {
              @Override
              public Long next() {
                return nextLong();
              }
            };
      case INT96:
        return (ColumnIterator<T>)
            new ColumnIterator<Binary>(desc, writerVersion) {
              @Override
              public Binary next() {
                return nextBinary();
              }
            };
      case FLOAT:
        return (ColumnIterator<T>)
            new ColumnIterator<Float>(desc, writerVersion) {
              @Override
              public Float next() {
                return nextFloat();
              }
            };
      case DOUBLE:
        return (ColumnIterator<T>)
            new ColumnIterator<Double>(desc, writerVersion) {
              @Override
              public Double next() {
                return nextDouble();
              }
            };
      case FIXED_LEN_BYTE_ARRAY:
      case BINARY:
        return (ColumnIterator<T>)
            new ColumnIterator<Binary>(desc, writerVersion) {
              @Override
              public Binary next() {
                return nextBinary();
              }
            };
      default:
        throw new UnsupportedOperationException(
            "Unsupported primitive type: " + desc.getPrimitiveType().getPrimitiveTypeName());
    }
  }

  private final PageIterator<T> pageIterator;

  /**
   * 构造列迭代器，并按 writer 版本创建对应页迭代器。
   *
   * @param desc 列描述符
   * @param writerVersion Parquet writer 版本
   */
  private ColumnIterator(ColumnDescriptor desc, String writerVersion) {
    super(desc);
    this.pageIterator = PageIterator.newIterator(desc, writerVersion);
  }

  /**
   * 返回当前三元组的定义级别（def level），表示该值在 schema 路径上的可选嵌套深度。
   *
   * @return 当前定义级别
   */
  @Override
  public int currentDefinitionLevel() {
    advance();
    return pageIterator.currentDefinitionLevel();
  }

  /**
   * 返回当前三元组的重复级别（rep level），表示该值所在重复嵌套层级。
   *
   * @return 当前重复级别
   */
  @Override
  public int currentRepetitionLevel() {
    advance();
    return pageIterator.currentRepetitionLevel();
  }

  /** 读取下一个 boolean 值，并递增已读计数、按需推进页。 */
  @Override
  public boolean nextBoolean() {
    this.triplesRead += 1;
    advance();
    return pageIterator.nextBoolean();
  }

  /** 读取下一个 int 值，并递增已读计数、按需推进页。 */
  @Override
  public int nextInteger() {
    this.triplesRead += 1;
    advance();
    return pageIterator.nextInteger();
  }

  /** 读取下一个 long 值，并递增已读计数、按需推进页。 */
  @Override
  public long nextLong() {
    this.triplesRead += 1;
    advance();
    return pageIterator.nextLong();
  }

  /** 读取下一个 float 值，并递增已读计数、按需推进页。 */
  @Override
  public float nextFloat() {
    this.triplesRead += 1;
    advance();
    return pageIterator.nextFloat();
  }

  /** 读取下一个 double 值，并递增已读计数、按需推进页。 */
  @Override
  public double nextDouble() {
    this.triplesRead += 1;
    advance();
    return pageIterator.nextDouble();
  }

  /** 读取下一个 Binary 值，并递增已读计数、按需推进页。 */
  @Override
  public Binary nextBinary() {
    this.triplesRead += 1;
    advance();
    return pageIterator.nextBinary();
  }

  /**
   * 读取一个 null 占位值，仅推进 def level 状态，不产生实际数据。
   *
   * @param <N> 形式类型，恒返回 null
   * @return null
   */
  @Override
  public <N> N nextNull() {
    this.triplesRead += 1;
    advance();
    return pageIterator.nextNull();
  }

  /** 返回当前页迭代器，供 BaseColumnIterator 推进页时使用。 */
  @Override
  protected BasePageIterator pageIterator() {
    return pageIterator;
  }
}
