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
import org.apache.iceberg.relocated.com.google.common.base.Joiner;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Iterables;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types.StructType;
import org.apache.iceberg.util.CharSequenceSet;

/**
 * 文件级说明：未绑定的谓词（UnboundPredicate）实现。
 *
 * <p>所属模块：iceberg-api（表达式 API 包）。职责：表示一个尚未与表 Schema 绑定的谓词 （如 {@code a > 5}），持有操作符（{@link
 * Operation}）、未绑定项（{@link UnboundTerm}） 与字面量列表；调用 {@link #bind(StructType, boolean)} 后会根据字段类型、是否必填、
 * 字面量是否在类型范围内等情况，返回优化后的 {@link Expression}（可能是 {@link BoundLiteralPredicate}/{@link
 * BoundSetPredicate}/{@link BoundUnaryPredicate}， 也可能是常量 {@link Expressions#alwaysTrue()}/{@link
 * Expressions#alwaysFalse()}）。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>绑定期优化：在 bind 阶段就能判断谓词恒真/恒假（例如对必填字段做 IS_NULL 恒假、 字面量超出类型范围时按操作符短路），从而在查询计划期剪枝，避免无谓扫描。
 *   <li>支持 IN/NOT_IN 集合谓词与单值谓词复用同一类，通过 literals 是否为 null、是否多值来区分。
 *   <li>实现 {@link Unbound} 接口，与 {@link BoundPredicate} 解耦，便于在未拿到 Schema 前 构造并序列化表达式（例如 REST 请求体）。
 * </ul>
 *
 * <p>上下游：上游由用户/引擎通过 {@link Expressions} 工厂或 SQL 解析构造； 下游在 {@code core} 的扫描/Manifest 过滤流程中被 {@code
 * bind} 后用于数据文件裁剪。
 */
