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
package org.apache.iceberg.orc;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.expressions.Bound;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.BoundReference;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.ExpressionVisitors;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Type.TypeID;
import org.apache.orc.TypeDescription;
import org.apache.orc.storage.common.type.HiveDecimal;
import org.apache.orc.storage.ql.io.sarg.PredicateLeaf;
import org.apache.orc.storage.ql.io.sarg.SearchArgument;
import org.apache.orc.storage.ql.io.sarg.SearchArgument.TruthValue;
import org.apache.orc.storage.ql.io.sarg.SearchArgumentFactory;
import org.apache.orc.storage.serde2.io.HiveDecimalWritable;

/**
 * 将 Iceberg {@link Expression} 转换为 ORC {@link SearchArgument} 的访问器。
 *
 * <p>所属模块：iceberg-orc。用于把 Iceberg 的过滤谓词下推到 ORC Reader 层， 让 ORC 在读取 stripe 时跳过不满足条件的行组，减少 IO。
 *
 * <p>职责：遍历已绑定（Bound）的 Iceberg 表达式树，按谓词类型生成对应的 ORC SearchArgument 构建调用（以 Action 延迟执行的方式组织），最终
 * builder.build() 生成 SearchArgument。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>使用 Action（函数式接口）延迟执行：表达式树的访问顺序与 builder 的 and/or/not 嵌套顺序需要严格匹配，Action 让先访问的子树在父节点 invoke
 *       时才真正写入 builder。
 *   <li>语义适配：ORC SearchArgument 使用 SQL 三值逻辑（NULL 传播），而 Iceberg 表达式 对 NULL 有不同处理（如 notEq 保留 NULL
 *       行），因此在 notEq/notIn 中额外补 IS NULL 分支。
 *   <li>不支持类型的谓词返回 YES_NO_NULL，表示该谓词不参与过滤（安全降级）。
 * </ul>
 *
 * <p>上下游关系：被 {@link ORC} 读取入口调用；依赖 {@link ORCSchemaUtil} 做字段 id→列名映射。
 */
