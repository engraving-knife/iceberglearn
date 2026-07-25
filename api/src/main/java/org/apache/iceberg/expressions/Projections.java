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

import java.util.Collection;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.expressions.ExpressionVisitors.ExpressionVisitor;
import org.apache.iceberg.transforms.Transform;

/**
 * 模块：api，表达式层核心工具类。
 *
 * <p>职责：将基于数据行的表达式（row expression）投影为基于分区值的表达式（partition expression）， 是 Iceberg 分区裁剪（partition
 * pruning）的核心基础。
 *
 * <p>设计意图：提供两类投影——inclusive（包容）与 strict（严格）：
 *
 * <ul>
 *   <li>inclusive：若原表达式匹配某行，则投影后的表达式一定匹配该行所在分区（用于过滤候选分区）
 *   <li>strict：若投影后的表达式匹配某分区，则该分区内所有行一定匹配原表达式（用于确认匹配分区）
 * </ul>
 *
 * 通过访问者模式（{@link ExpressionVisitor}）遍历表达式树完成投影。
 *
 * <p>上下游关系：上游为查询谓词，下游被 {@code Scan} 等扫描规划逻辑调用， 内部依赖 {@link PartitionSpec} 与 {@link Transform}。
 */
public class Projections {
  private Projections() {}

  /**
   * 将数据行表达式投影为分区值表达式的访问者基类，绑定某个 {@link PartitionSpec 分区规约}。
   *
   * <p>投影分两类：inclusive（包容）与 strict（严格）。
   *
   * <ul>
   *   <li>inclusive：若表达式匹配某行，则投影表达式匹配该行所在分区
   *   <li>strict：若分区匹配投影表达式，则该分区所有行都匹配原表达式
   * </ul>
   */
  public abstract static class ProjectionEvaluator extends ExpressionVisitor<Expression> {
    /**
     * 将给定的数据行表达式投影为分区表达式。
     *
     * @param expr 基于数据行的表达式
     * @return 基于分区数据的表达式（具体语义由子类决定）
     */
    public abstract Expression project(Expression expr);
  }

  /**
   * 为指定 {@link PartitionSpec 分区规约} 创建一个 inclusive（包容）投影器，默认大小写敏感。
   *
   * <p>包容投影保证：若原表达式匹配某行，则投影表达式匹配该行所在分区。 每个谓词通过 {@link Transform#project(String, BoundPredicate)}
   * 完成投影。
   *
   * @param spec 分区规约
   * @return 该分区规约对应的包容投影器
   * @see Transform#project(String, BoundPredicate) 每个谓词使用的包容投影方法
   */
  public static ProjectionEvaluator inclusive(PartitionSpec spec) {
    return new InclusiveProjection(spec, true);
  }

  /**
   * 为指定 {@link PartitionSpec 分区规约} 创建一个 inclusive（包容）投影器，可指定大小写敏感。
   *
   * <p>包容投影保证：若原表达式匹配某行，则投影表达式匹配该行所在分区。 每个谓词通过 {@link Transform#project(String, BoundPredicate)}
   * 完成投影。
   *
   * @param spec 分区规约
   * @param caseSensitive 列名匹配是否区分大小写
   * @return 该分区规约对应的包容投影器
   * @see Transform#project(String, BoundPredicate) 每个谓词使用的包容投影方法
   */
  public static ProjectionEvaluator inclusive(PartitionSpec spec, boolean caseSensitive) {
    return new InclusiveProjection(spec, caseSensitive);
  }

  /**
   * 为指定 {@link PartitionSpec 分区规约} 创建一个 strict（严格）投影器，默认大小写敏感。
   *
   * <p>严格投影保证：若投影表达式匹配某分区，则该分区内所有行都匹配原表达式。 每个谓词通过 {@link Transform#projectStrict(String,
   * BoundPredicate)} 完成投影。
   *
   * @param spec 分区规约
   * @return 该分区规约对应的严格投影器
   * @see Transform#projectStrict(String, BoundPredicate) 每个谓词使用的严格投影方法
   */
  public static ProjectionEvaluator strict(PartitionSpec spec) {
    return new StrictProjection(spec, true);
  }

  /**
   * 为指定 {@link PartitionSpec 分区规约} 创建一个 strict（严格）投影器，可指定大小写敏感。
   *
   * <p>严格投影保证：若投影表达式匹配某分区，则该分区内所有行都匹配原表达式。 每个谓词通过 {@link Transform#projectStrict(String,
   * BoundPredicate)} 完成投影。
   *
   * @param spec 分区规约
   * @param caseSensitive 列名匹配是否区分大小写
   * @return 该分区规约对应的严格投影器
   * @see Transform#projectStrict(String, BoundPredicate) 每个谓词使用的严格投影方法
   */
  public static ProjectionEvaluator strict(PartitionSpec spec, boolean caseSensitive) {
    return new StrictProjection(spec, caseSensitive);
  }

  /** 投影器公共基类：持有分区规约与大小写敏感标志，实现表达式树的遍历与谓词绑定公共逻辑。 */
  private static class BaseProjectionEvaluator extends ProjectionEvaluator {
    private final PartitionSpec spec;
    private final boolean caseSensitive;

