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
import java.util.Set;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.expressions.ExpressionVisitors.BoundVisitor;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.NaNUtil;

/**
 * 行级表达式求值器：在一条数据行上判定（已绑定的）布尔表达式是否成立。
 *
 * <p>所属模块：iceberg-api（表达式体系的运行时求值入口之一，针对“数据行”粒度）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>构造时把未绑定表达式经 {@link Binder} 绑定到给定 {@link StructType}。
 *   <li>提供 {@link #eval(StructLike)}：对一条实现了 {@link StructLike} 的数据行求布尔结果。
 * </ul>
 *
 * <p>设计意图：内部用一个 {@link EvalVisitor}（继承 {@link BoundVisitor}）配合 {@link
 * ExpressionVisitors#visitEvaluator} 做带短路的后续遍历——AND 左侧为 false 时直接返回 false、OR 左侧为 true 时直接返回
 * true，避免不必要的子树求值。绑定只在构造时执行一次， 后续多次 eval 复用已绑定表达式，{@code struct} 作为求值期上下文按行设置。本类线程安全
 * （无共享可变状态，EvalVisitor 每次 eval 新建）。
 *
 * <p>上下游关系：输入由扫描任务等提供行数据；被 Iceberg 各引擎集成（Spark/Flink 等）与 core 模块在行级过滤时调用。与 {@link
 * InclusiveMetricsEvaluator}（文件级）互补。
 */
public class Evaluator implements Serializable {
  private final Expression expr;

  /**
   * 构造行级求值器（大小写敏感，默认）。
   *
   * @param struct 数据行的 struct 类型
   * @param unbound 未绑定表达式
   */
  public Evaluator(StructType struct, Expression unbound) {
    this.expr = Binder.bind(struct, unbound, true);
  }

  /**
   * 构造行级求值器（可指定大小写敏感）。
   *
   * @param struct 数据行的 struct 类型
   * @param unbound 未绑定表达式
   * @param caseSensitive 绑定时是否大小写敏感
   */
  public Evaluator(StructType struct, Expression unbound, boolean caseSensitive) {
    this.expr = Binder.bind(struct, unbound, caseSensitive);
  }

  /**
   * 在一条数据行上求布尔结果。
   *
   * <p>逻辑：新建一个 {@link EvalVisitor}，把当前行设为其求值上下文，再以带短路的 {@link ExpressionVisitors#visitEvaluator}
   * 遍历已绑定表达式树。
   *
   * @param data 一条数据行
   * @return 表达式在该行上的布尔结果
   */
  public boolean eval(StructLike data) {
    return new EvalVisitor().eval(data);
  }

  /**
   * 行级求值访问者：把已绑定表达式树的每个节点映射为布尔结果。
   *
   * <p>设计意图：继承 {@link BoundVisitor}，按谓词类型分发到 isNull/notNull/lt/eq/in 等方法； 比较类判定复用 {@link
   * Literal#comparator()}；NaN 判定委托 {@link NaNUtil}。 {@code struct} 字段保存当前行，供 {@code
   * valueExpr.eval(struct)} 取字段值。
   */
  private class EvalVisitor extends BoundVisitor<Boolean> {
    private StructLike struct;

    private boolean eval(StructLike row) {
      this.struct = row;
      return ExpressionVisitors.visitEvaluator(expr, this);
    }

    @Override
    public Boolean alwaysTrue() {
      return true;
    }

    @Override
    public Boolean alwaysFalse() {
      return false;
    }

    @Override
    public Boolean not(Boolean result) {
      return !result;
    }

    @Override
    public Boolean and(Boolean leftResult, Boolean rightResult) {
      return leftResult && rightResult;
    }

    @Override
    public Boolean or(Boolean leftResult, Boolean rightResult) {
      return leftResult || rightResult;
    }

    /** 判定字段值是否为 null。 */
    @Override
    public <T> Boolean isNull(Bound<T> valueExpr) {
      return valueExpr.eval(struct) == null;
    }

    /** 判定字段值是否非 null。 */
    @Override
    public <T> Boolean notNull(Bound<T> valueExpr) {
      return valueExpr.eval(struct) != null;
    }

    /** 判定字段值是否为 NaN。 */
    @Override
    public <T> Boolean isNaN(Bound<T> valueExpr) {
      return NaNUtil.isNaN(valueExpr.eval(struct));
    }

    /** 判定字段值是否非 NaN。 */
    @Override
    public <T> Boolean notNaN(Bound<T> valueExpr) {
      return !NaNUtil.isNaN(valueExpr.eval(struct));
    }

    /** 判定字段值是否小于字面量。 */
    @Override
    public <T> Boolean lt(Bound<T> valueExpr, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return cmp.compare(valueExpr.eval(struct), lit.value()) < 0;
    }

    /** 判定字段值是否小于等于字面量。 */
    @Override
    public <T> Boolean ltEq(Bound<T> valueExpr, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return cmp.compare(valueExpr.eval(struct), lit.value()) <= 0;
    }

    /** 判定字段值是否大于字面量。 */
    @Override
    public <T> Boolean gt(Bound<T> valueExpr, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return cmp.compare(valueExpr.eval(struct), lit.value()) > 0;
    }

    /** 判定字段值是否大于等于字面量。 */
    @Override
    public <T> Boolean gtEq(Bound<T> valueExpr, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return cmp.compare(valueExpr.eval(struct), lit.value()) >= 0;
    }

    /** 判定字段值是否等于字面量。 */
    @Override
    public <T> Boolean eq(Bound<T> valueExpr, Literal<T> lit) {
      Comparator<T> cmp = lit.comparator();
      return cmp.compare(valueExpr.eval(struct), lit.value()) == 0;
    }

    /** 判定字段值是否不等于字面量。 */
    @Override
    public <T> Boolean notEq(Bound<T> valueExpr, Literal<T> lit) {
      return !eq(valueExpr, lit);
    }

    /** 判定字段值是否在字面量集合中。 */
    @Override
    public <T> Boolean in(Bound<T> valueExpr, Set<T> literalSet) {
      return literalSet.contains(valueExpr.eval(struct));
    }

    /** 判定字段值是否不在字面量集合中。 */
    @Override
    public <T> Boolean notIn(Bound<T> valueExpr, Set<T> literalSet) {
      return !in(valueExpr, literalSet);
    }

    /** 判定字段值是否以字面量为前缀（null 值返回 false）。 */
    @Override
    public <T> Boolean startsWith(Bound<T> valueExpr, Literal<T> lit) {
      T evalRes = valueExpr.eval(struct);
      return evalRes != null && ((String) evalRes).startsWith((String) lit.value());
    }

    /** 判定字段值是否不以字面量为前缀。 */
    @Override
    public <T> Boolean notStartsWith(Bound<T> valueExpr, Literal<T> lit) {
      return !startsWith(valueExpr, lit);
    }
  }
}
