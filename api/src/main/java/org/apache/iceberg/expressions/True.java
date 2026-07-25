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

import java.io.ObjectStreamException;

/**
 * 恒真表达式：始终求值为 true 的常量节点，对应 {@link Operation#TRUE}。
 *
 * <p>所属模块：iceberg-api（表达式常量节点之一；与 {@link False} 配对）。
 *
 * <p>职责：作为表达式化简的终止节点，例如绑定阶段把“必满足”的谓词化简为 True。
 *
 * <p>设计意图：单例（{@link #INSTANCE}）+ 私有构造器，避免重复分配；通过 {@link #writeReplace()} 在序列化时替换为 {@link
 * SerializationProxies.ConstantExpressionProxy}， 避免跨版本反序列化类不兼容。
 *
 * <p>上下游关系：由 {@link Expressions#alwaysTrue()} 与绑定/化简逻辑产生；被各类 Evaluator 与 Visitor 视作短路终止节点。
 */
public class True implements Expression {
  /** 全局唯一实例。 */
  static final True INSTANCE = new True();

  private True() {}

  /** 返回 {@link Operation#TRUE}。 */
  @Override
  public Operation op() {
    return Operation.TRUE;
  }

  /**
   * 返回此表达式的否定形式（恒假）。
   *
   * @return {@link False#INSTANCE}
   */
  @Override
  public Expression negate() {
    return False.INSTANCE;
  }

  /**
   * 判定等价：仅当对方也是 TRUE 操作时返回 true。
   *
   * @param other 另一表达式
   * @return 对方 op 为 TRUE 时返回 true
   */
  @Override
  public boolean isEquivalentTo(Expression other) {
    return other.op() == Operation.TRUE;
  }

  /** 返回可读字符串 "true"。 */
  @Override
  public String toString() {
    return "true";
  }

  /**
   * 序列化替换钩子：以常量代理替代自身进行序列化。
   *
   * <p>设计要点：避免直接序列化单例类，跨版本兼容性更好。
   *
   * @return 持久化代理
   * @throws ObjectStreamException 序列化协议异常
   */
  Object writeReplace() throws ObjectStreamException {
    return new SerializationProxies.ConstantExpressionProxy(true);
  }
}
