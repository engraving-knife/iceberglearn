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

import java.util.Set;
import java.util.function.Supplier;
import org.apache.iceberg.exceptions.ValidationException;

/**
 * 表达式遍历工具：提供访问者（Visitor）抽象与多种遍历策略，用于在表达式树上做求值、投影、重写等。
 *
 * <p>所属模块：iceberg-api（表达式体系的核心基础设施，所有求值器/投影器/重写器均基于本类的 访问者与 {@link #visit} 方法实现）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>定义多种访问者基类：{@link ExpressionVisitor}（通用）、{@link BoundExpressionVisitor}
 *       （按引用细分的已绑定访问者）、{@link BoundVisitor}（按 term 细分的已绑定访问者）、 {@link
 *       CustomOrderExpressionVisitor}（支持自定义遍历顺序）。
 *   <li>提供遍历入口 {@link #visit(Expression, ExpressionVisitor)}（后序全量）、 {@link
 *       #visitEvaluator}（带短路的后序）、{@link #visit(Expression, CustomOrderExpressionVisitor)}
 *       （懒求值顺序可控）。
 * </ul>
 *
 * <p>设计意图：采用访问者模式把“树结构”与“在树上的操作”解耦——节点类（And/Or/Not/Predicate） 只负责持结构，具体语义由访问者实现；多种访问者基类适配不同场景（如
 * MetricsEval 需要引用、 Residual 需要引用、Evaluator 用 term）。后序遍历保证子节点结果先于父节点产出，便于聚合。
 *
 * <p>上下游关系：被 {@link Evaluator}、{@link InclusiveMetricsEvaluator}、{@link ResidualEvaluator}、 {@link
 * Projections}、{@link RewriteNot} 等广泛使用。
 */
public class ExpressionVisitors {

  private ExpressionVisitors() {}

  public abstract static class ExpressionVisitor<R> {
    public R alwaysTrue() {
      return null;
    }

    public R alwaysFalse() {
      return null;
    }

    public R not(R result) {
      return null;
    }

    public R and(R leftResult, R rightResult) {
      return null;
    }

    public R or(R leftResult, R rightResult) {
      return null;
    }

    public <T> R predicate(BoundPredicate<T> pred) {
      return null;
    }

    public <T> R predicate(UnboundPredicate<T> pred) {
      return null;
    }

    public <T, C> R aggregate(BoundAggregate<T, C> agg) {
      throw new UnsupportedOperationException("Cannot visit aggregate expression");
    }

    public <T> R aggregate(UnboundAggregate<T> agg) {
      throw new UnsupportedOperationException("Cannot visit aggregate expression");
    }
  }

  /**
   * 按引用细分的已绑定访问者：把已绑定谓词进一步分发到 isNull/lt/eq/in 等具体方法， 且仅处理 term 为 {@link BoundReference} 的情况。
   *
   * <p>设计意图：文件指标求值（{@link InclusiveMetricsEvaluator}）、残差求值 （{@link
   * ResidualEvaluator}）都直接基于字段引用工作，故提供此细粒度访问者； 非 reference 的 term（如变换）经 {@link #handleNonReference}
   * 处理（默认抛异常）。
   *
   * @param <R> 返回类型
   */
  public abstract static class BoundExpressionVisitor<R> extends ExpressionVisitor<R> {
    public <T> R isNull(BoundReference<T> ref) {
      return null;
    }

    public <T> R notNull(BoundReference<T> ref) {
      return null;
    }

    public <T> R isNaN(BoundReference<T> ref) {
      throw new UnsupportedOperationException(
          this.getClass().getName() + " does not implement isNaN");
    }

    public <T> R notNaN(BoundReference<T> ref) {
      throw new UnsupportedOperationException(
          this.getClass().getName() + " does not implement notNaN");
    }

    public <T> R lt(BoundReference<T> ref, Literal<T> lit) {
      return null;
    }

    public <T> R ltEq(BoundReference<T> ref, Literal<T> lit) {
      return null;
    }

    public <T> R gt(BoundReference<T> ref, Literal<T> lit) {
      return null;
    }

    public <T> R gtEq(BoundReference<T> ref, Literal<T> lit) {
      return null;
    }

    public <T> R eq(BoundReference<T> ref, Literal<T> lit) {
      return null;
    }

    public <T> R notEq(BoundReference<T> ref, Literal<T> lit) {
      return null;
    }

