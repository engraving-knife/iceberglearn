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
package org.apache.iceberg.transforms;

import static org.apache.iceberg.expressions.Expressions.predicate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
import java.util.function.Function;
import org.apache.iceberg.expressions.BoundLiteralPredicate;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundSetPredicate;
import org.apache.iceberg.expressions.BoundTransform;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;

/**
 * 谓词投影工具类：把源字段上的 {@link BoundPredicate} 投影为分区值上的 {@link UnboundPredicate}。
 *
 * <p>所属模块：iceberg-api（被各 Transform 实现的 project/projectStrict 调用）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>针对 truncate 变换，按整数/长整型/Decimal/数组（字符串、二进制）分别做边界调整投影。
 *   <li>处理集合谓词的批量变换（transformSet）。
 *   <li>处理 BoundTransform 谓词的去变换投影（projectTransformPredicate）。
 *   <li>修正 0.10.0 及更早版本对负时间值的错误变换（fixInclusiveTimeProjection/fixStrictTimeProjection）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>truncate 是单调但多对一的变换（如多个相邻整数截断到同一值），inclusive 投影需把开区间调整为闭区间 并对边界做 ±1 调整以保证不漏数据；strict
 *       投影则相反——EQ 无法严格投影返回 null，因为相邻值会落到同一分区。
 *   <li>fixInclusiveTimeProjection/fixStrictTimeProjection 是历史兼容补丁：旧版本对 epoch 之前的负时间值 变换结果比正确值大
 *       1，需在投影时同时考虑正确值与错误值，否则会漏读旧数据。
 * </ul>
 *
 * <p>上下游关系：被 {@link Bucket}、{@link Truncate}、{@link Dates}、{@link Timestamps}、{@link TimeTransform}
 * 的 project/projectStrict 调用；产出 {@link UnboundPredicate} 供扫描规划使用。
 */
class ProjectionUtil {

  private ProjectionUtil() {}

  /**
   * 把整数字面量谓词按截断变换做 inclusive 投影。
   *
   * <p>逻辑：LT 调整为 LT_EQ 并对边界 -1（开转闭）；LT_EQ 直接变换；GT 调整为 GT_EQ 并对边界 +1； GT_EQ 直接变换；EQ 直接变换；其余返回 null。
   *
   * @param name 分区列名
   * @param pred 源字面量谓词
   * @param transform 截断函数
   * @param <T> 变换后类型
   * @return 投影后的谓词；不可投影返回 null
   */
  static <T> UnboundPredicate<T> truncateInteger(
      String name, BoundLiteralPredicate<Integer> pred, Function<Integer, T> transform) {
    int boundary = pred.literal().value();
    switch (pred.op()) {
      case LT:
        // adjust closed and then transform ltEq
        return predicate(Expression.Operation.LT_EQ, name, transform.apply(boundary - 1));
      case LT_EQ:
        return predicate(Expression.Operation.LT_EQ, name, transform.apply(boundary));
      case GT:
        // adjust closed and then transform gtEq
        return predicate(Expression.Operation.GT_EQ, name, transform.apply(boundary + 1));
      case GT_EQ:
        return predicate(Expression.Operation.GT_EQ, name, transform.apply(boundary));
      case EQ:
        return predicate(pred.op(), name, transform.apply(boundary));
      default:
        return null;
    }
  }

  /**
   * 把整数字面量谓词按截断变换做 strict 投影。
   *
   * <p>逻辑：LT/LT_EQ/GT/GT_EQ 保留算子并对边界做相应调整；NOT_EQ 直接变换；EQ 返回 null （相邻整数会截断到同一值，无法保证相等）；其余返回 null。
   *
   * @param name 分区列名
   * @param pred 源字面量谓词
   * @param transform 截断函数
   * @param <T> 变换后类型
   * @return 投影后的谓词；不可投影返回 null
   */
  static <T> UnboundPredicate<T> truncateIntegerStrict(
      String name, BoundLiteralPredicate<Integer> pred, Function<Integer, T> transform) {
    int boundary = pred.literal().value();
    switch (pred.op()) {
      case LT:
        return predicate(Expression.Operation.LT, name, transform.apply(boundary));
      case LT_EQ:
        return predicate(Expression.Operation.LT, name, transform.apply(boundary + 1));
      case GT:
        return predicate(Expression.Operation.GT, name, transform.apply(boundary));
      case GT_EQ:
        return predicate(Expression.Operation.GT, name, transform.apply(boundary - 1));
      case NOT_EQ:
        return predicate(Expression.Operation.NOT_EQ, name, transform.apply(boundary));
      case EQ:
        // there is no predicate that guarantees equality because adjacent ints transform to the
        // same value
        return null;
      default:
        return null;
    }
  }

