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

import java.util.stream.Stream;
import org.apache.iceberg.expressions.Expression.Operation;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.Transforms;

/**
 * 表达式工厂：集中提供构造各类 {@link Expression}（谓词、连接词、变换项、聚合）的静态方法。
 *
 * <p>所属模块：iceberg-api（表达式体系对外的统一构造入口，引擎集成与 core 模块均通过本类 构造过滤/聚合表达式）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>构造布尔连接词 {@code and/or/not}，并在构造时做 alwaysTrue/alwaysFalse 短路化简。
 *   <li>构造变换项 {@code bucket/year/month/day/hour/truncate}，把列名+变换封装为 {@link UnboundTransform}。
 *   <li>构造未绑定谓词 {@code isNull/notNull/isNaN/notNaN/lessThan/.../in/notIn/startsWith}。
 *   <li>构造聚合 {@code count/countStar/max/min}，以及 {@code alwaysTrue/alwaysFalse/rewriteNot}。
 * </ul>
 *
 * <p>设计意图：所有工厂方法返回未绑定表达式（按列名），由调用方在具体 schema 上绑定； 连接词在构造期即做常量折叠（如 {@code and(true, x) =>
 * x}），减少后续求值开销并保证规范化。 谓词方法同时提供“列名”与“UnboundTerm”两种重载，便于直接引用列或对变换结果做判定。
 *
 * <p>上下游关系：被 Spark/Flink 等引擎集成、core 模块的扫描/过滤流程大量调用； 产物经 {@link Binder} 绑定后交 {@link
 * Evaluator}、{@link Projections} 等使用。
 */
public class Expressions {
  private Expressions() {}

  /**
   * 构造合取（AND），并在构造期做常量折叠。
   *
   * <p>逻辑：任一为 alwaysFalse 则返回 alwaysFalse；左为 alwaysTrue 返回右、右为 alwaysTrue 返回左；否则构造 {@link And}。
   *
   * @param left 左表达式
   * @param right 右表达式
   * @return 折叠后的表达式
   */
  public static Expression and(Expression left, Expression right) {
    Preconditions.checkNotNull(left, "Left expression cannot be null.");
    Preconditions.checkNotNull(right, "Right expression cannot be null.");
    if (left == alwaysFalse() || right == alwaysFalse()) {
      return alwaysFalse();
    } else if (left == alwaysTrue()) {
      return right;
    } else if (right == alwaysTrue()) {
      return left;
    }
    return new And(left, right);
  }

  /**
   * 多参数合取：把前两个的 AND 结果与后续变长参数依次折叠。
   *
   * @param left 左表达式
   * @param right 右表达式
   * @param expressions 其余表达式
   * @return 折叠后的表达式
   */
  public static Expression and(Expression left, Expression right, Expression... expressions) {
    return Stream.of(expressions).reduce(and(left, right), Expressions::and);
  }

  /**
   * 构造析取（OR），并在构造期做常量折叠。
   *
   * <p>逻辑：任一为 alwaysTrue 则返回 alwaysTrue；左为 alwaysFalse 返回右、右为 alwaysFalse 返回左；否则构造 {@link Or}。
   *
   * @param left 左表达式
   * @param right 右表达式
   * @return 折叠后的表达式
   */
  public static Expression or(Expression left, Expression right) {
    Preconditions.checkNotNull(left, "Left expression cannot be null.");
    Preconditions.checkNotNull(right, "Right expression cannot be null.");
    if (left == alwaysTrue() || right == alwaysTrue()) {
      return alwaysTrue();
    } else if (left == alwaysFalse()) {
      return right;
    } else if (right == alwaysFalse()) {
      return left;
    }
    return new Or(left, right);
  }

  /**
   * 构造否定（NOT），并在构造期做化简。
   *
   * <p>逻辑：alwaysTrue 取反为 alwaysFalse、反之亦然；对 {@link Not} 再取反则还原为其子表达式 （双重否定消除）；否则构造 {@link Not}。
   *
   * @param child 子表达式
   * @return 化简后的表达式
   */
  public static Expression not(Expression child) {
    Preconditions.checkNotNull(child, "Child expression cannot be null.");
    if (child == alwaysTrue()) {
      return alwaysFalse();
    } else if (child == alwaysFalse()) {
      return alwaysTrue();
    } else if (child instanceof Not) {
      return ((Not) child).child();
    }
    return new Not(child);
  }

