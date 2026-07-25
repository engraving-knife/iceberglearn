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

import java.util.List;
import java.util.Set;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.expressions.ExpressionVisitors.ExpressionVisitor;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types.StructType;

/**
 * 表达式绑定器：将 {@link Expression} 中的未绑定命名引用替换为指向具体 struct schema 字段的绑定引用。
 *
 * <p>所属模块：iceberg-api（表达式体系的核心入口；把面向用户的“按名字写表达式”转换为面向 求值的“按字段 id 写表达式”，是表达式从声明到求值的关键桥梁）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>遍历表达式树，把 {@link UnboundPredicate} / {@link UnboundAggregate} 绑定到 {@link StructType}
 *       的具体字段上，得到对应的 Bound 形态。
 *   <li>在绑定过程中对字面量做类型转换（{@link Literal#to(Type)}），并对不可转换的情况 抛出 {@link ValidationException}。
 *   <li>支持在绑定阶段进行简化（如对必填字段 {@code isNull} 直接返回 alwaysFalse）。
 *   <li>提供 {@link #isBound(Expression)} 判定与 {@link #boundReferences} 字段收集。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>采用 Visitor 模式遍历表达式：BindVisitor 负责重建表达式树，遇到已绑定节点直接抛 IllegalStateException 以避免重复绑定。
 *   <li>caseSensitive 由调用方显式传入，以兼容不同引擎对字段名大小写的处理约定。
 *   <li>ReferenceVisitor 复用同一棵表达式树访问得到字段 id 集合，用于扫描时收集投影字段。
 * </ul>
 *
 * <p>上下游关系：被 {@link AggregateEvaluator}、core 模块的扫描规划、各种 Evaluator 调用。
 */
public class Binder {
  private Binder() {}

  /**
   * 将表达式中所有未绑定命名引用替换为指向给定 struct 字段的绑定引用。
   *
   * <p>逻辑：使用 {@link BindVisitor} 访问表达式树。引用解析时，谓词中的字面量会通过 {@link Literal#to(Type)}
   * 转换为字段类型；若不允许自动转换则抛 {@link ValidationException}。结果表达式可能被简化，例如对必填字段 {@code isNull("a")} 会变为
   * {@code alwaysFalse()}。
   *
   * <p>表达式中不能包含已绑定引用，否则抛 {@link IllegalStateException}。
   *
   * @param struct 用于按名字解析引用的 {@link StructType}
   * @param expr 待重写为绑定引用的表达式
   * @param caseSensitive 是否对字段名大小写敏感
   * @return 重写后的绑定表达式
   * @throws ValidationException 字面量与绑定引用类型不匹配时抛出
   * @throws IllegalStateException 表达式中存在已绑定引用时抛出
   */
  public static Expression bind(StructType struct, Expression expr, boolean caseSensitive) {
    return ExpressionVisitors.visit(expr, new BindVisitor(struct, caseSensitive));
  }

  /**
   * 默认大小写敏感地绑定表达式（包级可见，仅供同包测试使用）。
   *
   * @param struct 用于解析引用的 {@link StructType}
   * @param expr 待绑定的表达式
   * @return 重写后的绑定表达式
   * @throws IllegalStateException 表达式中存在已绑定引用时抛出
   */
  static Expression bind(StructType struct, Expression expr) {
    return Binder.bind(struct, expr, true);
  }

  /**
   * 收集表达式列表所引用的所有字段 id。
   *
   * <p>逻辑：若表达式列表为 null 返回空集；否则对每个表达式先判断是否已绑定：已绑定直接 访问，未绑定先调用 {@link #bind} 绑定后再访问，由 {@link
   * ReferenceVisitor} 累积字段 id。
   *
   * @param struct 用于解析引用的 {@link StructType}
   * @param exprs 表达式列表，可为 null
   * @param caseSensitive 是否对字段名大小写敏感
   * @return 表达式所引用字段的 id 集合
   */
  public static Set<Integer> boundReferences(
      StructType struct, List<Expression> exprs, boolean caseSensitive) {
    if (exprs == null) {
      return ImmutableSet.of();
    }
    ReferenceVisitor visitor = new ReferenceVisitor();
    for (Expression expr : exprs) {
      if (isBound(expr)) {
        ExpressionVisitors.visit(expr, visitor);
      } else {
        ExpressionVisitors.visit(bind(struct, expr, caseSensitive), visitor);
      }
    }
    return visitor.references;
  }

  /**
   * 判断表达式是否已绑定。
   *
   * <p>逻辑：使用 {@link IsBoundVisitor} 遍历表达式；当所有谓词均为 Bound 时返回 true， 全为 Unbound 时返回 false；若同时存在 Bound
   * 与 Unbound 谓词则抛 {@link IllegalArgumentException}。无法判定（如 alwaysTrue 等无谓词节点）时按未绑定处理。
   *
   * @param expr 待判定的表达式
   * @return 已绑定返回 true
   * @throws IllegalArgumentException 表达式同时存在已绑定与未绑定谓词时抛出
   */
  public static boolean isBound(Expression expr) {
    Boolean isBound = ExpressionVisitors.visit(expr, new IsBoundVisitor());
    return isBound != null ? isBound : false; // assume unbound if undetermined
  }

