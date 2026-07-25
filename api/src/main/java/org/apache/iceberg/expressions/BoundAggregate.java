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
package org.apache.iceberg.expressions;

import java.util.Map;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

/**
 * 已绑定聚合表达式的抽象基类：聚合操作（COUNT/MIN/MAX 等）作用于一个 {@link BoundTerm}。
 *
 * <p>所属模块：iceberg-api（表达式体系的聚合分支；与 {@link BoundPredicate} 平行， 描述“从数据中累积出一个标量结果”的语义）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载聚合操作类型 {@link Operation} 与绑定的 term，提供 {@link #eval(StructLike)}、 {@link
 *       #eval(DataFile)}、{@link #hasValue(DataFile)}、{@link #newAggregator()} 等
 *       求值入口（具体子类必须覆盖相应方法，否则抛 UnsupportedOperationException）。
 *   <li>提供结果类型推导（{@link #type()}）、列名与可读描述（{@link #columnName()} / {@link #describe()}），用于结果 schema
 *       构建与可读输出。
 *   <li>定义 {@link Aggregator} 与 {@link NullSafeAggregator} 抽象，统一增量聚合与 null 安全处理。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>“先抛异常再由子类覆盖”的模板方法风格，使基类只描述协议而不臆测实现。
 *   <li>NullSafeAggregator 把“遇到 null 跳过 / 遇到缺失统计标记无效”的通用逻辑下沉到基类， 子类只需实现 {@link #update(Object)} 与
 *       {@link #current()} 两个钩子。
 * </ul>
 *
 * <p>上下游关系：由 {@link Binder} 把 {@link UnboundAggregate} 绑定得到；被 {@link AggregateEvaluator}
 * 持有并驱动求值；具体实现见 {@link CountAggregate}、 {@link MinAggregate}、{@link CountStar}、{@link
 * ValueAggregate} 等。
 *
 * @param <T> 聚合输入值的 Java 类型
 * @param <C> 聚合输出结果的 Java 类型
 */
public class BoundAggregate<T, C> extends Aggregate<BoundTerm<T>> implements Bound<C> {

  /**
   * 构造已绑定聚合。
   *
   * @param op 聚合操作类型
   * @param term 已绑定的 term（被聚合的字段或变换）
   */
  protected BoundAggregate(Operation op, BoundTerm<T> term) {
    super(op, term);
  }

