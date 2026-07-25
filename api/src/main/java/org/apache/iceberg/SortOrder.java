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
package org.apache.iceberg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.BoundTerm;
import org.apache.iceberg.expressions.BoundTransform;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Term;
import org.apache.iceberg.expressions.UnboundTerm;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.Type;

/**
 * 定义表中数据与删除文件排序方式的 sort order。
 *
 * <p>所属模块：iceberg-api（表元数据抽象层）。
 *
 * <p>职责：持有一组 {@link SortField}，描述写入数据时如何对记录排序；提供与其它 sort order 的兼容性判断（{@link
 * #satisfies(SortOrder)}）、转换为未绑定形式等能力。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>orderId=0 保留给未排序场景，自定义排序 ID 从 1 起。
 *   <li>fields 以数组形式持久化以保证顺序稳定；fieldList 为 transient + 双检锁懒加载的 不可变视图，兼顾序列化体积与线程安全。
 *   <li>实现 {@link Serializable} 以支持在引擎中传递。
 * </ul>
 *
 * <p>上下游关系：由表元数据持有；被引擎写入路径用于排序，被扫描规划用于判断文件是否已按 期望顺序排列。
 */
public class SortOrder implements Serializable {
  private static final SortOrder UNSORTED_ORDER =
      new SortOrder(new Schema(), 0, Collections.emptyList());

  private final Schema schema;
  private final int orderId;
  private final SortField[] fields;

  private transient volatile List<SortField> fieldList;

  private SortOrder(Schema schema, int orderId, List<SortField> fields) {
    this.schema = schema;
    this.orderId = orderId;
    this.fields = fields.toArray(new SortField[0]);
  }

  /** 返回本 sort order 关联的 {@link Schema}。 */
  public Schema schema() {
    return schema;
  }

  /** 返回本 sort order 的 ID。 */
  public int orderId() {
    return orderId;
  }

  /** 返回本 sort order 的排序字段列表（不可变视图）。 */
  public List<SortField> fields() {
    return lazyFieldList();
  }

  /** 是否已排序（含至少一个排序字段）。 */
  public boolean isSorted() {
    return fields.length >= 1;
  }

  /** 是否未排序。 */
  public boolean isUnsorted() {
    return fields.length < 1;
  }

  /**
   * 判断本 sort order 是否满足另一个 sort order 的要求。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>任何排序都满足"未排序"要求；
   *   <li>本排序字段数少于目标时不能满足；
   *   <li>否则逐前缀字段比较，全部 satisfies 才返回 true。
   * </ul>
   *
   * @param anotherSortOrder 另一个 sort order
   * @return 本排序满足目标排序要求则返回 true
   */
  public boolean satisfies(SortOrder anotherSortOrder) {
    // any ordering satisfies an unsorted ordering
    if (anotherSortOrder.isUnsorted()) {
      return true;
    }

    // this ordering cannot satisfy an ordering with more sort fields
    if (anotherSortOrder.fields.length > fields.length) {
      return false;
    }

    // this ordering has either more or the same number of sort fields
    return IntStream.range(0, anotherSortOrder.fields.length)
        .allMatch(index -> fields[index].satisfies(anotherSortOrder.fields[index]));
  }

  /**
   * 判断本 sort order 与另一个 sort order 在忽略 orderId 的情况下是否等价。
   *
   * @param anotherSortOrder 另一个 sort order
   * @return 字段数组完全相等则返回 true
   */
  public boolean sameOrder(SortOrder anotherSortOrder) {
    return Arrays.equals(fields, anotherSortOrder.fields);
  }

  /** 双检锁懒构建字段列表的不可变视图。 */
  private List<SortField> lazyFieldList() {
    if (fieldList == null) {
      synchronized (this) {
        if (fieldList == null) {
          this.fieldList = ImmutableList.copyOf(fields);
        }
      }
    }
    return fieldList;
  }

