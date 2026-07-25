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

import java.io.ObjectStreamException;
import java.io.Serializable;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 空（void）分区变换：恒返回 null，表示分区字段被弃用但元数据中保留。
 *
 * <p>所属模块：iceberg-api（被 PartitionSpec 持有，用于已删除/弃用的分区字段）。
 *
 * <p>职责：apply 恒返回 null；project/projectStrict 返回 null（不下推）；isVoid 返回 true。
 *
 * <p>设计意图：当一个分区字段不再使用时，不能直接从 PartitionSpec 删除（会破坏历史数据分区定位）， 而是改用 VoidTransform
 * 标记，使新写入的数据不产生该分区值，旧数据仍可读。单例 + 序列化代理保持单例语义。
 *
 * <p>上下游关系：由 {@link Transforms#alwaysNull()} 构造；被 PartitionSpec 持有； 被 {@link
 * PartitionSpecVisitor#alwaysNull} 识别。
 *
 * @param <S> 源值的 Java 类型
 */
class VoidTransform<S> implements Transform<S, Void> {
  private static final VoidTransform<Object> INSTANCE = new VoidTransform<>();

  /**
   * 返回 VoidTransform 单例。
   *
   * @param <T> 源值类型
   * @return VoidTransform 单例
   */
  @SuppressWarnings("unchecked")
  static <T> VoidTransform<T> get() {
    return (VoidTransform<T>) INSTANCE;
  }

  /** 恒返回 null 的可序列化函数，单例复用。 */
  private static class Apply<S> implements SerializableFunction<S, Void>, Serializable {
    private static final Apply<?> APPLY_INSTANCE = new Apply<>();

    @SuppressWarnings("unchecked")
    private static <S> Apply<S> get() {
      return (Apply<S>) APPLY_INSTANCE;
    }

    @Override
    public Void apply(S t) {
      return null;
    }
  }

  private VoidTransform() {}

  /**
   * apply 恒返回 null。
   *
   * @param value 源值（忽略）
   * @return 始终 null
   */
  @Override
  public Void apply(Object value) {
    return null;
  }

  /**
   * 绑定到类型，返回恒 null 的 Apply 单例。
   *
   * @param type 源类型（忽略）
   * @return Apply 单例
   */
  @Override
  public SerializableFunction<S, Void> bind(Type type) {
    return Apply.get();
  }

  /**
   * 假定可应用于任何类型，返回 true。
   *
   * @param type 待校验类型
   * @return 始终 true
   */
  @Override
  public boolean canTransform(Type type) {
    return true;
  }

  /**
   * 结果类型与源类型相同。
   *
   * @param sourceType 源类型
   * @return 同一类型
   */
  @Override
  public Type getResultType(Type sourceType) {
    return sourceType;
  }

  /**
   * strict 投影：void 变换无法投影，返回 null。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 始终 null
   */
  @Override
  public UnboundPredicate<Void> projectStrict(String name, BoundPredicate<S> predicate) {
    return null;
  }

  /**
   * inclusive 投影：void 变换无法投影，返回 null。
   *
   * @param name 分区列名
   * @param predicate 源字段谓词
   * @return 始终 null
   */
  @Override
  public UnboundPredicate<Void> project(String name, BoundPredicate<S> predicate) {
    return null;
  }

  /** 标记本变换为 void。 */
  @Override
  public boolean isVoid() {
    return true;
  }

  /**
   * 返回 "null"（void 值恒为 null）。
   *
   * @param value void 值（忽略）
   * @return "null"
   */
  @Override
  public String toHumanString(Void value) {
    return "null";
  }

  /**
   * 返回 "void" 字符串，用于元数据序列化。
   *
   * @return "void"
   */
  @Override
  public String toString() {
    return "void";
  }

  /**
   * 序列化替换：用代理对象替代本实例，保持单例语义。
   *
   * @return 序列化代理
   * @throws ObjectStreamException 不会抛出
   */
  Object writeReplace() throws ObjectStreamException {
    return SerializationProxies.VoidTransformProxy.get();
  }
}
