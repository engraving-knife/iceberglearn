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
import java.util.Locale;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 布尔表达式树根接口：所有表达式（谓词、聚合、逻辑组合）的统一抽象。
 *
 * <p>所属模块：iceberg-api（表达式体系的顶层契约；定义操作枚举、节点协议与默认行为）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>声明 {@link #op()} 返回节点对应的 {@link Operation}。
 *   <li>提供默认不可求否的 {@link #negate()} 与默认恒 false 的 {@link #isEquivalentTo}， 由具体子类型按需覆盖。
 *   <li>内嵌 {@link Operation} 枚举，覆盖全部谓词、逻辑、聚合操作及它们之间的转换 （negate / flipLR）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现 {@link Serializable}：表达式需在分布式引擎间序列化传输，因此所有节点都必须可序列化。
 *   <li>Operation 集中定义 negate / flipLR，使逻辑等价变换不散落在各子类中，便于维护。
 *   <li>默认方法提供“安全降级”，使新操作无需在每个子类实现 negate/isEquivalentTo。
 * </ul>
 *
 * <p>上下游关系：被 {@link Binder}、{@link ExpressionVisitors}、各类 Evaluator、序列化代理 （{@link
 * SerializationProxies}）等广泛依赖。
 */
public interface Expression extends Serializable {
  /**
   * 表达式操作枚举：覆盖布尔常量、一元/二元谓词、逻辑运算、字符串前缀匹配与聚合操作。
   *
   * <p>设计意图：用一个枚举承载所有操作类型，使表达式节点可以仅以 op 区分语义， 便于访问者分派与等价变换。
   */
  enum Operation {
    TRUE,
    FALSE,
    IS_NULL,
    NOT_NULL,
    IS_NAN,
    NOT_NAN,
    LT,
    LT_EQ,
    GT,
    GT_EQ,
    EQ,
    NOT_EQ,
    IN,
    NOT_IN,
    NOT,
    AND,
    OR,
    STARTS_WITH,
    NOT_STARTS_WITH,
    COUNT,
    COUNT_STAR,
    MAX,
    MIN;

    /**
     * 按字符串解析操作类型（大小写不敏感）。
     *
     * <p>逻辑：先校验非 null，再以大写形式调用 {@link #valueOf}；解析失败抛 {@link IllegalArgumentException} 并保留原异常链。
     *
     * @param operationType 操作名字符串
     * @return 解析得到的 {@link Operation}
     * @throws IllegalArgumentException 入参为 null 或无法识别时抛出
     */
    public static Operation fromString(String operationType) {
      Preconditions.checkArgument(null != operationType, "Invalid operation type: null");
      try {
        return Expression.Operation.valueOf(operationType.toUpperCase(Locale.ENGLISH));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            String.format("Invalid operation type: %s", operationType), e);
      }
    }

    /**
     * 返回此操作取反后的对应操作。
     *
     * <p>逻辑：按操作成对映射（如 IS_NULL↔NOT_NULL、LT↔GT_EQ、IN↔NOT_IN 等）； 不可取反的操作抛 {@link
     * IllegalArgumentException}。
     *
     * @return 取反后的操作
     * @throws IllegalArgumentException 若该操作无否定形式
     */
    public Operation negate() {
      switch (this) {
        case IS_NULL:
          return Operation.NOT_NULL;
        case NOT_NULL:
          return Operation.IS_NULL;
        case IS_NAN:
          return Operation.NOT_NAN;
        case NOT_NAN:
          return Operation.IS_NAN;
        case LT:
          return Operation.GT_EQ;
        case LT_EQ:
          return Operation.GT;
        case GT:
          return Operation.LT_EQ;
        case GT_EQ:
          return Operation.LT;
        case EQ:
          return Operation.NOT_EQ;
        case NOT_EQ:
          return Operation.EQ;
        case IN:
          return Operation.NOT_IN;
        case NOT_IN:
          return Operation.IN;
        case STARTS_WITH:
          return Operation.NOT_STARTS_WITH;
        case NOT_STARTS_WITH:
          return Operation.STARTS_WITH;
        default:
          throw new IllegalArgumentException("No negation for operation: " + this);
      }
    }

    /**
     * 返回左右操作数交换后的等价操作。
     *
     * <p>逻辑：比较类操作按对称关系映射（如 LT↔GT、LT_EQ↔GT_EQ），相等/不等与 逻辑 AND/OR 自身对称；不可交换的操作抛 {@link
     * IllegalArgumentException}。
     *
     * @return 交换左右后的等价操作
     * @throws IllegalArgumentException 若该操作不支持左右交换
     */
    // Allow flipLR as a name because it's a public API
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    public Operation flipLR() {
      switch (this) {
        case LT:
          return Operation.GT;
        case LT_EQ:
          return Operation.GT_EQ;
        case GT:
          return Operation.LT;
        case GT_EQ:
          return Operation.LT_EQ;
        case EQ:
          return Operation.EQ;
        case NOT_EQ:
          return Operation.NOT_EQ;
        case AND:
          return Operation.AND;
        case OR:
          return Operation.OR;
        default:
          throw new IllegalArgumentException("No left-right flip for operation: " + this);
      }
    }
  }

  /** 返回本表达式节点对应的操作类型。 */
  Operation op();

  /**
   * 返回此表达式的否定形式，等价于 not(this)。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，由支持取反的子类型覆盖。
   *
   * @return 否定后的表达式
   */
  default Expression negate() {
    throw new UnsupportedOperationException(String.format("%s cannot be negated", this));
  }

  /**
   * 判定本表达式是否与另一表达式接受相同的输入集合（语义等价）。
   *
   * <p>语义：返回 true 时保证两表达式对相同输入求值相同；返回 false 不保证不同 （可能仍等价但本方法未识别）。建议在调用前先做 not 重写与绑定，以获得最佳结果。
   *
   * @param other 另一表达式
   * @return 确定等价返回 true
   */
  default boolean isEquivalentTo(Expression other) {
    // only bound predicates can be equivalent
    return false;
  }
}