    private BaseProjectionEvaluator(PartitionSpec spec, boolean caseSensitive) {
      this.spec = spec;
      this.caseSensitive = caseSensitive;
    }

    /**
     * 将行表达式投影为分区表达式。
     *
     * <p>逻辑：先用 {@link RewriteNot} 将表达式树中的 NOT 下推到叶子节点（保证投影默认值正确）， 再以本访问者遍历表达式树完成投影。
     */
    @Override
    public Expression project(Expression expr) {
      // projections assume that there are no NOT nodes in the expression tree. to ensure that this
      // is the case, the expression is rewritten to push all NOT nodes down to the expression
      // leaf nodes.
      // this is necessary to ensure that the default expression returned when a predicate can't be
      // projected is correct.
      return ExpressionVisitors.visit(ExpressionVisitors.visit(expr, RewriteNot.get()), this);
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
      throw new UnsupportedOperationException("[BUG] project called on expression with a not");
    }

    @Override
    public Expression and(Expression leftResult, Expression rightResult) {
      return Expressions.and(leftResult, rightResult);
    }

    @Override
    public Expression or(Expression leftResult, Expression rightResult) {
      return Expressions.or(leftResult, rightResult);
    }

    /** 处理未绑定谓词：先按 schema 绑定为绑定谓词，再交由 {@link #predicate(BoundPredicate)} 完成具体投影。 */
    @Override
    public <T> Expression predicate(UnboundPredicate<T> pred) {
      Expression bound = pred.bind(spec.schema().asStruct(), caseSensitive);

      if (bound instanceof BoundPredicate) {
        return predicate((BoundPredicate<?>) bound);
      }

      return bound;
    }

    /** 返回当前分区规约。 */
    PartitionSpec spec() {
      return spec;
    }

    /** 返回是否区分列名大小写。 */
    boolean isCaseSensitive() {
      return caseSensitive;
    }
  }

  /** inclusive 投影实现：对每个分区字段调用 {@link Transform#project}，结果以 AND 组合。 */
  private static class InclusiveProjection extends BaseProjectionEvaluator {
    private InclusiveProjection(PartitionSpec spec, boolean caseSensitive) {
      super(spec, caseSensitive);
    }

    /**
     * inclusive 谓词投影。
     *
     * <p>逻辑：取出该谓词引用字段对应的所有分区字段；若无分区字段则返回 alwaysTrue（不裁剪）。 对每个分区字段调用其 transform 的 inclusive 投影，结果以
     * AND 组合（更严格的约束不会漏掉匹配分区）。
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Expression predicate(BoundPredicate<T> pred) {
      Collection<PartitionField> parts = spec().getFieldsBySourceId(pred.ref().fieldId());
      if (parts == null) {
        // the predicate has no partition column
        return Expressions.alwaysTrue();
      }

      Expression result = Expressions.alwaysTrue();
      for (PartitionField part : parts) {
        // consider (d = 2019-01-01) with bucket(7, d) and bucket(5, d)
        // projections: b1 = bucket(7, '2019-01-01') = 5, b2 = bucket(5, '2019-01-01') = 0
        // any value where b1 != 5 or any value where b2 != 0 cannot be the '2019-01-01'
        //
        // similarly, if partitioning by day(ts) and hour(ts), the more restrictive
        // projection should be used. ts = 2019-01-01T01:00:00 produces day=2019-01-01 and
        // hour=2019-01-01-01. the value will be in 2019-01-01-01 and not in 2019-01-01-02.
        UnboundPredicate<?> inclusiveProjection =
            ((Transform<T, ?>) part.transform()).project(part.name(), pred);
        if (inclusiveProjection != null) {
          result = Expressions.and(result, inclusiveProjection);
        }
      }

      return result;
    }
  }

  /** strict 投影实现：对每个分区字段调用 {@link Transform#projectStrict}，结果以 OR 组合。 */
  private static class StrictProjection extends BaseProjectionEvaluator {
    private StrictProjection(PartitionSpec spec, boolean caseSensitive) {
      super(spec, caseSensitive);
    }

    /**
     * strict 谓词投影。
     *
     * <p>逻辑：取出该谓词引用字段对应的所有分区字段；若无分区字段则返回 alwaysFalse（无法确认匹配）。 对每个分区字段调用其 transform 的 strict 投影，结果以
     * OR 组合（任一投影成立即确认分区匹配）。
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Expression predicate(BoundPredicate<T> pred) {
      Collection<PartitionField> parts = spec().getFieldsBySourceId(pred.ref().fieldId());
      if (parts == null) {
        // the predicate has no partition column
        return Expressions.alwaysFalse();
      }

      Expression result = Expressions.alwaysFalse();
      for (PartitionField part : parts) {
        // consider (ts > 2019-01-01T01:00:00) with day(ts) and hour(ts)
        // projections: d >= 2019-01-02 and h >= 2019-01-01-02 (note the inclusive bounds).
        // any timestamp where either projection predicate is true must match the original
        // predicate. For example, ts = 2019-01-01T03:00:00 matches the hour projection but not
        // the day, but does match the original predicate.
        UnboundPredicate<?> strictProjection =
            ((Transform<T, ?>) part.transform()).projectStrict(part.name(), pred);
        if (strictProjection != null) {
          result = Expressions.or(result, strictProjection);
        }
      }

      return result;
    }
  }
}