  /**
   * 把长整型字面量谓词按截断变换做 strict 投影。
   *
   * <p>逻辑：与 {@link #truncateIntegerStrict} 同，但边界为 long。
   *
   * @param name 分区列名
   * @param pred 源字面量谓词
   * @param transform 截断函数
   * @param <T> 变换后类型
   * @return 投影后的谓词；不可投影返回 null
   */
  static <T> UnboundPredicate<T> truncateLongStrict(
      String name, BoundLiteralPredicate<Long> pred, Function<Long, T> transform) {
    long boundary = pred.literal().value();
    switch (pred.op()) {
      case LT:
        return predicate(Expression.Operation.LT, name, transform.apply(boundary));
      case LT_EQ:
        return predicate(Expression.Operation.LT, name, transform.apply(boundary + 1L));
      case GT:
        return predicate(Expression.Operation.GT, name, transform.apply(boundary));
      case GT_EQ:
        return predicate(Expression.Operation.GT, name, transform.apply(boundary - 1L));
      case NOT_EQ:
        return predicate(Expression.Operation.NOT_EQ, name, transform.apply(boundary));
      case EQ:
        // there is no predicate that guarantees equality because adjacent longs transform to the
        // same value
        return null;
      default:
        return null;
    }
  }

  /**
   * 把长整型字面量谓词按截断变换做 inclusive 投影。
   *
   * <p>逻辑：与 {@link #truncateInteger} 同，但边界为 long。
   *
   * @param name 分区列名
   * @param pred 源字面量谓词
   * @param transform 截断函数
   * @param <T> 变换后类型
   * @return 投影后的谓词；不可投影返回 null
   */
  static <T> UnboundPredicate<T> truncateLong(
      String name, BoundLiteralPredicate<Long> pred, Function<Long, T> transform) {
    long boundary = pred.literal().value();
    switch (pred.op()) {
      case LT:
        // adjust closed and then transform ltEq
        return predicate(Expression.Operation.LT_EQ, name, transform.apply(boundary - 1L));
      case LT_EQ:
        return predicate(Expression.Operation.LT_EQ, name, transform.apply(boundary));
      case GT:
        // adjust closed and then transform gtEq
        return predicate(Expression.Operation.GT_EQ, name, transform.apply(boundary + 1L));
      case GT_EQ:
        return predicate(Expression.Operation.GT_EQ, name, transform.apply(boundary));
      case EQ:
        return predicate(pred.op(), name, transform.apply(boundary));
      default:
        return null;
    }
  }

  /**
   * 把 Decimal 字面量谓词按截断变换做 inclusive 投影。
   *
   * <p>逻辑：与整数版同，但 ±1 在 unscaledValue 层面操作（保持 scale 不变）。
   *
   * @param name 分区列名
   * @param pred 源字面量谓词
   * @param transform 截断函数
   * @param <T> 变换后类型
   * @return 投影后的谓词；不可投影返回 null
   */
  static <T> UnboundPredicate<T> truncateDecimal(
      String name, BoundLiteralPredicate<BigDecimal> pred, Function<BigDecimal, T> transform) {
    BigDecimal boundary = pred.literal().value();
    switch (pred.op()) {
      case LT:
        // adjust closed and then transform ltEq
        BigDecimal minusOne =
            new BigDecimal(boundary.unscaledValue().subtract(BigInteger.ONE), boundary.scale());
        return predicate(Expression.Operation.LT_EQ, name, transform.apply(minusOne));
      case LT_EQ:
        return predicate(Expression.Operation.LT_EQ, name, transform.apply(boundary));
      case GT:
        // adjust closed and then transform gtEq
        BigDecimal plusOne =
            new BigDecimal(boundary.unscaledValue().add(BigInteger.ONE), boundary.scale());
        return predicate(Expression.Operation.GT_EQ, name, transform.apply(plusOne));
      case GT_EQ:
        return predicate(Expression.Operation.GT_EQ, name, transform.apply(boundary));
      case EQ:
        return predicate(pred.op(), name, transform.apply(boundary));
      default:
        return null;
    }
  }

