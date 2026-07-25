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

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.apache.iceberg.PartitionField;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundExpressionVisitor;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.util.NaNUtil;

/**
 * 模块：api，表达式层。
 *
 * <p>职责：在给定 {@link PartitionSpec 分区规约} 下，利用分区值对表达式进行部分求值，得到残差表达式（residual）。
 *
 * <p>设计意图：分区裁剪只能定位到候选分区，但分区内仍可能存在不满足过滤条件的行。 残差表达式即"分区值已知后，原过滤条件中尚未被分区完全确定、需要在行级别继续求值的部分"。
 *
 * <p>示例：表按 day(utc_timestamp) 分区，过滤条件为 utc_timestamp &gt;= a and utc_timestamp &lt;= b， 对分区数据 d
 * 有四种残差：
 *
 * <ul>
 *   <li>d &gt; day(a) 且 d &lt; day(b)：残差恒为 true
 *   <li>d == day(a) 且 d != day(b)：残差为 utc_timestamp &gt;= a
 *   <li>d == day(b) 且 d != day(a)：残差为 utc_timestamp &lt;= b
 *   <li>d == day(a) == day(b)：残差为 utc_timestamp &gt;= a and utc_timestamp &lt;= b
 * </ul>
 *
 * <p>分区数据通过 {@link StructLike} 传入，残差由 {@link #residualFor(StructLike)} 返回。该类线程安全。
 *
 * <p>上下游关系：上游为查询过滤表达式，下游被扫描读取流程调用以减少行级数据读取量。
 */
public class ResidualEvaluator implements Serializable {
  /** 针对非分区表的残差求值器：由于无分区可利用，残差恒等于原表达式。 */
  private static class UnpartitionedResidualEvaluator extends ResidualEvaluator {
    private final Expression expr;

    UnpartitionedResidualEvaluator(Expression expr) {
      super(PartitionSpec.unpartitioned(), expr, false);
      this.expr = expr;
    }

    @Override
    public Expression residualFor(StructLike ignored) {
      return expr;
    }
  }

  /**
   * 为非分区 {@link PartitionSpec 规约} 返回残差求值器。
   *
   * @param expr 过滤表达式
   * @return 始终返回原表达式的残差求值器
   */
  public static ResidualEvaluator unpartitioned(Expression expr) {
    return new UnpartitionedResidualEvaluator(expr);
  }

  /**
   * 根据 {@link PartitionSpec 分区规约} 与 {@link Expression 表达式} 构造残差求值器。
   *
   * <p>若分区规约无分区字段，则返回非分区版本（直接返回原表达式）。
   *
   * @param spec 分区规约
   * @param expr 过滤表达式
   * @param caseSensitive 列名匹配是否区分大小写
   * @return 该表达式对应的残差求值器
   */
  public static ResidualEvaluator of(PartitionSpec spec, Expression expr, boolean caseSensitive) {
    if (spec.fields().size() > 0) {
      return new ResidualEvaluator(spec, expr, caseSensitive);
    } else {
      return unpartitioned(expr);
    }
  }

  private final PartitionSpec spec;
  private final Expression expr;
  private final boolean caseSensitive;

  private ResidualEvaluator(PartitionSpec spec, Expression expr, boolean caseSensitive) {
    this.spec = spec;
    this.expr = expr;
    this.caseSensitive = caseSensitive;
  }

  /**
   * 返回给定分区值对应的残差表达式。
   *
   * @param partitionData 分区数据值
   * @return 当前求值器表达式在该分区值下的残差
   */
  public Expression residualFor(StructLike partitionData) {
    return new ResidualVisitor().eval(partitionData);
  }

  /** 残差求值访问者：基于分区值对绑定谓词进行部分求值。 对能被分区值完全确定的谓词返回常量（alwaysTrue/alwaysFalse），否则保留原谓词作为残差。 */
  private class ResidualVisitor extends BoundExpressionVisitor<Expression> {
    private StructLike struct;