    public <T> R in(BoundReference<T> ref, Set<T> literalSet) {
      throw new UnsupportedOperationException("In expression is not supported by the visitor");
    }

    public <T> R notIn(BoundReference<T> ref, Set<T> literalSet) {
      throw new UnsupportedOperationException("notIn expression is not supported by the visitor");
    }

    public <T> R startsWith(BoundReference<T> ref, Literal<T> lit) {
      throw new UnsupportedOperationException(
          "startsWith expression is not supported by the visitor");
    }

    public <T> R notStartsWith(BoundReference<T> ref, Literal<T> lit) {
      throw new UnsupportedOperationException(
          "notStartsWith expression is not supported by the visitor");
    }

    /**
     * Handle a non-reference value in this visitor.
     *
     * <p>Visitors that require {@link BoundReference references} and not {@link Bound terms} can
     * use this method to return a default value for expressions with non-references. The default
     * implementation will throw a validation exception because the non-reference is not supported.
     *
     * @param term a non-reference bound expression
     * @param <T> a Java return type
     * @return a return value for the visitor
     */
    public <T> R handleNonReference(Bound<T> term) {
      throw new ValidationException("Visitor %s does not support non-reference: %s", this, term);
    }

    @Override
    public <T> R predicate(BoundPredicate<T> pred) {
      if (!(pred.term() instanceof BoundReference)) {
        return handleNonReference(pred.term());
      }

      if (pred.isLiteralPredicate()) {
        BoundLiteralPredicate<T> literalPred = pred.asLiteralPredicate();
        switch (pred.op()) {
          case LT:
            return lt((BoundReference<T>) pred.term(), literalPred.literal());
          case LT_EQ:
            return ltEq((BoundReference<T>) pred.term(), literalPred.literal());
          case GT:
            return gt((BoundReference<T>) pred.term(), literalPred.literal());
          case GT_EQ:
            return gtEq((BoundReference<T>) pred.term(), literalPred.literal());
          case EQ:
            return eq((BoundReference<T>) pred.term(), literalPred.literal());
          case NOT_EQ:
            return notEq((BoundReference<T>) pred.term(), literalPred.literal());
          case STARTS_WITH:
            return startsWith((BoundReference<T>) pred.term(), literalPred.literal());
          case NOT_STARTS_WITH:
            return notStartsWith((BoundReference<T>) pred.term(), literalPred.literal());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundLiteralPredicate: " + pred.op());
        }

      } else if (pred.isUnaryPredicate()) {
        switch (pred.op()) {
          case IS_NULL:
            return isNull((BoundReference<T>) pred.term());
          case NOT_NULL:
            return notNull((BoundReference<T>) pred.term());
          case IS_NAN:
            return isNaN((BoundReference<T>) pred.term());
          case NOT_NAN:
            return notNaN((BoundReference<T>) pred.term());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundUnaryPredicate: " + pred.op());
        }

      } else if (pred.isSetPredicate()) {
        switch (pred.op()) {
          case IN:
            return in((BoundReference<T>) pred.term(), pred.asSetPredicate().literalSet());
          case NOT_IN:
            return notIn((BoundReference<T>) pred.term(), pred.asSetPredicate().literalSet());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundSetPredicate: " + pred.op());
        }
      }

      throw new IllegalStateException("Unsupported bound predicate: " + pred.getClass().getName());
    }

    @Override
    public <T> R predicate(UnboundPredicate<T> pred) {
      throw new UnsupportedOperationException("Not a bound predicate: " + pred);
    }
  }

  /**
   * 按 term 细分的已绑定访问者：与 {@link BoundExpressionVisitor} 类似，但把具体方法参数 从 {@link BoundReference} 放宽为
   * {@link Bound}（任意已绑定 term）。
   *
   * <p>设计意图：行级求值（{@link Evaluator}）需要对任意 term（含变换）求值，故用本访问者； {@link #predicate(BoundPredicate)}
   * 默认实现按谓词子类型分发到 lt/eq/in 等方法。
   *
   * @param <R> 返回类型
   */
  public abstract static class BoundVisitor<R> extends ExpressionVisitor<R> {
    public <T> R isNull(Bound<T> expr) {
      return null;
    }

    public <T> R notNull(Bound<T> expr) {
      return null;
    }