  /**
   * 把 Decimal 字面量谓词按截断变换做 strict 投影。
   *
   * <p>逻辑：与 {@link #truncateIntegerStrict} 同，但 ±1 在 unscaledValue 层面操作。
   *
   * @param name 分区列名
   * @param pred 源字面量谓词
   * @param transform 截断函数
   * @param <T> 变换后类型
   * @return 投影后的谓词；不可投影返回 null
   */
  static <T> UnboundPredicate<T> truncateDecimalStrict(
      String name, BoundLiteralPredicate<BigDecimal> pred, Function<BigDecimal, T> transform) {
    BigDecimal boundary = pred.literal().value();

    BigDecimal minusOne =
        new BigDecimal(boundary.unscaledValue().subtract(BigInteger.ONE), boundary.scale());

    BigDecimal plusOne =
        new BigDecimal(boundary.unscaledValue().add(BigInteger.ONE), boundary.scale());

    switch (pred.op()) {
      case LT:
        return predicate(Expression.Operation.LT, name, transform.apply(boundary));
      case LT_EQ:
        return predicate(Expression.Operation.LT, name, transform.apply(plusOne));
      case GT:
        return predicate(Expression.Operation.GT, name, transform.apply(boundary));
      case GT_EQ:
        return predicate(Expression.Operation.GT, name, transform.apply(minusOne));
      case NOT_EQ:
        return predicate(Expression.Operation.NOT_EQ, name, transform.apply(boundary));
      case EQ:
        // there is no predicate that guarantees equality because adjacent decimals transform to the
        // same value
        return null;
      default:
        return null;
    }
  }

  /**
   * 把数组型（字符串/二进制）字面量谓词按截断变换做 inclusive 投影。
   *
   * <p>逻辑：LT/LT_EQ 合并为 LT_EQ；GT/GT_EQ 合并为 GT_EQ；EQ/STARTS_WITH 直接变换；其余返回 null。
   *
   * @param name 分区列名
   * @param pred 源字面量谓词
   * @param transform 截断函数
   * @param <S> 源类型
   * @param <T> 变换后类型
   * @return 投影后的谓词；不可投影返回 null
   */
  static <S, T> UnboundPredicate<T> truncateArray(
      String name, BoundLiteralPredicate<S> pred, Function<S, T> transform) {
    S boundary = pred.literal().value();
    switch (pred.op()) {
      case LT:
      case LT_EQ:
        return predicate(Expression.Operation.LT_EQ, name, transform.apply(boundary));
      case GT:
      case GT_EQ:
        return predicate(Expression.Operation.GT_EQ, name, transform.apply(boundary));
      case EQ:
        return predicate(Expression.Operation.EQ, name, transform.apply(boundary));
      case STARTS_WITH:
        return predicate(Expression.Operation.STARTS_WITH, name, transform.apply(boundary));
        //        case IN: // TODO
        //          return Expressions.predicate(Operation.IN, name, transform.apply(boundary));
      default:
        return null;
    }
  }