  /**
   * 构造 bucket 变换项（未绑定）。
   *
   * @param name 列名
   * @param numBuckets 桶数
   * @param <T> 变换结果类型
   * @return 未绑定变换项
   */
  @SuppressWarnings("unchecked")
  public static <T> UnboundTerm<T> bucket(String name, int numBuckets) {
    Transform<?, T> transform = (Transform<?, T>) Transforms.bucket(numBuckets);
    return new UnboundTransform<>(ref(name), transform);
  }

  /** 构造 year 变换项（未绑定）。 */
  @SuppressWarnings("unchecked")
  public static <T> UnboundTerm<T> year(String name) {
    return new UnboundTransform<>(ref(name), (Transform<?, T>) Transforms.year());
  }

  /** 构造 month 变换项（未绑定）。 */
  @SuppressWarnings("unchecked")
  public static <T> UnboundTerm<T> month(String name) {
    return new UnboundTransform<>(ref(name), (Transform<?, T>) Transforms.month());
  }

  /** 构造 day 变换项（未绑定）。 */
  @SuppressWarnings("unchecked")
  public static <T> UnboundTerm<T> day(String name) {
    return new UnboundTransform<>(ref(name), (Transform<?, T>) Transforms.day());
  }

  /** 构造 hour 变换项（未绑定）。 */
  @SuppressWarnings("unchecked")
  public static <T> UnboundTerm<T> hour(String name) {
    return new UnboundTransform<>(ref(name), (Transform<?, T>) Transforms.hour());
  }

  /** 构造 truncate 变换项（未绑定）。 */
  public static <T> UnboundTerm<T> truncate(String name, int width) {
    return new UnboundTransform<>(ref(name), Transforms.truncate(width));
  }

  /** 构造 IS_NULL 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> isNull(String name) {
    return new UnboundPredicate<>(Expression.Operation.IS_NULL, ref(name));
  }

  /** 构造 IS_NULL 谓词（按项）。 */
  public static <T> UnboundPredicate<T> isNull(UnboundTerm<T> expr) {
    return new UnboundPredicate<>(Expression.Operation.IS_NULL, expr);
  }

  /** 构造 NOT_NULL 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> notNull(String name) {
    return new UnboundPredicate<>(Expression.Operation.NOT_NULL, ref(name));
  }

  /** 构造 NOT_NULL 谓词（按项）。 */
  public static <T> UnboundPredicate<T> notNull(UnboundTerm<T> expr) {
    return new UnboundPredicate<>(Expression.Operation.NOT_NULL, expr);
  }

  /** 构造 IS_NAN 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> isNaN(String name) {
    return new UnboundPredicate<>(Expression.Operation.IS_NAN, ref(name));
  }

  /** 构造 IS_NAN 谓词（按项）。 */
  public static <T> UnboundPredicate<T> isNaN(UnboundTerm<T> expr) {
    return new UnboundPredicate<>(Expression.Operation.IS_NAN, expr);
  }

  /** 构造 NOT_NAN 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> notNaN(String name) {
    return new UnboundPredicate<>(Expression.Operation.NOT_NAN, ref(name));
  }

  /** 构造 NOT_NAN 谓词（按项）。 */
  public static <T> UnboundPredicate<T> notNaN(UnboundTerm<T> expr) {
    return new UnboundPredicate<>(Expression.Operation.NOT_NAN, expr);
  }

  /** 构造 LT 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> lessThan(String name, T value) {
    return new UnboundPredicate<>(Expression.Operation.LT, ref(name), value);
  }

  /** 构造 LT 谓词（按项）。 */
  public static <T> UnboundPredicate<T> lessThan(UnboundTerm<T> expr, T value) {
    return new UnboundPredicate<>(Expression.Operation.LT, expr, value);
  }

  /** 构造 LT_EQ 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> lessThanOrEqual(String name, T value) {
    return new UnboundPredicate<>(Expression.Operation.LT_EQ, ref(name), value);
  }

  /** 构造 LT_EQ 谓词（按项）。 */
  public static <T> UnboundPredicate<T> lessThanOrEqual(UnboundTerm<T> expr, T value) {
    return new UnboundPredicate<>(Expression.Operation.LT_EQ, expr, value);
  }

  /** 构造 GT 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> greaterThan(String name, T value) {
    return new UnboundPredicate<>(Expression.Operation.GT, ref(name), value);
  }

  /** 构造 GT 谓词（按项）。 */
  public static <T> UnboundPredicate<T> greaterThan(UnboundTerm<T> expr, T value) {
    return new UnboundPredicate<>(Expression.Operation.GT, expr, value);
  }