  /**
   * 在一行数据上求此聚合的值（行级单点求值）。
   *
   * <p>基类默认抛出 {@link UnsupportedOperationException}，由具体子类按需覆盖。
   *
   * @param struct 一行数据
   * @return 该行对聚合的贡献值
   */
  @Override
  public C eval(StructLike struct) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement eval(StructLike)");
  }

  /**
   * 在数据文件上求此聚合的值（复用 DataFile 上预统计的列指标）。
   *
   * <p>基类默认抛出 {@link UnsupportedOperationException}，由支持文件级求值的子类覆盖。
   *
   * @param file 数据文件
   * @return 该文件对聚合的贡献值
   */
  C eval(DataFile file) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement eval(DataFile)");
  }

  /**
   * 判断该文件是否提供了本聚合所需的预统计值。
   *
   * <p>基类默认抛出 {@link UnsupportedOperationException}，由支持文件级求值的子类覆盖。
   *
   * @param file 数据文件
   * @return 文件具备该聚合所需统计值时返回 true
   */
  boolean hasValue(DataFile file) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement hasValue(DataFile)");
  }

  /**
   * 创建一个用于增量累积此聚合结果的 {@link Aggregator}。
   *
   * <p>基类默认抛出 {@link UnsupportedOperationException}，由具体子类覆盖以提供 累积实现。
   *
   * @return 新的聚合器实例
   */
  Aggregator<C> newAggregator() {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement newAggregator()");
  }

  /**
   * 返回此聚合所绑定的字段引用。
   *
   * @return 绑定字段引用
   */
  @Override
  public BoundReference<?> ref() {
    return term().ref();
  }

  /**
   * 推导此聚合的结果类型。
   *
   * <p>逻辑：COUNT / COUNT_STAR 一律返回 LongType；其余返回 term 自身类型 （例如 MAX/MIN 的结果类型与字段类型一致）。
   *
   * @return 聚合结果类型
   */
  public Type type() {
    if (op() == Operation.COUNT || op() == Operation.COUNT_STAR) {
      return Types.LongType.get();
    } else {
      return term().type();
    }
  }

  /**
   * 返回聚合涉及的列名。
   *
   * <p>逻辑：COUNT_STAR 返回 "*"；其他返回绑定字段名。
   *
   * @return 列名
   */
  public String columnName() {
    if (op() == Operation.COUNT_STAR) {
      return "*";
    } else {
      return ref().name();
    }
  }

  /**
   * 生成此聚合的可读描述（用于结果 schema 字段名等场景）。
   *
   * <p>逻辑：按操作类型拼装，例如 "count(*)"、"max(field)" 等；遇到不支持的操作抛异常。
   *
   * @return 可读描述字符串
   */
  public String describe() {
    switch (op()) {
      case COUNT_STAR:
        return "count(*)";
      case COUNT:
        return "count(" + ExpressionUtil.describe(term()) + ")";
      case MAX:
        return "max(" + ExpressionUtil.describe(term()) + ")";
      case MIN:
        return "min(" + ExpressionUtil.describe(term()) + ")";
      default:
        throw new UnsupportedOperationException("Unsupported aggregate type: " + op());
    }
  }

  /**
   * 从字段 id 到值的映射中安全取值（默认值 null）。
   *
   * <p>设计要点：map 可能为 null，此方法统一处理 null 情况，避免调用方重复判空。
   *
   * @param map 字段 id 到值的映射，可为 null
   * @param key 字段 id
   * @param <V> 值类型
   * @return 命中的值，或 null
   */
  <V> V safeGet(Map<Integer, V> map, int key) {
    return safeGet(map, key, null);
  }

  /**
   * 从字段 id 到值的映射中安全取值（带默认值）。
   *
   * <p>逻辑：map 为 null 时直接返回 defaultValue；否则返回 {@code map.getOrDefault}。
   *
   * @param map 字段 id 到值的映射，可为 null
   * @param key 字段 id
   * @param defaultValue 默认值
   * @param <V> 值类型
   * @return 命中的值，或 defaultValue
   */
  <V> V safeGet(Map<Integer, V> map, int key, V defaultValue) {
    if (map != null) {
      return map.getOrDefault(key, defaultValue);
    }

    return null;
  }

  /**
   * 聚合器接口：负责在多行/多文件上增量累积聚合结果。
   *
   * <p>设计意图：把“如何累积”从“如何求值”剥离，让 {@link AggregateEvaluator} 持有 一组 Aggregator 统一驱动 update/result。
   *
   * @param <R> 聚合结果类型
   */
  interface Aggregator<R> {
    /** 用一行数据更新聚合状态。 */
    void update(StructLike struct);

    /** 用一个数据文件更新聚合状态（复用预统计值）。 */
    void update(DataFile file);

    /** 判断该文件是否提供了本聚合所需统计值。 */
    boolean hasValue(DataFile file);

    /** 返回当前累积结果。 */
    R result();

    /** 返回聚合器是否仍处于有效状态。 */
    boolean isValid();
  }

  /**
   * NullSafeAggregator：把“跳过 null / 缺失即失效”的通用逻辑下沉到基类。
   *
   * <p>设计意图：所有具体聚合都遵循“输入为 null 时跳过、文件缺少统计值时整体失效”的规则， 此抽象类统一封装该规则，子类只需实现 {@link
   * #update(Object)}（累积一个非 null 值） 与 {@link #current()}（返回当前累积值）。
   *
   * <p>逻辑（文件级 update）：若已失效则直接返回；否则若文件不具备所需统计值则标记失效； 若文件统计值为 null 则跳过；否则累积该值。
   *
   * @param <T> 聚合输入类型
   * @param <R> 聚合结果类型
   */
  abstract static class NullSafeAggregator<T, R> implements Aggregator<R> {
    private final BoundAggregate<T, R> aggregate;
    private boolean isValid = true;

    NullSafeAggregator(BoundAggregate<T, R> aggregate) {
      this.aggregate = aggregate;
    }

    /** 子类钩子：累积一个非 null 的贡献值。 */
    protected abstract void update(R value);

    /** 子类钩子：返回当前累积结果。 */
    protected abstract R current();

    @Override
    public void update(StructLike struct) {
      R value = aggregate.eval(struct);
      if (value != null) {
        update(value);
      }
    }

    @Override
    public boolean hasValue(DataFile file) {
      return aggregate.hasValue(file);
    }

    @Override
    public void update(DataFile file) {
      if (isValid) {
        if (hasValue(file)) {
          R value = aggregate.eval(file);
          if (value != null) {
            update(value);
          }
        } else {
          this.isValid = false;
        }
      }
    }

    @Override
    public R result() {
      if (!isValid) {
        return null;
      }

      return current();
    }

    @Override
    public boolean isValid() {
      return this.isValid;
    }
  }
}