  /**
   * 绑定访问器：访问表达式树，把未绑定谓词/聚合绑定到 struct 字段，重建一棵 Bound 表达式树。
   *
   * <p>设计意图：通过 Visitor 模式保留表达式结构（and/or/not），仅替换叶子谓词/聚合。 遇到已绑定节点直接抛异常，确保不会重复绑定。
   */
  private static class BindVisitor extends ExpressionVisitor<Expression> {
    private final StructType struct;
    private final boolean caseSensitive;

    private BindVisitor(StructType struct, boolean caseSensitive) {
      this.struct = struct;
      this.caseSensitive = caseSensitive;
    }

    @Override
    public Expression alwaysTrue() {
      return Expressions.alwaysTrue();
    }

    @Override
    public Expression alwaysFalse() {
      return Expressions.alwaysFalse();
    }

    @Override
    public Expression not(Expression result) {
      return Expressions.not(result);
    }

    @Override
    public Expression and(Expression leftResult, Expression rightResult) {
      return Expressions.and(leftResult, rightResult);
    }

    @Override
    public Expression or(Expression leftResult, Expression rightResult) {
      return Expressions.or(leftResult, rightResult);
    }

    @Override
    public <T> Expression predicate(BoundPredicate<T> pred) {
      throw new IllegalStateException("Found already bound predicate: " + pred);
    }

    @Override
    public <T> Expression predicate(UnboundPredicate<T> pred) {
      return pred.bind(struct, caseSensitive);
    }

    @Override
    public <T> Expression aggregate(UnboundAggregate<T> agg) {
      return agg.bind(struct, caseSensitive);
    }

    @Override
    public <T, C> Expression aggregate(BoundAggregate<T, C> agg) {
      throw new IllegalStateException("Found already bound aggregate: " + agg);
    }
  }

  /**
   * 字段引用收集访问器：遍历已绑定表达式，收集所有被引用字段的 id。
   *
   * <p>设计意图：布尔逻辑节点（and/or/not/alwaysTrue/alwaysFalse）不引入新引用， 故直接返回累积集合；只在叶子 {@link BoundPredicate}
   * 处把字段 id 加入集合。
   */
  private static class ReferenceVisitor extends ExpressionVisitor<Set<Integer>> {
    private final Set<Integer> references = Sets.newHashSet();

    @Override
    public Set<Integer> alwaysTrue() {
      return references;
    }

    @Override
    public Set<Integer> alwaysFalse() {
      return references;
    }

    @Override
    public Set<Integer> not(Set<Integer> result) {
      return references;
    }

    @Override
    public Set<Integer> and(Set<Integer> leftResult, Set<Integer> rightResult) {
      return references;
    }

    @Override
    public Set<Integer> or(Set<Integer> leftResult, Set<Integer> rightResult) {
      return references;
    }

    @Override
    public <T> Set<Integer> predicate(BoundPredicate<T> pred) {
      references.add(pred.ref().fieldId());
      return references;
    }
  }

  /**
   * 绑定状态判定访问器：遍历表达式判定其是否已绑定。
   *
   * <p>设计意图：Bound 谓词返回 true、Unbound 谓词返回 false；and/or 通过 {@link #combineResults} 合并，若两侧状态不一致则抛
   * IllegalArgumentException 表示存在部分绑定表达式（这是不合法状态）。
   */
  private static class IsBoundVisitor extends ExpressionVisitor<Boolean> {
    @Override
    public Boolean not(Boolean result) {
      return result;
    }

    @Override
    public Boolean and(Boolean leftResult, Boolean rightResult) {
      return combineResults(leftResult, rightResult);
    }

    @Override
    public Boolean or(Boolean leftResult, Boolean rightResult) {
      return combineResults(leftResult, rightResult);
    }

    @Override
    public <T> Boolean predicate(BoundPredicate<T> pred) {
      return true;
    }

    @Override
    public <T> Boolean predicate(UnboundPredicate<T> pred) {
      return false;
    }

    /**
     * 合并左右子表达式的绑定状态。
     *
     * <p>逻辑：左侧非空时以左侧为准，但要求右侧要么为空、要么与左侧一致；否则抛 IllegalArgumentException 标记“部分绑定”非法状态。左侧为空时直接返回右侧。
     *
     * @param isLeftBound 左侧绑定状态
     * @param isRightBound 右侧绑定状态
     * @return 合并后的绑定状态
     * @throws IllegalArgumentException 当左右状态不一致（部分绑定）时抛出
     */
    private Boolean combineResults(Boolean isLeftBound, Boolean isRightBound) {
      if (isLeftBound != null) {
        Preconditions.checkArgument(
            isRightBound == null || isLeftBound.equals(isRightBound),
            "Found partially bound expression");
        return isLeftBound;
      } else {
        return isRightBound;
      }
    }
  }
}