class ExpressionToSearchArgument
    extends ExpressionVisitors.BoundVisitor<ExpressionToSearchArgument.Action> {

  /**
   * 将 Iceberg 表达式转为 ORC SearchArgument。
   *
   * <p>逻辑：先把 readSchema 转为 Iceberg schema 再生成 id→ORC 列名映射； 创建 builder 后访问表达式树并 invoke 所有 Action，最后
   * build 返回。
   *
   * @param expr Iceberg 已绑定表达式
   * @param readSchema ORC 读取 schema
   * @return 对应的 ORC SearchArgument
   */
  static SearchArgument convert(Expression expr, TypeDescription readSchema) {
    Map<Integer, String> idToColumnName =
        ORCSchemaUtil.idToOrcName(ORCSchemaUtil.convert(readSchema));
    SearchArgument.Builder builder = SearchArgumentFactory.newBuilder();
    ExpressionVisitors.visit(expr, new ExpressionToSearchArgument(builder, idToColumnName))
        .invoke();
    return builder.build();
  }

  // Currently every predicate in ORC requires a PredicateLeaf.Type field which is not available for
  // these Iceberg types
  private static final Set<TypeID> UNSUPPORTED_TYPES =
      ImmutableSet.of(
          TypeID.BINARY, TypeID.FIXED, TypeID.UUID, TypeID.STRUCT, TypeID.MAP, TypeID.LIST);

  private SearchArgument.Builder builder;
  private Map<Integer, String> idToColumnName;

  private ExpressionToSearchArgument(
      SearchArgument.Builder builder, Map<Integer, String> idToColumnName) {
    this.builder = builder;
    this.idToColumnName = idToColumnName;
  }

  @Override
  public Action alwaysTrue() {
    return () -> this.builder.literal(TruthValue.YES);
  }

  @Override
  public Action alwaysFalse() {
    return () -> this.builder.literal(TruthValue.NO);
  }

  @Override
  public Action not(Action child) {
    return () -> {
      this.builder.startNot();
      child.invoke();
      this.builder.end();
    };
  }

  @Override
  public Action and(Action leftChild, Action rightChild) {
    return () -> {
      this.builder.startAnd();
      leftChild.invoke();
      rightChild.invoke();
      this.builder.end();
    };
  }

  @Override
  public Action or(Action leftChild, Action rightChild) {
    return () -> {
      this.builder.startOr();
      leftChild.invoke();
      rightChild.invoke();
      this.builder.end();
    };
  }

  @Override
  public <T> Action isNull(Bound<T> expr) {
    return () ->
        this.builder.isNull(idToColumnName.get(expr.ref().fieldId()), type(expr.ref().type()));
  }

  @Override
  public <T> Action notNull(Bound<T> expr) {
    return () ->
        this.builder
            .startNot()
            .isNull(idToColumnName.get(expr.ref().fieldId()), type(expr.ref().type()))
            .end();
  }

  @Override
  public <T> Action isNaN(Bound<T> expr) {
    return () ->
        this.builder.equals(
            idToColumnName.get(expr.ref().fieldId()),
            type(expr.ref().type()),
            literal(expr.ref().type(), getNaNForType(expr.ref().type())));
  }

  /** 返回指定浮点类型的 NaN 值（ORC 无原生 isNaN 谓词，用 equals(NaN) 模拟）。 */
  private Object getNaNForType(Type type) {
    switch (type.typeId()) {
      case FLOAT:
        return Float.NaN;
      case DOUBLE:
        return Double.NaN;
      default:
        throw new IllegalArgumentException("Cannot get NaN value for type " + type.typeId());
    }
  }

  @Override
  public <T> Action notNaN(Bound<T> expr) {
    return () -> {
      this.builder.startOr();
      isNull(expr).invoke();
      this.builder.startNot();
      isNaN(expr).invoke();
      this.builder.end(); // end NOT
      this.builder.end(); // end OR
    };
  }

  @Override
  public <T> Action lt(Bound<T> expr, Literal<T> lit) {
    return () ->
        this.builder.lessThan(
            idToColumnName.get(expr.ref().fieldId()),
            type(expr.ref().type()),
            literal(expr.ref().type(), lit.value()));
  }

  @Override
  public <T> Action ltEq(Bound<T> expr, Literal<T> lit) {
    return () ->
        this.builder.lessThanEquals(
            idToColumnName.get(expr.ref().fieldId()),
            type(expr.ref().type()),
            literal(expr.ref().type(), lit.value()));
  }

  @Override
  public <T> Action gt(Bound<T> expr, Literal<T> lit) {
    // ORC SearchArguments do not have a greaterThan predicate, so we use not(lessThanOrEquals)
    // e.g. x > 5 => not(x <= 5)
    return () ->
        this.builder
            .startNot()
            .lessThanEquals(
                idToColumnName.get(expr.ref().fieldId()),
                type(expr.ref().type()),
                literal(expr.ref().type(), lit.value()))
            .end();
  }

  @Override
  public <T> Action gtEq(Bound<T> expr, Literal<T> lit) {
    // ORC SearchArguments do not have a greaterThanOrEquals predicate, so we use not(lessThan)
    // e.g. x >= 5 => not(x < 5)
    return () ->
        this.builder
            .startNot()
            .lessThan(
                idToColumnName.get(expr.ref().fieldId()),
                type(expr.ref().type()),
                literal(expr.ref().type(), lit.value()))
            .end();
  }

  @Override
  public <T> Action eq(Bound<T> expr, Literal<T> lit) {
    return () ->
        this.builder.equals(
            idToColumnName.get(expr.ref().fieldId()),
            type(expr.ref().type()),
            literal(expr.ref().type(), lit.value()));
  }

  /**
   * 转换 notEq 谓词。
   *
   * <p>逻辑：因 ORC 用 SQL 语义（col != x 排除 NULL），而 Iceberg 保留 NULL 行， 故等价转换为 {@code col IS NULL OR col !=
   * x}。
   */
  @Override
  public <T> Action notEq(Bound<T> expr, Literal<T> lit) {
    // NOTE: ORC uses SQL semantics for Search Arguments, so an expression like
    // `col != 1` will exclude rows where col is NULL along with rows where col = 1
    // In contrast, Iceberg's Expressions will keep rows with NULL values
    // So the equivalent ORC Search Argument for an Iceberg Expression `col != x`
    // is `col IS NULL OR col != x`
    return () -> {
      this.builder.startOr();
      isNull(expr).invoke();
      this.builder.startNot();
      eq(expr, lit).invoke();
      this.builder.end(); // end NOT
      this.builder.end(); // end OR
    };
  }

  @Override
  public <T> Action in(Bound<T> expr, Set<T> literalSet) {
    return () ->
        this.builder.in(
            idToColumnName.get(expr.ref().fieldId()),
            type(expr.ref().type()),
            literalSet.stream().map(lit -> literal(expr.ref().type(), lit)).toArray(Object[]::new));
  }

  /**
   * 转换 notIn 谓词。
   *
   * <p>逻辑：同 notEq，补 IS NULL 分支：{@code col IS NULL OR col NOT IN {x}}。
   */
  @Override
  public <T> Action notIn(Bound<T> expr, Set<T> literalSet) {
    // NOTE: ORC uses SQL semantics for Search Arguments, so an expression like
    // `col NOT IN {1}` will exclude rows where col is NULL along with rows where col = 1
    // In contrast, Iceberg's Expressions will keep rows with NULL values
    // So the equivalent ORC Search Argument for an Iceberg Expression `col NOT IN {x}`
    // is `col IS NULL OR col NOT IN {x}`
    return () -> {
      this.builder.startOr();
      isNull(expr).invoke();
      this.builder.startNot();
      in(expr, literalSet).invoke();
      this.builder.end(); // end NOT
      this.builder.end(); // end OR
    };
  }

  /**
   * 转换 startsWith 谓词。
   *
   * <p>设计要点：ORC 不支持前缀匹配下推，返回 YES_NO_NULL 表示该谓词不参与过滤。
   */
  @Override
  public <T> Action startsWith(Bound<T> expr, Literal<T> lit) {
    // Cannot push down STARTS_WITH operator to ORC, so return TruthValue.YES_NO_NULL which
    // signifies
    // that this predicate cannot help with filtering
    return () -> this.builder.literal(TruthValue.YES_NO_NULL);
  }

  /**
   * 转换 notStartsWith 谓词。
   *
   * <p>设计要点：同 startsWith，ORC 不支持下推，返回 YES_NO_NULL。
   */
  @Override
  public <T> Action notStartsWith(Bound<T> expr, Literal<T> lit) {
    // Cannot push down NOT_STARTS_WITH operator to ORC, so return TruthValue.YES_NO_NULL which
    // signifies
    // that this predicate cannot help with filtering
    return () -> this.builder.literal(TruthValue.YES_NO_NULL);
  }

  /**
   * 谓词分派入口：对不支持类型或非 BoundReference 的项返回 YES_NO_NULL 降级。
   *
   * <p>逻辑：UNSUPPORTED_TYPES（BINARY/FIXED/UUID/STRUCT/MAP/LIST）因 ORC PredicateLeaf
   * 无法表示而跳过；其余委托父类分派到具体 eq/lt/gt 等方法。
   */
  @Override
  public <T> Action predicate(BoundPredicate<T> pred) {
    if (UNSUPPORTED_TYPES.contains(pred.ref().type().typeId())
        || !(pred.term() instanceof BoundReference)) {
      // Cannot push down predicates for types which cannot be represented in PredicateLeaf.Type, so
      // return
      // TruthValue.YES_NO_NULL which signifies that this predicate cannot help with filtering
      return () -> this.builder.literal(TruthValue.YES_NO_NULL);
    } else {
      return super.predicate(pred);
    }
  }

  @FunctionalInterface
  interface Action {
    void invoke();
  }

  /**
   * Iceberg 类型 → ORC PredicateLeaf.Type 映射。
   *
   * @throws UnsupportedOperationException 出现 ORC 不支持的谓词类型
   */
  private PredicateLeaf.Type type(Type icebergType) {
    switch (icebergType.typeId()) {
      case BOOLEAN:
        return PredicateLeaf.Type.BOOLEAN;
      case INTEGER:
      case LONG:
      case TIME:
        return PredicateLeaf.Type.LONG;
      case FLOAT:
      case DOUBLE:
        return PredicateLeaf.Type.FLOAT;
      case DATE:
        return PredicateLeaf.Type.DATE;
      case TIMESTAMP:
        return PredicateLeaf.Type.TIMESTAMP;
      case STRING:
        return PredicateLeaf.Type.STRING;
      case DECIMAL:
        return PredicateLeaf.Type.DECIMAL;
      default:
        throw new UnsupportedOperationException(
            "Type " + icebergType + " not supported in ORC SearchArguments");
    }
  }

  /**
   * Iceberg 字面量 → ORC SearchArgument 所需的 Java 对象转换。
   *
   * <p>逻辑：INTEGER→Long，FLOAT→Double，DATE→java.sql.Date，TIMESTAMP→java.sql.Timestamp，
   * DECIMAL→HiveDecimalWritable，STRING→toString，其余原样返回。
   *
   * @throws UnsupportedOperationException 出现 ORC 不支持的类型
   */
  private <T> Object literal(Type icebergType, T icebergLiteral) {
    switch (icebergType.typeId()) {
      case BOOLEAN:
      case LONG:
      case TIME:
      case DOUBLE:
        return icebergLiteral;
      case INTEGER:
        return ((Integer) icebergLiteral).longValue();
      case FLOAT:
        return ((Float) icebergLiteral).doubleValue();
      case STRING:
        return icebergLiteral.toString();
      case DATE:
        return Date.valueOf(LocalDate.ofEpochDay((Integer) icebergLiteral));
      case TIMESTAMP:
        long microsFromEpoch = (Long) icebergLiteral;
        return Timestamp.from(
            Instant.ofEpochSecond(
                Math.floorDiv(microsFromEpoch, 1_000_000),
                Math.floorMod(microsFromEpoch, 1_000_000) * 1_000));
      case DECIMAL:
        return new HiveDecimalWritable(HiveDecimal.create((BigDecimal) icebergLiteral, false));
      default:
        throw new UnsupportedOperationException(
            "Type " + icebergType + " not supported in ORC SearchArguments");
    }
  }
}
