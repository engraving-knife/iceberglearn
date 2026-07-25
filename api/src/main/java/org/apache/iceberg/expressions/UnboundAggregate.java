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

import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.types.Types;

/**
 * 未绑定聚合表达式：用户构造阶段持有的聚合形态，引用尚未解析到具体字段。
 *
 * <p>所属模块：iceberg-api（聚合分支的未绑定形态；与 {@link BoundAggregate} 配对）。
 *
 * <p>职责：承载聚合操作类型与未绑定 term，在 {@link #bind} 时按操作类型创建对应的 已绑定聚合实现（CountStar / CountNonNull /
 * MaxAggregate / MinAggregate）。
 *
 * <p>设计意图：把“用户书写”与“引擎求值”解耦——用户用名字写聚合，绑定阶段才解析为 按字段 id 访问的具体实现。COUNT_STAR 不需要 term，故绑定时传 null。
 *
 * <p>上下游关系：由 {@link Expressions#count} / {@code max} / {@code min} 等构造； 被 {@link Binder} 调用 {@link
 * #bind} 转换为 {@link BoundAggregate}。
 *
 * @param <T> 聚合输入值的 Java 类型
 */
public class UnboundAggregate<T> extends Aggregate<UnboundTerm<T>>
    implements Unbound<T, Expression> {

  /**
   * 构造未绑定聚合。
   *
   * @param op 聚合操作类型
   * @param term 未绑定 term
   */
  UnboundAggregate(Operation op, UnboundTerm<T> term) {
    super(op, term);
  }

  /**
   * 返回此聚合底层引用（未绑定形态）。
   *
   * @return 命名引用
   */
  @Override
  public NamedReference<?> ref() {
    return term().ref();
  }

  /**
   * 把本未绑定聚合绑定到具体 struct，返回对应的已绑定聚合实现。
   *
   * <p>逻辑：按操作类型分派——COUNT_STAR 不需要 term，直接返回 {@link CountStar}； COUNT/MAX/MIN 先绑定 term 再构造对应实现；其他操作抛
   * {@link UnsupportedOperationException}。
   *
   * @param struct 用于解析引用的 {@link Types.StructType}
   * @param caseSensitive 是否大小写敏感
   * @return 已绑定聚合表达式
   * @throws ValidationException 字面量与绑定引用类型不匹配，或比较非法时抛出
   */
  @Override
  public Expression bind(Types.StructType struct, boolean caseSensitive) {
    switch (op()) {
      case COUNT_STAR:
        return new CountStar<>(null);
      case COUNT:
        return new CountNonNull<>(boundTerm(struct, caseSensitive));
      case MAX:
        return new MaxAggregate<>(boundTerm(struct, caseSensitive));
      case MIN:
        return new MinAggregate<>(boundTerm(struct, caseSensitive));
      default:
        throw new UnsupportedOperationException("Unsupported aggregate type: " + op());
    }
  }

  /**
   * 绑定 term 到 struct，返回 BoundTerm。
   *
   * <p>逻辑：校验 term 非空，再委托 term 自身的 bind。
   *
   * @param struct 用于解析引用的 {@link Types.StructType}
   * @param caseSensitive 是否大小写敏感
   * @return 已绑定 term
   */
  private BoundTerm<T> boundTerm(Types.StructType struct, boolean caseSensitive) {
    Preconditions.checkArgument(term() != null, "Invalid aggregate term: null");
    return term().bind(struct, caseSensitive);
  }
}