    public <T> R isNaN(Bound<T> expr) {
      throw new UnsupportedOperationException(
          this.getClass().getName() + " does not implement isNaN");
    }

    public <T> R notNaN(Bound<T> expr) {
      throw new UnsupportedOperationException(
          this.getClass().getName() + " does not implement notNaN");
    }

    public <T> R lt(Bound<T> expr, Literal<T> lit) {
      return null;
    }

    public <T> R ltEq(Bound<T> expr, Literal<T> lit) {
      return null;
    }

    public <T> R gt(Bound<T> expr, Literal<T> lit) {
      return null;
    }

    public <T> R gtEq(Bound<T> expr, Literal<T> lit) {
      return null;
    }

    public <T> R eq(Bound<T> expr, Literal<T> lit) {
      return null;
    }

    public <T> R notEq(Bound<T> expr, Literal<T> lit) {
      return null;
    }

    public <T> R in(Bound<T> expr, Set<T> literalSet) {
      throw new UnsupportedOperationException("In operation is not supported by the visitor");
    }

    public <T> R notIn(Bound<T> expr, Set<T> literalSet) {
      throw new UnsupportedOperationException("notIn operation is not supported by the visitor");
    }

    public <T> R startsWith(Bound<T> expr, Literal<T> lit) {
      throw new UnsupportedOperationException("Unsupported operation.");
    }

    public <T> R notStartsWith(Bound<T> expr, Literal<T> lit) {
      throw new UnsupportedOperationException("Unsupported operation.");
    }

    @Override
    public <T> R predicate(BoundPredicate<T> pred) {
      if (pred.isLiteralPredicate()) {
        BoundLiteralPredicate<T> literalPred = pred.asLiteralPredicate();
        switch (pred.op()) {
          case LT:
            return lt(pred.term(), literalPred.literal());
          case LT_EQ:
            return ltEq(pred.term(), literalPred.literal());
          case GT:
            return gt(pred.term(), literalPred.literal());
          case GT_EQ:
            return gtEq(pred.term(), literalPred.literal());
          case EQ:
            return eq(pred.term(), literalPred.literal());
          case NOT_EQ:
            return notEq(pred.term(), literalPred.literal());
          case STARTS_WITH:
            return startsWith(pred.term(), literalPred.literal());
          case NOT_STARTS_WITH:
            return notStartsWith(pred.term(), literalPred.literal());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundLiteralPredicate: " + pred.op());
        }

      } else if (pred.isUnaryPredicate()) {
        switch (pred.op()) {
          case IS_NULL:
            return isNull(pred.term());
          case NOT_NULL:
            return notNull(pred.term());
          case IS_NAN:
            return isNaN(pred.term());
          case NOT_NAN:
            return notNaN(pred.term());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundUnaryPredicate: " + pred.op());
        }

      } else if (pred.isSetPredicate()) {
        switch (pred.op()) {
          case IN:
            return in(pred.term(), pred.asSetPredicate().literalSet());
          case NOT_IN:
            return notIn(pred.term(), pred.asSetPredicate().literalSet());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundSetPredicate: " + pred.op());
        }
      }

      throw new IllegalStateException("Unsupported bound predicate: " + pred.getClass().getName());
    }

    @Override
    public <T> R predicate(UnboundPredicate<T> pred) {
      throw new UnsupportedOperationException("Not a bound predicate: " + pred);
    }
  }

  /**
   * 用 {@link ExpressionVisitor} 后序（postfix）全量遍历表达式树。
   *
   * <p>逻辑：按节点类型分发——Predicate/Aggregate 调对应 predicate/aggregate； TRUE/FALSE/NOT/AND/OR 分别调
   * alwaysTrue/alwaysFalse/not/and/or，且 AND/OR 会递归 先遍历子节点再把子结果传给父回调。
   *
   * @param expr 待遍历表达式
   * @param visitor 访问者
   * @param <R> 返回类型
   * @return 根节点的访问结果
   */
  public static <R> R visit(Expression expr, ExpressionVisitor<R> visitor) {
    if (expr instanceof Predicate) {
      if (expr instanceof BoundPredicate) {
        return visitor.predicate((BoundPredicate<?>) expr);
      } else {
        return visitor.predicate((UnboundPredicate<?>) expr);
      }
    } else if (expr instanceof Aggregate) {
      if (expr instanceof BoundAggregate) {
        return visitor.aggregate((BoundAggregate<?, ?>) expr);
      } else {
        return visitor.aggregate((UnboundAggregate<?>) expr);
      }
    } else {
      switch (expr.op()) {
        case TRUE:
          return visitor.alwaysTrue();
        case FALSE:
          return visitor.alwaysFalse();
        case NOT:
          Not not = (Not) expr;
          return visitor.not(visit(not.child(), visitor));
        case AND:
          And and = (And) expr;
          return visitor.and(visit(and.left(), visitor), visit(and.right(), visitor));
        case OR:
          Or or = (Or) expr;
          return visitor.or(visit(or.left(), visitor), visit(or.right(), visitor));
        default:
          throw new UnsupportedOperationException("Unknown operation: " + expr.op());
      }
    }
  }