  /** 构造 GT_EQ 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> greaterThanOrEqual(String name, T value) {
    return new UnboundPredicate<>(Expression.Operation.GT_EQ, ref(name), value);
  }

  /** 构造 GT_EQ 谓词（按项）。 */
  public static <T> UnboundPredicate<T> greaterThanOrEqual(UnboundTerm<T> expr, T value) {
    return new UnboundPredicate<>(Expression.Operation.GT_EQ, expr, value);
  }

  /** 构造 EQ 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> equal(String name, T value) {
    return new UnboundPredicate<>(Expression.Operation.EQ, ref(name), value);
  }

  /** 构造 EQ 谓词（按项）。 */
  public static <T> UnboundPredicate<T> equal(UnboundTerm<T> expr, T value) {
    return new UnboundPredicate<>(Expression.Operation.EQ, expr, value);
  }

  /** 构造 NOT_EQ 谓词（按列名）。 */
  public static <T> UnboundPredicate<T> notEqual(String name, T value) {
    return new UnboundPredicate<>(Expression.Operation.NOT_EQ, ref(name), value);
  }

  /** 构造 NOT_EQ 谓词（按项）。 */
  public static <T> UnboundPredicate<T> notEqual(UnboundTerm<T> expr, T value) {
    return new UnboundPredicate<>(Expression.Operation.NOT_EQ, expr, value);
  }

  /** 构造 STARTS_WITH 谓词（按列名）。 */
  public static UnboundPredicate<String> startsWith(String name, String value) {
    return new UnboundPredicate<>(Expression.Operation.STARTS_WITH, ref(name), value);
  }

  /** 构造 STARTS_WITH 谓词（按项）。 */
  public static UnboundPredicate<String> startsWith(UnboundTerm<String> expr, String value) {
    return new UnboundPredicate<>(Expression.Operation.STARTS_WITH, expr, value);
  }

  /** 构造 NOT_STARTS_WITH 谓词（按列名）。 */
  public static UnboundPredicate<String> notStartsWith(String name, String value) {
    return new UnboundPredicate<>(Expression.Operation.NOT_STARTS_WITH, ref(name), value);
  }

  /** 构造 NOT_STARTS_WITH 谓词（按项）。 */
  public static UnboundPredicate<String> notStartsWith(UnboundTerm<String> expr, String value) {
    return new UnboundPredicate<>(Expression.Operation.NOT_STARTS_WITH, expr, value);
  }

  /** 构造 IN 谓词（按列名，变长值）。 */
  public static <T> UnboundPredicate<T> in(String name, T... values) {
    return predicate(Operation.IN, name, Lists.newArrayList(values));
  }

  /** 构造 IN 谓词（按项，变长值）。 */
  public static <T> UnboundPredicate<T> in(UnboundTerm<T> expr, T... values) {
    return predicate(Operation.IN, expr, Lists.newArrayList(values));
  }

  /** 构造 IN 谓词（按列名，集合值）。 */
  public static <T> UnboundPredicate<T> in(String name, Iterable<T> values) {
    Preconditions.checkNotNull(values, "Values cannot be null for IN predicate.");
    return predicate(Operation.IN, ref(name), values);
  }

  /** 构造 IN 谓词（按项，集合值）。 */
  public static <T> UnboundPredicate<T> in(UnboundTerm<T> expr, Iterable<T> values) {
    Preconditions.checkNotNull(values, "Values cannot be null for IN predicate.");
    return predicate(Operation.IN, expr, values);
  }

  /** 构造 NOT_IN 谓词（按列名，变长值）。 */
  public static <T> UnboundPredicate<T> notIn(String name, T... values) {
    return predicate(Operation.NOT_IN, name, Lists.newArrayList(values));
  }

  /** 构造 NOT_IN 谓词（按项，变长值）。 */
  public static <T> UnboundPredicate<T> notIn(UnboundTerm<T> expr, T... values) {
    return predicate(Operation.NOT_IN, expr, Lists.newArrayList(values));
  }

  /** 构造 NOT_IN 谓词（按列名，集合值）。 */
  public static <T> UnboundPredicate<T> notIn(String name, Iterable<T> values) {
    Preconditions.checkNotNull(values, "Values cannot be null for NOT_IN predicate.");
    return predicate(Operation.NOT_IN, name, values);
  }

  /** 构造 NOT_IN 谓词（按项，集合值）。 */
  public static <T> UnboundPredicate<T> notIn(UnboundTerm<T> expr, Iterable<T> values) {
    Preconditions.checkNotNull(values, "Values cannot be null for NOT_IN predicate.");
    return predicate(Operation.NOT_IN, expr, values);
  }

  /** 构造带值谓词（按列名与值），内部转为字面量。 */
  public static <T> UnboundPredicate<T> predicate(Operation op, String name, T value) {
    return predicate(op, name, Literals.from(value));
  }