  /**
   * 将本 sort order 转换为未绑定形式 {@link UnboundSortOrder}，便于序列化/跨 schema 传输。
   *
   * <p>逻辑：以 orderId 构造 builder，逐字段写入 transform 字符串、sourceId、方向、nullOrder。
   */
  public UnboundSortOrder toUnbound() {
    UnboundSortOrder.Builder builder = UnboundSortOrder.builder().withOrderId(orderId);

    for (SortField field : fields) {
      builder.addSortField(
          field.transform().toString(), field.sourceId(), field.direction(), field.nullOrder());
    }

    return builder.build();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (SortField field : fields) {
      sb.append("\n");
      sb.append("  ").append(field);
    }
    if (fields.length > 0) {
      sb.append("\n");
    }
    sb.append("]");
    return sb.toString();
  }

  /** 相等性：orderId 相同且字段数组相同。 */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    } else if (other == null || getClass() != other.getClass()) {
      return false;
    }

    SortOrder that = (SortOrder) other;
    return orderId == that.orderId && sameOrder(that);
  }

  @Override
  public int hashCode() {
    return 31 * Integer.hashCode(orderId) + Arrays.hashCode(fields);
  }

  /**
   * 返回未排序表的 sort order 单例。
   *
   * @return 未排序的 sort order
   */
  public static SortOrder unsorted() {
    return UNSORTED_ORDER;
  }

  /**
   * 为给定 {@link Schema} 创建 sort order 构建器。
   *
   * @param schema 表 schema
   * @return sort order 构建器
   */
  public static Builder builderFor(Schema schema) {
    return new Builder(schema);
  }

  /**
   * 用于构造合法 {@link SortOrder} 的构建器。
   *
   * <p>设计意图：实现 {@link SortOrderBuilder}，提供 asc/desc 等便捷方法；通过 term 绑定到 schema 后构造 SortField。通过
   * {@link #builderFor(Schema)} 创建实例。
   */
  public static class Builder implements SortOrderBuilder<Builder> {
    private final Schema schema;
    private final List<SortField> fields = Lists.newArrayList();
    private Integer orderId = null;
    private boolean caseSensitive = true;

    private Builder(Schema schema) {
      this.schema = schema;
    }

    /**
     * 添加一个升序排序字段，指定 null 排列方式。
     *
     * @param term 表达式 term
     * @param nullOrder null 排列方式（first 或 last）
     * @return this，便于链式调用
     */
    @Override
    public Builder asc(Term term, NullOrder nullOrder) {
      return addSortField(term, SortDirection.ASC, nullOrder);
    }

    /**
     * 添加一个降序排序字段，指定 null 排列方式。
     *
     * @param term 表达式 term
     * @param nullOrder null 排列方式（first 或 last）
     * @return this，便于链式调用
     */
    @Override
    public Builder desc(Term term, NullOrder nullOrder) {
      return addSortField(term, SortDirection.DESC, nullOrder);
    }

    /** 按列名添加排序字段，指定方向与 null 排列方式。 */
    public Builder sortBy(String name, SortDirection direction, NullOrder nullOrder) {
      return addSortField(Expressions.ref(name), direction, nullOrder);
    }

    /** 按 term 添加排序字段，指定方向与 null 排列方式。 */
    public Builder sortBy(Term term, SortDirection direction, NullOrder nullOrder) {
      return addSortField(term, direction, nullOrder);
    }

    /** 设置 sort order ID。 */
    public Builder withOrderId(int newOrderId) {
      this.orderId = newOrderId;
      return this;
    }

    /** 设置 term 绑定时是否大小写敏感。 */
    @Override
    public Builder caseSensitive(boolean sortCaseSensitive) {
      this.caseSensitive = sortCaseSensitive;
      return this;
    }

    /**
     * 添加一个排序字段。
     *
     * <p>逻辑：要求 term 是 {@link UnboundTerm}，按 caseSensitive 绑定到 schema； 由绑定结果取 sourceId 与 transform
     * 构造 SortField 加入列表。
     *
     * @param term 表达式 term（必须未绑定）
     * @param direction 排序方向
     * @param nullOrder null 排列方式
     * @return this，便于链式调用
     */
    private Builder addSortField(Term term, SortDirection direction, NullOrder nullOrder) {
      Preconditions.checkArgument(term instanceof UnboundTerm, "Term must be unbound");
      // ValidationException is thrown by bind if binding fails so we assume that boundTerm is
      // correct
      BoundTerm<?> boundTerm = ((UnboundTerm<?>) term).bind(schema.asStruct(), caseSensitive);
      int sourceId = boundTerm.ref().fieldId();
      SortField sortField = new SortField(toTransform(boundTerm), sourceId, direction, nullOrder);
      fields.add(sortField);
      return this;
    }

    /** 直接以已构造的 transform 添加排序字段（内部使用）。 */
    Builder addSortField(
        Transform<?, ?> transform, int sourceId, SortDirection direction, NullOrder nullOrder) {
      SortField sortField = new SortField(transform, sourceId, direction, nullOrder);
      fields.add(sortField);
      return this;
    }

    /**
     * 构建并校验 sort order。
     *
     * <p>逻辑：先 {@link #buildUnchecked()} 构造，再 {@link #checkCompatibility} 校验各字段 transform 与源类型兼容。
     */
    public SortOrder build() {
      SortOrder sortOrder = buildUnchecked();
      checkCompatibility(sortOrder, schema);
      return sortOrder;
    }

    /**
     * 不做兼容性校验直接构造 sort order。
     *
     * <p>逻辑：字段为空时返回 {@link #unsorted()}（orderId 必须为 0 或 null）；非空时 orderId 不能为 0（保留给未排序），未指定则默认 1。
     */
    SortOrder buildUnchecked() {
      if (fields.isEmpty()) {
        if (orderId != null && orderId != 0) {
          throw new IllegalArgumentException("Unsorted order ID must be 0");
        }
        return SortOrder.unsorted();
      }

      if (orderId != null && orderId == 0) {
        throw new IllegalArgumentException("Sort order ID 0 is reserved for unsorted order");
      }

      // default ID to 1 as 0 is reserved for unsorted order
      int actualOrderId = orderId != null ? orderId : 1;
      return new SortOrder(schema, actualOrderId, fields);
    }

    /**
     * 把绑定后的 term 转换为 transform。
     *
     * <p>逻辑：BoundReference 转为 identity transform；BoundTransform 取其内部 transform； 其他类型抛 {@link
     * ValidationException}。
     */
    private Transform<?, ?> toTransform(BoundTerm<?> term) {
      if (term instanceof BoundReference) {
        return Transforms.identity(term.type());
      } else if (term instanceof BoundTransform) {
        return ((BoundTransform<?, ?>) term).transform();
      } else {
        throw new ValidationException(
            "Invalid term: %s, expected either a bound reference or transform", term);
      }
    }
  }

  /**
   * 校验 sort order 中每个排序字段的 transform 与源类型兼容。
   *
   * <p>逻辑：对每个字段校验源类型存在、是基本类型、且 transform 可作用于该类型。
   *
   * @param sortOrder 待校验的 sort order
   * @param schema 表 schema
   */
  public static void checkCompatibility(SortOrder sortOrder, Schema schema) {
    for (SortField field : sortOrder.fields) {
      Type sourceType = schema.findType(field.sourceId());
      ValidationException.check(
          sourceType != null, "Cannot find source column for sort field: %s", field);
      ValidationException.check(
          sourceType.isPrimitiveType(),
          "Cannot sort by non-primitive source field: %s",
          sourceType);
      ValidationException.check(
          field.transform().canTransform(sourceType),
          "Invalid source type %s for transform: %s",
          sourceType,
          field.transform());
    }
  }
}