  /**
   * 带短路的后序遍历（专用于 Boolean 求值）：仅遍历决定结果所必需的节点。
   *
   * <p>逻辑：与 {@link #visit(Expression, ExpressionVisitor)} 类似，但 AND 左子树为 false 时 直接返回
   * alwaysFalse（短路），OR 左子树为 true 时直接返回 alwaysTrue（短路）， 避免不必要的右子树求值。不支持 Aggregate。
   *
   * @param expr 待遍历表达式
   * @param visitor 访问者
   * @return 根节点的布尔结果
   */
  public static Boolean visitEvaluator(Expression expr, ExpressionVisitor<Boolean> visitor) {
    if (expr instanceof Predicate) {
      if (expr instanceof BoundPredicate) {
        return visitor.predicate((BoundPredicate<?>) expr);
      } else {
        return visitor.predicate((UnboundPredicate<?>) expr);
      }
    } else {
      switch (expr.op()) {
        case TRUE:
          return visitor.alwaysTrue();
        case FALSE:
          return visitor.alwaysFalse();
        case NOT:
          Not not = (Not) expr;
          return visitor.not(visitEvaluator(not.child(), visitor));
        case AND:
          And and = (And) expr;
          Boolean andLeftOperand = visitEvaluator(and.left(), visitor);
          if (!andLeftOperand) {
            return visitor.alwaysFalse();
          }
          return visitor.and(Boolean.TRUE, visitEvaluator(and.right(), visitor));
        case OR:
          Or or = (Or) expr;
          Boolean orLeftOperand = visitEvaluator(or.left(), visitor);
          if (orLeftOperand) {
            return visitor.alwaysTrue();
          }
          return visitor.or(Boolean.FALSE, visitEvaluator(or.right(), visitor));
        default:
          throw new UnsupportedOperationException("Unknown operation: " + expr.op());
      }
    }
  }

  /**
   * 自定义顺序访问者：通过 {@link Supplier} 懒求值子节点结果，由访问者决定遍历顺序。
   *
   * <p>设计意图：某些场景（如残差求值中先评估 strict 投影再评估 inclusive）需要控制子树遍历 顺序与是否遍历，本访问者把子结果包装为 Supplier，访问者按需 get
   * 才触发递归。
   *
   * @param <R> 返回类型
   */
  public abstract static class CustomOrderExpressionVisitor<R> {
    public R alwaysTrue() {
      return null;
    }

    public R alwaysFalse() {
      return null;
    }

    public R not(Supplier<R> result) {
      return null;
    }

    public R and(Supplier<R> leftResult, Supplier<R> rightResult) {
      return null;
    }

    public R or(Supplier<R> leftResult, Supplier<R> rightResult) {
      return null;
    }

    public <T> R predicate(UnboundPredicate<T> pred) {
      throw new UnsupportedOperationException("Not a bound predicate: " + pred);
    }