    /** 设置分区数据并遍历表达式树求值。 */
    private Expression eval(StructLike dataStruct) {
      this.struct = dataStruct;
      return ExpressionVisitors.visit(expr, this);
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
    public <T> Expression isNull(BoundReference<T> ref) {
      return (ref.eval(struct) == null) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression notNull(BoundReference<T> ref) {
      return (ref.eval(struct) != null) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression isNaN(BoundReference<T> ref) {
      return NaNUtil.isNaN(ref.eval(struct)) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression notNaN(BoundReference<T> ref) {
      return NaNUtil.isNaN(ref.eval(struct)) ? alwaysFalse() : alwaysTrue();
    }

    @Override
    public <T> Expression lt(BoundReference<T> ref, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return (cmp.compare(ref.eval(struct), lit.value()) < 0) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression ltEq(BoundReference<T> ref, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return (cmp.compare(ref.eval(struct), lit.value()) <= 0) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression gt(BoundReference<T> ref, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return (cmp.compare(ref.eval(struct), lit.value()) > 0) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression gtEq(BoundReference<T> ref, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return (cmp.compare(ref.eval(struct), lit.value()) >= 0) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression eq(BoundReference<T> ref, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return (cmp.compare(ref.eval(struct), lit.value()) == 0) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression notEq(BoundReference<T> ref, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return (cmp.compare(ref.eval(struct), lit.value()) != 0) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression in(BoundReference<T> ref, Set<T> literalSet) {
      return literalSet.contains(ref.eval(struct)) ? alwaysTrue() : alwaysFalse();
    }

    @Override
    public <T> Expression notIn(BoundReference<T> ref, Set<T> literalSet) {
      return literalSet.contains(ref.eval(struct)) ? alwaysFalse() : alwaysTrue();
    }

    @Override
    public <T> Expression startsWith(BoundReference<T> ref, Literal<T> lit) {
      return ((String) ref.eval(struct)).startsWith((String) lit.value())
          ? alwaysTrue()
          : alwaysFalse();
    }

    @Override
    public <T> Expression notStartsWith(BoundReference<T> ref, Literal<T> lit) {
      return ((String) ref.eval(struct)).startsWith((String) lit.value())
          ? alwaysFalse()
          : alwaysTrue();
    }

    /**
     * 求绑定谓词的残差。
     *
     * <p>逻辑：
     *
     * <ol>
     *   <li>取该谓词引用字段对应的所有分区字段；若无分区字段则直接返回原谓词（无法用分区值求值）
     *   <li>对每个分区字段计算 strict 投影并求值：若为 true，说明该分区内所有行必满足谓词，残差为 alwaysTrue
     *   <li>计算 inclusive 投影并求值：若为 false，说明该分区内所有行必不满足谓词，残差为 alwaysFalse
     *   <li>若两类投影都无法定论，则返回原谓词作为残差
     * </ol>
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Expression predicate(BoundPredicate<T> pred) {
      // Get the strict projection and inclusive projection of this predicate in partition data,
      // then use them to determine whether to return the original predicate. The strict projection
      // returns true iff the original predicate would have returned true, so the predicate can be
      // eliminated if the strict projection evaluates to true. Similarly the inclusive projection
      // returns false iff the original predicate would have returned false, so the predicate can
      // also be eliminated if the inclusive projection evaluates to false.

      // If there is no strict projection or if it evaluates to false, then return the predicate.
      List<PartitionField> parts = spec.getFieldsBySourceId(pred.ref().fieldId());
      if (parts == null) {
        return pred; // not associated inclusive a partition field, can't be evaluated
      }

      for (PartitionField part : parts) {

        // checking the strict projection
        UnboundPredicate<?> strictProjection =
            ((Transform<T, ?>) part.transform()).projectStrict(part.name(), pred);
        Expression strictResult = null;

        if (strictProjection != null) {
          Expression bound = strictProjection.bind(spec.partitionType(), caseSensitive);
          if (bound instanceof BoundPredicate) {
            strictResult = super.predicate((BoundPredicate<?>) bound);
          } else {
            // if the result is not a predicate, then it must be a constant like alwaysTrue or
            // alwaysFalse
            strictResult = bound;
          }
        }

        if (strictResult != null && strictResult.op() == Expression.Operation.TRUE) {
          // If strict is true, returning true
          return Expressions.alwaysTrue();
        }

        // checking the inclusive projection
        UnboundPredicate<?> inclusiveProjection =
            ((Transform<T, ?>) part.transform()).project(part.name(), pred);
        Expression inclusiveResult = null;
        if (inclusiveProjection != null) {
          Expression boundInclusive = inclusiveProjection.bind(spec.partitionType(), caseSensitive);
          if (boundInclusive instanceof BoundPredicate) {
            // using predicate method specific to inclusive
            inclusiveResult = super.predicate((BoundPredicate<?>) boundInclusive);
          } else {
            // if the result is not a predicate, then it must be a constant like alwaysTrue or
            // alwaysFalse
            inclusiveResult = boundInclusive;
          }
        }

        if (inclusiveResult != null && inclusiveResult.op() == Expression.Operation.FALSE) {
          // If inclusive is false, returning false
          return Expressions.alwaysFalse();
        }
      }

      // neither strict not inclusive predicate was conclusive, returning the original pred
      return pred;
    }

    /**
     * 求未绑定谓词的残差：先按 schema 绑定，再委托 {@link #predicate(BoundPredicate)} 求值。 若结果仍是谓词则保留原未绑定谓词，否则返回常量残差。
     */
    @Override
    public <T> Expression predicate(UnboundPredicate<T> pred) {
      Expression bound = pred.bind(spec.schema().asStruct(), caseSensitive);

      if (bound instanceof BoundPredicate) {
        Expression boundResidual = predicate((BoundPredicate<?>) bound);
        if (boundResidual instanceof Predicate) {
          return pred; // replace inclusive original unbound predicate
        }
        return boundResidual; // use the non-predicate residual (e.g. alwaysTrue)
      }

      // if binding didn't result in a Predicate, return the expression
      return bound;
    }
  }
}