public class UnboundPredicate<T> extends Predicate<T, UnboundTerm<T>>
    implements Unbound<T, Expression> {
  private static final Joiner COMMA = Joiner.on(", ");

  private final List<Literal<T>> literals;

  /** 由原始值构造单值谓词的包级构造方法，内部把 value 包装为 {@link Literals#from(Object)}。 */
  UnboundPredicate(Operation op, UnboundTerm<T> term, T value) {
    this(op, term, Literals.from(value));
  }

  /** 构造一元谓词（IS_NULL/NOT_NULL/IS_NAN/NOT_NAN），无字面量。 */
  UnboundPredicate(Operation op, UnboundTerm<T> term) {
    super(op, term);
    this.literals = null;
  }

  /** 由已构造的 {@link Literal} 构造单值谓词。 */
  UnboundPredicate(Operation op, UnboundTerm<T> term, Literal<T> lit) {
    super(op, term);
    this.literals = Lists.newArrayList(lit);
  }

  /** 由值集合构造 IN/NOT_IN 谓词，每个值通过 {@link Literals#from(Object)} 转换。 */
  UnboundPredicate(Operation op, UnboundTerm<T> term, Iterable<T> values) {
    super(op, term);
    this.literals = Lists.newArrayList(Iterables.transform(values, Literals::from));
  }

  private UnboundPredicate(Operation op, UnboundTerm<T> term, List<Literal<T>> literals) {
    super(op, term);
    this.literals = literals;
  }

  /** 返回该谓词引用的字段名。 */
  @Override
  public NamedReference<?> ref() {
    return term().ref();
  }

  /**
   * 取反当前谓词，返回新的未绑定谓词（如 {@code a > 5} 取反为 {@code a <= 5}）。
   *
   * @return 取反后的 {@link UnboundPredicate}
   */
  @Override
  public Expression negate() {
    return new UnboundPredicate<>(op().negate(), term(), literals);
  }

  /**
   * 返回单值谓词的字面量；IN/NOT_IN 谓词调用会抛异常。
   *
   * @return 单值字面量；一元谓词（无字面量）返回 null
   * @throws IllegalArgumentException 若对 IN/NOT_IN 调用
   */
  public Literal<T> literal() {
    Preconditions.checkArgument(
        op() != Operation.IN && op() != Operation.NOT_IN,
        "%s predicate cannot return a literal",
        op());
    return literals == null ? null : Iterables.getOnlyElement(literals);
  }

  /** 返回全部字面量列表（IN/NOT_IN 用于多值，单值谓词返回单元素列表，一元谓词返回 null）。 */
  public List<Literal<T>> literals() {
    return literals;
  }

  /**
   * 以默认大小写敏感模式绑定本谓词（包级可见，仅供既有测试使用）。
   *
   * @param struct 用于按名解析引用的 {@link StructType}
   * @return 绑定并优化后的 {@link Expression}
   * @throws ValidationException 若字面量与绑定引用类型不匹配，或比较非法
   */
  Expression bind(StructType struct) {
    return bind(struct, true);
  }

  /**
   * 将本未绑定谓词绑定到给定 Schema，返回优化后的 {@link Expression}。
   *
   * <p>逻辑步骤：
   *
   * <ol>
   *   <li>先把 {@link UnboundTerm} 绑定为 {@link BoundTerm}（解析字段引用、类型）。
   *   <li>若 literals 为 null（一元操作 IS_NULL/NOT_NULL/IS_NAN/NOT_NAN），走 {@link
   *       #bindUnaryOperation(BoundTerm)}。
   *   <li>若是 IN/NOT_IN 集合操作，走 {@link #bindInOperation(BoundTerm)}。
   *   <li>否则走 {@link #bindLiteralOperation(BoundTerm)} 处理单值比较。
   * </ol>
   *
   * @param struct 用于按名解析引用的 {@link StructType}
   * @param caseSensitive 是否区分字段名大小写
   * @return 绑定并优化后的 {@link Expression}（可能是 Bound 谓词或常量 true/false）
   * @throws ValidationException 若字面量与绑定引用类型不匹配，或比较非法
   */
  @Override
  public Expression bind(StructType struct, boolean caseSensitive) {
    BoundTerm<T> bound = term().bind(struct, caseSensitive);

    if (literals == null) {
      return bindUnaryOperation(bound);
    }

    if (op() == Operation.IN || op() == Operation.NOT_IN) {
      return bindInOperation(bound);
    }

    return bindLiteralOperation(bound);
  }

  /**
   * 绑定一元操作（IS_NULL/NOT_NULL/IS_NAN/NOT_NAN）。
   *
   * <p>逻辑：根据字段是否必填做短路优化——
   *
   * <ul>
   *   <li>IS_NULL 对必填字段恒假；NOT_NULL 对必填字段恒真。
   *   <li>IS_NAN/NOT_NAN 仅适用于浮点类型（FLOAT/DOUBLE），否则抛 ValidationException。
   * </ul>
   *
   * @param boundTerm 已绑定的项
   * @return 绑定后的一元谓词或常量表达式
   * @throws ValidationException 若操作符非法或 IS_NAN/NOT_NAN 用于非浮点列
   */
  private Expression bindUnaryOperation(BoundTerm<T> boundTerm) {
    switch (op()) {
      case IS_NULL:
        if (boundTerm.ref().field().isRequired()) {
          return Expressions.alwaysFalse();
        }
        return new BoundUnaryPredicate<>(Operation.IS_NULL, boundTerm);
      case NOT_NULL:
        if (boundTerm.ref().field().isRequired()) {
          return Expressions.alwaysTrue();
        }
        return new BoundUnaryPredicate<>(Operation.NOT_NULL, boundTerm);
      case IS_NAN:
        if (floatingType(boundTerm.type().typeId())) {
          return new BoundUnaryPredicate<>(Operation.IS_NAN, boundTerm);
        } else {
          throw new ValidationException("IsNaN cannot be used with a non-floating-point column");
        }
      case NOT_NAN:
        if (floatingType(boundTerm.type().typeId())) {
          return new BoundUnaryPredicate<>(Operation.NOT_NAN, boundTerm);
        } else {
          throw new ValidationException("NotNaN cannot be used with a non-floating-point column");
        }
      default:
        throw new ValidationException("Operation must be IS_NULL, NOT_NULL, IS_NAN, or NOT_NAN");
    }
  }

  /** 判断给定类型是否为浮点类型（FLOAT 或 DOUBLE）。 */
  private boolean floatingType(Type.TypeID typeID) {
    return Type.TypeID.DOUBLE.equals(typeID) || Type.TypeID.FLOAT.equals(typeID);
  }

  /**
   * 绑定单值字面量比较操作（EQ/NOT_EQ/LT/LT_EQ/GT/GT_EQ/STARTS_WITH 等）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>把字面量转换为字段类型；转换失败抛 ValidationException。
   *   <li>若字面量高于类型上界（aboveMax）：LT/LT_EQ/NOT_EQ 恒真，GT/GT_EQ/EQ 恒假。
   *   <li>若字面量低于类型下界（belowMin）：GT/GT_EQ/NOT_EQ 恒真，LT/LT_EQ/EQ 恒假。
   *   <li>否则返回 {@link BoundLiteralPredicate}。
   * </ol>
   *
   * @param boundTerm 已绑定的项
   * @return 绑定后的字面量谓词或常量表达式
   * @throws ValidationException 若字面量无法转换为目标类型
   */
  private Expression bindLiteralOperation(BoundTerm<T> boundTerm) {
    Literal<T> lit = literal().to(boundTerm.type());

    if (lit == null) {
      throw new ValidationException(
          "Invalid value for conversion to type %s: %s (%s)",
          boundTerm.type(), literal().value(), literal().value().getClass().getName());

    } else if (lit == Literals.aboveMax()) {
      switch (op()) {
        case LT:
        case LT_EQ:
        case NOT_EQ:
          return Expressions.alwaysTrue();
        case GT:
        case GT_EQ:
        case EQ:
          return Expressions.alwaysFalse();
      }
    } else if (lit == Literals.belowMin()) {
      switch (op()) {
        case GT:
        case GT_EQ:
        case NOT_EQ:
          return Expressions.alwaysTrue();
        case LT:
        case LT_EQ:
        case EQ:
          return Expressions.alwaysFalse();
      }
    }

    // TODO: translate truncate(col) == value to startsWith(value)
    return new BoundLiteralPredicate<>(op(), boundTerm, lit);
  }

  /**
   * 绑定 IN/NOT_IN 集合操作。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>把每个字面量转换为字段类型，过滤掉 aboveMax/belowMin（这些值不可能命中）。
   *   <li>若过滤后为空：IN 恒假，NOT_IN 恒真。
   *   <li>若仅剩一个值：退化为 EQ/NOT_EQ 单值谓词。
   *   <li>否则返回 {@link BoundSetPredicate} 集合谓词。
   * </ol>
   *
   * @param boundTerm 已绑定的项
   * @return 绑定后的集合谓词、单值谓词或常量表达式
   * @throws ValidationException 若字面量无法转换或操作符非 IN/NOT_IN
   */
  private Expression bindInOperation(BoundTerm<T> boundTerm) {
    List<Literal<T>> convertedLiterals =
        Lists.newArrayList(
            Iterables.filter(
                Lists.transform(
                    literals,
                    lit -> {
                      Literal<T> converted = lit.to(boundTerm.type());
                      ValidationException.check(
                          converted != null,
                          "Invalid value for conversion to type %s: %s (%s)",
                          boundTerm.type(),
                          lit,
                          lit.getClass().getName());
                      return converted;
                    }),
                lit -> lit != Literals.aboveMax() && lit != Literals.belowMin()));

    if (convertedLiterals.isEmpty()) {
      switch (op()) {
        case IN:
          return Expressions.alwaysFalse();
        case NOT_IN:
          return Expressions.alwaysTrue();
        default:
          throw new ValidationException("Operation must be IN or NOT_IN");
      }
    }

    Set<T> literalSet = setOf(convertedLiterals);
    if (literalSet.size() == 1) {
      switch (op()) {
        case IN:
          return new BoundLiteralPredicate<>(
              Operation.EQ, boundTerm, Iterables.get(convertedLiterals, 0));
        case NOT_IN:
          return new BoundLiteralPredicate<>(
              Operation.NOT_EQ, boundTerm, Iterables.get(convertedLiterals, 0));
        default:
          throw new ValidationException("Operation must be IN or NOT_IN");
      }
    }

    return new BoundSetPredicate<>(op(), boundTerm, literalSet);
  }

  @Override
  public String toString() {
    switch (op()) {
      case IS_NULL:
        return "is_null(" + term() + ")";
      case NOT_NULL:
        return "not_null(" + term() + ")";
      case IS_NAN:
        return "is_nan(" + term() + ")";
      case NOT_NAN:
        return "not_nan(" + term() + ")";
      case LT:
        return term() + " < " + literal();
      case LT_EQ:
        return term() + " <= " + literal();
      case GT:
        return term() + " > " + literal();
      case GT_EQ:
        return term() + " >= " + literal();
      case EQ:
        return term() + " == " + literal();
      case NOT_EQ:
        return term() + " != " + literal();
      case STARTS_WITH:
        return term() + " startsWith \"" + literal() + "\"";
      case NOT_STARTS_WITH:
        return term() + " notStartsWith \"" + literal() + "\"";
      case IN:
        return term() + " in (" + COMMA.join(literals()) + ")";
      case NOT_IN:
        return term() + " not in (" + COMMA.join(literals()) + ")";
      default:
        return "Invalid predicate: operation = " + op();
    }
  }

  @SuppressWarnings("unchecked")
  static <T> Set<T> setOf(Iterable<Literal<T>> literals) {
    Literal<T> lit = Iterables.get(literals, 0);
    if (lit instanceof Literals.StringLiteral) {
      Iterable<T> values = Iterables.transform(literals, Literal::value);
      Iterable<CharSequence> charSeqs = Iterables.transform(values, val -> (CharSequence) val);
      return (Set<T>) CharSequenceSet.of(charSeqs);
    } else {
      return Sets.newHashSet(Iterables.transform(literals, Literal::value));
    }
  }
}