    public <T> R predicate(BoundPredicate<T> pred) {
      if (pred.isLiteralPredicate()) {
        BoundLiteralPredicate<T> literalPred = pred.asLiteralPredicate();
        switch (pred.op()) {
          case LT:
            return lt(pred.term(), literalPred.literal());
          case LT_EQ:
            return ltEq(pred.term(), literalPred.literal());
          case GT:
            return gt(pred.term(), literalPred.literal());
          case GT_EQ:
            return gtEq(pred.term(), literalPred.literal());
          case EQ:
            return eq(pred.term(), literalPred.literal());
          case NOT_EQ:
            return notEq(pred.term(), literalPred.literal());
          case STARTS_WITH:
            return startsWith(pred.term(), literalPred.literal());
          case NOT_STARTS_WITH:
            return notStartsWith(pred.term(), literalPred.literal());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundLiteralPredicate: " + pred.op());
        }

      } else if (pred.isUnaryPredicate()) {
        switch (pred.op()) {
          case IS_NULL:
            return isNull(pred.term());
          case NOT_NULL:
            return notNull(pred.term());
          case IS_NAN:
            return isNaN(pred.term());
          case NOT_NAN:
            return notNaN(pred.term());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundUnaryPredicate: " + pred.op());
        }

      } else if (pred.isSetPredicate()) {
        switch (pred.op()) {
          case IN:
            return in(pred.term(), pred.asSetPredicate().literalSet());
          case NOT_IN:
            return notIn(pred.term(), pred.asSetPredicate().literalSet());
          default:
            throw new IllegalStateException(
                "Invalid operation for BoundSetPredicate: " + pred.op());
        }
      }

      throw new IllegalStateException("Unsupported bound predicate: " + pred.getClass().getName());
    }

    public <T> R isNull(BoundTerm<T> term) {
      return null;
    }

    public <T> R notNull(BoundTerm<T> term) {
      return null;
    }

    public <T> R isNaN(BoundTerm<T> term) {
      return null;
    }

    public <T> R notNaN(BoundTerm<T> term) {
      return null;
    }

    public <T> R lt(BoundTerm<T> term, Literal<T> lit) {
      return null;
    }

    public <T> R ltEq(BoundTerm<T> term, Literal<T> lit) {
      return null;
    }

    public <T> R gt(BoundTerm<T> term, Literal<T> lit) {
      return null;
    }

    public <T> R gtEq(BoundTerm<T> term, Literal<T> lit) {
      return null;
    }

    public <T> R eq(BoundTerm<T> term, Literal<T> lit) {
      return null;
    }

    public <T> R notEq(BoundTerm<T> term, Literal<T> lit) {
      return null;
    }

    public <T> R in(BoundTerm<T> term, Set<T> literalSet) {
      return null;
    }

    public <T> R notIn(BoundTerm<T> term, Set<T> literalSet) {
      return null;
    }

    public <T> R startsWith(BoundTerm<T> term, Literal<T> lit) {
      return null;
    }

    public <T> R notStartsWith(BoundTerm<T> term, Literal<T> lit) {
      return null;
    }
  }

  /**
   * 用 {@link CustomOrderExpressionVisitor} 遍历，把每个非叶子节点的子结果以 {@link Supplier} 传入，由访问者决定何时触发子树遍历。
   *
   * <p>逻辑：委托 {@link #visitExpr} 构造根节点的 Supplier 并 get 取结果。
   *
   * @param expr 待遍历表达式
   * @param visitor 自定义顺序访问者
   * @param <R> 返回类型
   * @return 根节点的访问结果
   */
  public static <R> R visit(Expression expr, CustomOrderExpressionVisitor<R> visitor) {
    return visitExpr(expr, visitor).get();
  }

  /**
   * 递归构造各节点的懒求值 Supplier（自定义顺序遍历的核心）。
   *
   * <p>逻辑：Predicate 返回调用 visitor.predicate 的 Supplier；TRUE/FALSE/NOT/AND/OR 返回 对应回调的 Supplier，子节点以
   * Supplier 形式传入（不立即求值）。
   */
  private static <R> Supplier<R> visitExpr(
      Expression expr, CustomOrderExpressionVisitor<R> visitor) {
    if (expr instanceof Predicate) {
      if (expr instanceof BoundPredicate) {
        return () -> visitor.predicate((BoundPredicate<?>) expr);
      } else {
        return () -> visitor.predicate((UnboundPredicate<?>) expr);
      }
    } else {
      switch (expr.op()) {
        case TRUE:
          return visitor::alwaysTrue;
        case FALSE:
          return visitor::alwaysFalse;
        case NOT:
          Not not = (Not) expr;
          return () -> visitor.not(visitExpr(not.child(), visitor));
        case AND:
          And and = (And) expr;
          return () -> visitor.and(visitExpr(and.left(), visitor), visitExpr(and.right(), visitor));
        case OR:
          Or or = (Or) expr;
          return () -> visitor.or(visitExpr(or.left(), visitor), visitExpr(or.right(), visitor));
        default:
          throw new UnsupportedOperationException("Unknown operation: " + expr.op());
      }
    }
  }
}