  /**
   * 把数组型字面量谓词按截断变换做 strict 投影。
   *
   * <p>逻辑：LT/LT_EQ→LT；GT/GT_EQ→GT；NOT_EQ 直接变换；EQ 返回 null（相邻值会截断到同一分区）；其余返回 null。
   *
   * @param name 分区列名
   * @param pred 源字面量谓词
   * @param transform 截断函数
   * @param <S> 源类型
   * @param <T> 变换后类型
   * @return 投影后的谓词；不可投影返回 null
   */
  static <S, T> UnboundPredicate<T> truncateArrayStrict(
      String name, BoundLiteralPredicate<S> pred, Function<S, T> transform) {
    S boundary = pred.literal().value();
    switch (pred.op()) {
      case LT:
      case LT_EQ:
        return predicate(Expression.Operation.LT, name, transform.apply(boundary));
      case GT:
      case GT_EQ:
        return predicate(Expression.Operation.GT, name, transform.apply(boundary));
      case NOT_EQ:
        return predicate(Expression.Operation.NOT_EQ, name, transform.apply(boundary));
      case EQ:
        // there is no predicate that guarantees equality because adjacent values transform to the
        // same partition
        return null;
      default:
        return null;
    }
  }

  /**
   * 若谓词作用于与本变换匹配的 BoundTransform，则去掉变换层返回分区值上的谓词。
   *
   * <p>逻辑：检查 pred.term() 是否为 BoundTransform 且其 transform 的 toString 与本变换一致； 若一致则调用 removeTransform
   * 把谓词项替换为分区列名，保留算子与字面量/集合。
   *
   * @param transform 本变换
   * @param partitionName 分区列名
   * @param pred 待投影谓词
   * @param <T> 变换后类型
   * @return 去变换后的谓词；不匹配返回 null
   */
  @SuppressWarnings("unchecked")
  static <T> UnboundPredicate<T> projectTransformPredicate(
      Transform<?, T> transform, String partitionName, BoundPredicate<?> pred) {
    if (pred.term() instanceof BoundTransform
        && transform
            .toString()
            .equals(((BoundTransform<?, ?>) pred.term()).transform().toString())) {
      // the bound value must be a T because the transform matches
      return (UnboundPredicate<T>) removeTransform(partitionName, pred);
    }
    return null;
  }

  /**
   * 把谓词项中的 BoundTransform 替换为分区列名，保留算子与字面量/集合。
   *
   * @param partitionName 分区列名
   * @param pred 原谓词
   * @param <T> 谓词值类型
   * @return 替换项后的谓词
   */
  private static <T> UnboundPredicate<T> removeTransform(
      String partitionName, BoundPredicate<T> pred) {
    if (pred.isUnaryPredicate()) {
      return Expressions.predicate(pred.op(), partitionName);
    } else if (pred.isLiteralPredicate()) {
      return Expressions.predicate(pred.op(), partitionName, pred.asLiteralPredicate().literal());
    } else if (pred.isSetPredicate()) {
      return Expressions.predicate(pred.op(), partitionName, pred.asSetPredicate().literalSet());
    }
    throw new UnsupportedOperationException(
        "Cannot replace transform in unknown predicate: " + pred);
  }

  /**
   * 把集合谓词中的每个字面量按变换函数转换，返回新的集合谓词。
   *
   * @param fieldName 分区列名
   * @param predicate 源集合谓词
   * @param transform 变换函数
   * @param <S> 源类型
   * @param <T> 变换后类型
   * @return 变换后的集合谓词
   */
  static <S, T> UnboundPredicate<T> transformSet(
      String fieldName, BoundSetPredicate<S> predicate, Function<S, T> transform) {
    return predicate(
        predicate.op(),
        fieldName,
        Iterables.transform(predicate.asSetPredicate().literalSet(), transform::apply));
  }