  /**
   * 构造带值谓词（按列名与字面量）。
   *
   * <p>逻辑：校验操作不是一元（IS_NULL/NOT_NULL/IS_NAN/NOT_NAN），否则报错； 然后以列名构造引用并生成 {@link UnboundPredicate}。
   *
   * @param op 操作类型（必须为带值操作）
   * @param name 列名
   * @param lit 字面量
   * @return 未绑定谓词
   */
  public static <T> UnboundPredicate<T> predicate(Operation op, String name, Literal<T> lit) {
    Preconditions.checkArgument(
        op != Operation.IS_NULL
            && op != Operation.NOT_NULL
            && op != Operation.IS_NAN
            && op != Operation.NOT_NAN,
        "Cannot create %s predicate inclusive a value",
        op);
    return new UnboundPredicate<T>(op, ref(name), lit);
  }

  /** 构造集合谓词（按列名与值集合）。 */
  public static <T> UnboundPredicate<T> predicate(Operation op, String name, Iterable<T> values) {
    return predicate(op, ref(name), values);
  }

  /**
   * 构造一元谓词（按列名，无值）。
   *
   * <p>逻辑：校验操作为一元（IS_NULL/NOT_NULL/IS_NAN/NOT_NAN），否则报错； 然后以列名构造引用并生成 {@link UnboundPredicate}。
   *
   * @param op 操作类型（必须为一元操作）
   * @param name 列名
   * @return 未绑定一元谓词
   */
  public static <T> UnboundPredicate<T> predicate(Operation op, String name) {
    Preconditions.checkArgument(
        op == Operation.IS_NULL
            || op == Operation.NOT_NULL
            || op == Operation.IS_NAN
            || op == Operation.NOT_NAN,
        "Cannot create %s predicate without a value",
        op);
    return new UnboundPredicate<>(op, ref(name));
  }

  /** 构造集合谓词（按项与值集合）。 */
  public static <T> UnboundPredicate<T> predicate(
      Operation op, UnboundTerm<T> expr, Iterable<T> values) {
    return new UnboundPredicate<>(op, expr, values);
  }

  /** 构造一元谓词（按项，无值）。 */
  public static <T> UnboundPredicate<T> predicate(Operation op, UnboundTerm<T> expr) {
    return new UnboundPredicate<>(op, expr);
  }

  /** 返回恒真表达式单例 {@link True}。 */
  public static True alwaysTrue() {
    return True.INSTANCE;
  }

  /** 返回恒假表达式单例 {@link False}。 */
  public static False alwaysFalse() {
    return False.INSTANCE;
  }

  /**
   * 重写表达式，把 NOT 节点下沉到叶子（德摩根展开），返回等价的无顶层 NOT 表达式。
   *
   * <p>设计要点：投影/残差等流程假设表达式树无 NOT 节点，故需先经此重写。
   *
   * @param expr 原表达式
   * @return NOT 下沉后的等价表达式
   */
  public static Expression rewriteNot(Expression expr) {
    return ExpressionVisitors.visit(expr, RewriteNot.get());
  }

  /**
   * 构造列名引用。
   *
   * <p>以下两种写法等价：{@code equal("a", 5)} 与 {@code equal(ref("a"), 5)}。
   *
   * @param name 列名
   * @param <T> 引用类型
   * @return 命名引用
   */
  public static <T> NamedReference<T> ref(String name) {
    return new NamedReference<>(name);
  }

  /**
   * 构造变换项（按列名与自定义变换）。
   *
   * @param name 列名
   * @param transform 变换函数
   * @param <T> 变换结果类型
   * @return 未绑定变换项
   */
  public static <T> UnboundTerm<T> transform(String name, Transform<?, T> transform) {
    return new UnboundTransform<>(ref(name), transform);
  }

  /** 构造 COUNT(非空) 聚合（按列名）。 */
  public static <T> UnboundAggregate<T> count(String name) {
    return new UnboundAggregate<>(Operation.COUNT, ref(name));
  }

  /** 构造 COUNT(*) 聚合。 */
  public static <T> UnboundAggregate<T> countStar() {
    return new UnboundAggregate<>(Operation.COUNT_STAR, null);
  }

  /** 构造 MAX 聚合（按列名）。 */
  public static <T> UnboundAggregate<T> max(String name) {
    return new UnboundAggregate<>(Operation.MAX, ref(name));
  }

  /** 构造 MIN 聚合（按列名）。 */
  public static <T> UnboundAggregate<T> min(String name) {
    return new UnboundAggregate<>(Operation.MIN, ref(name));
  }
}