  /**
   * 修正 inclusive 时间投影，兼容 0.10.0 及更早版本对负时间值的错误变换。
   *
   * <p>逻辑：旧版本对 epoch 之前的负时间值变换结果比正确值大 1（如 day(1969-12-31 10:00:00) 得 0 而非 -1）。 本方法对负值边界做 +1
   * 调整：LT/LT_EQ 放宽边界；EQ 改为 IN（同时匹配正确值与错误值）； IN 把负值同时加入其 +1；GT/GT_EQ 不变（错误值已大于边界）；NOT_EQ/NOT_IN 无
   * inclusive 投影返回 null。
   *
   * @param projected 已投影的谓词
   * @return 修正后的谓词
   */
  static UnboundPredicate<Integer> fixInclusiveTimeProjection(UnboundPredicate<Integer> projected) {
    if (projected == null) {
      return projected;
    }

    // adjust the predicate for values that were 1 larger than the correct transformed value
    switch (projected.op()) {
      case LT:
        if (projected.literal().value() < 0) {
          return Expressions.lessThan(projected.term(), projected.literal().value() + 1);
        }

        return projected;

      case LT_EQ:
        if (projected.literal().value() < 0) {
          return Expressions.lessThanOrEqual(projected.term(), projected.literal().value() + 1);
        }

        return projected;

      case GT:
      case GT_EQ:
        // incorrect projected values are already greater than the bound for GT, GT_EQ
        return projected;

      case EQ:
        if (projected.literal().value() < 0) {
          // match either the incorrect value (projectedValue + 1) or the correct value
          // (projectedValue)
          return Expressions.in(
              projected.term(), projected.literal().value(), projected.literal().value() + 1);
        }

        return projected;

      case IN:
        Set<Integer> fixedSet = Sets.newHashSet();
        boolean hasNegativeValue = false;
        for (Literal<Integer> lit : projected.literals()) {
          Integer value = lit.value();
          fixedSet.add(value);
          if (value < 0) {
            hasNegativeValue = true;
            fixedSet.add(value + 1);
          }
        }

        if (hasNegativeValue) {
          return Expressions.in(projected.term(), fixedSet);
        }

        return projected;

      case NOT_IN:
      case NOT_EQ:
        // there is no inclusive projection for NOT_EQ and NOT_IN
        return null;

      default:
        return projected;
    }
  }

  /**
   * 修正 strict 时间投影，兼容 0.10.0 及更早版本对负时间值的错误变换。
   *
   * <p>逻辑：LT/LT_EQ 不变（正确边界对错误值也成立）；GT/GT_EQ 对 ≤0 的边界做 +1 收紧 （错误值可能落入满足投影的分区，需用更严格的值）；EQ/IN 无 strict
   * 投影返回 null； NOT_EQ 改为 NOT_IN（同时排除正确值与错误值）；NOT_IN 把负值同时加入其 +1。
   *
   * @param projected 已投影的谓词
   * @return 修正后的谓词
   */
  static UnboundPredicate<Integer> fixStrictTimeProjection(UnboundPredicate<Integer> projected) {
    if (projected == null) {
      return null;
    }

    switch (projected.op()) {
      case LT:
      case LT_EQ:
        // the correct bound is a correct strict projection for the incorrectly transformed values.
        return projected;

      case GT:
        // GT and GT_EQ need to be adjusted because values that do not match the predicate may have
        // been transformed
        // into partition values that match the projected predicate. For example, >=
        // month(1969-11-31) is > -2, but
        // 1969-10-31 was previously transformed to month -2 instead of -3. This must use the more
        // strict value.
        if (projected.literal().value() <= 0) {
          return Expressions.greaterThan(projected.term(), projected.literal().value() + 1);
        }

        return projected;

      case GT_EQ:
        if (projected.literal().value() <= 0) {
          return Expressions.greaterThanOrEqual(projected.term(), projected.literal().value() + 1);
        }

        return projected;

      case EQ:
      case IN:
        // there is no strict projection for EQ and IN
        return null;

      case NOT_EQ:
        if (projected.literal().value() < 0) {
          return Expressions.notIn(
              projected.term(), projected.literal().value(), projected.literal().value() + 1);
        }

        return projected;

      case NOT_IN:
        Set<Integer> fixedSet = Sets.newHashSet();
        boolean hasNegativeValue = false;
        for (Literal<Integer> lit : projected.literals()) {
          Integer value = lit.value();
          fixedSet.add(value);
          if (value < 0) {
            hasNegativeValue = true;
            fixedSet.add(value + 1);
          }
        }

        if (hasNegativeValue) {
          return Expressions.notIn(projected.term(), fixedSet);
        }

        return projected;

      default:
        return null;
    }
  }
}
