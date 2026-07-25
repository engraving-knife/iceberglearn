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

import java.io.Serializable;
import java.nio.ByteBuffer;
import org.apache.iceberg.expressions.BoundPredicate;
import org.apache.iceberg.expressions.UnboundPredicate;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.util.SerializableFunction;

/**
 * 分区变换接口：将源字段的值映射为分区值的核心抽象。
 *
 * <p>所属模块：iceberg-api（定义 Iceberg 表格式的公共契约，被 core 实现及各引擎集成模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对源值执行变换（如 identity/bucket/truncate/year 等），生成用于分区定位的值。
 *   <li>校验源类型 {@link Type} 是否可被本变换处理（{@link #canTransform(Type)}）。
 *   <li>声明变换产出类型 {@link #getResultType(Type)}，作为分区列的类型。
 *   <li>把字段上的 {@link BoundPredicate} 投影成分区值上的谓词（inclusive 与 strict 两类）， 用于分区裁剪与文件过滤。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>泛型 {@code <S,T>} 区分源值类型与变换结果类型，便于编译期类型检查。
 *   <li>提供 {@link #bind(Type)} 把"未绑定"的变换实例化为带具体类型的可序列化函数 {@link SerializableFunction}，避免在 apply
 *       时反复做类型判断；旧 {@link #apply(Object)} 已被弃用以推动调用方走 bind 路径。
 *   <li>{@link #preservesOrder()} / {@link #satisfiesOrderOf(Transform)} 暴露单调性语义， 供排序顺序推导与
 *       SortOrder 复用判断使用。
 *   <li>{@link #project}/{@link #projectStrict} 是分区裁剪的核心：inclusive 投影保证不漏数据， strict
 *       投影保证不误删数据，两者构成完整的过滤边界。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.PartitionSpec} 持有，被 expressions 模块的 {@link BoundPredicate}
 * 投影逻辑、core 的扫描规划与各引擎下推使用。
 *
 * @param <S> 源值的 Java 类型
 * @param <T> 变换后值的 Java 类型
 */
public interface Transform<S, T> extends Serializable {
  /**
   * 将源值变换为对应的分区值（已弃用）。
   *
   * <p>设计要点：旧 API 直接对单个值变换，无法感知源类型；新实现应通过 {@link #bind(Type)} 获取带类型的 {@link SerializableFunction}
   * 再调用其 apply。本默认实现直接抛出异常， 强制子类按新约定实现 bind。
   *
   * @param value 源值
   * @return 变换后的分区值
   * @deprecated 使用 {@link #bind(Type)} 替代；将在 2.0.0 移除
   */
  @Deprecated
  default T apply(S value) {
    throw new UnsupportedOperationException(
        "apply(value) is deprecated, use bind(Type).apply(value)");
  }

  /**
   * 把本变换绑定到具体源类型，返回可序列化的变换函数。
   *
   * <p>设计要点：绑定后函数可避免每次 apply 都做类型分派，且能被序列化进 PartitionSpec 元数据中持久化。默认实现抛出异常，由具体变换类（如 {@link
   * Bucket}、{@link Truncate}）覆盖。
   *
   * @param type 源字段的 Iceberg {@link Type}
   * @return 可对给定类型值执行变换的 {@link SerializableFunction}
   */
  default SerializableFunction<S, T> bind(Type type) {
    throw new UnsupportedOperationException("bind is not implemented");
  }

  /**
   * 判断本变换能否应用于给定的源类型。
   *
   * <p>设计要点：不同变换支持的类型集合不同（如 hour 仅支持 timestamp），调用方在构造 PartitionSpec 时通过本方法做前置校验。
   *
   * @param type 待校验的类型
   * @return 若本变换可应用于该类型返回 true，否则 false
   */
  boolean canTransform(Type type);

  /**
   * 返回本变换在给定源类型下产出的结果类型。
   *
   * <p>设计要点：分区列的类型由变换决定（如 bucket 结果恒为 int，truncate 结果与源同类型）。
   *
   * @param sourceType 源类型
   * @return 变换产出的结果类型
   */
  Type getResultType(Type sourceType);

  /**
   * 本变换是否保持值的顺序（单调性）。
   *
   * <p>设计要点：当 a &lt; b 蕴含 apply(a) &lt;= apply(b) 时为 true。保持顺序的变换 可用于排序下推，避免数据全排序后再分桶。
   *
   * @return 若保持顺序返回 true，否则 false
   */
  default boolean preservesOrder() {
    return false;
  }

  /**
   * 判断按本变换结果排序是否等价于按另一变换结果排序。
   *
   * <p>逻辑：例如按 day(ts) 排序的结果同时满足按 month(ts) 或 year(ts) 排序； 但不满足按 hour(ts) 或 identity(ts) 排序。默认按
   * equals 判断，时间粒度变换会 覆盖以表达更细粒度包含粗粒度的语义。
   *
   * @param other 另一个变换
   * @return 若按本变换排序可满足按 other 变换排序返回 true
   */
  default boolean satisfiesOrderOf(Transform<?, ?> other) {
    return equals(other);
  }

  /**
   * 把源字段上的谓词投影为分区值上的"包含型"（inclusive）谓词。
   *
   * <p>设计要点：保证若 pred(v) 为 true，则 projected(apply(v)) 必为 true， 即不会漏掉任何可能命中的分区，用于扫描时挑选候选分区。
   *
   * @param name 分区列字段名
   * @param predicate 源字段上的已绑定谓词
   * @return 分区值上的未绑定谓词；不可投影时返回 null
   */
  UnboundPredicate<T> project(String name, BoundPredicate<S> predicate);

  /**
   * 把源字段上的谓词投影为分区值上的"严格型"（strict）谓词。
   *
   * <p>设计要点：保证若 strict(apply(v)) 为 true，则 pred(v) 必为 true， 即凡是 strict
   * 谓词为真的分区，其所有数据都满足原谓词，可整体跳过文件内容过滤。
   *
   * @param name 分区列字段名
   * @param predicate 源字段上的已绑定谓词
   * @return 分区值上的未绑定谓词；不可投影时返回 null
   */
  UnboundPredicate<T> projectStrict(String name, BoundPredicate<S> predicate);

  /**
   * 判断本变换是否为 identity 变换。
   *
   * @return 若为 identity 变换返回 true，否则 false
   */
  default boolean isIdentity() {
    return false;
  }

  /**
   * 判断本变换是否为 void 变换（恒为 null）。
   *
   * @return 若为 void 变换返回 true，否则 false
   */
  default boolean isVoid() {
    return false;
  }

  /**
   * 返回变换值的人类可读字符串表示（已弃用，不感知类型）。
   *
   * <p>设计要点：对 ByteBuffer/byte[] 做 base64 编码，其他类型调用 toString。null 返回 "null"。 新代码应使用 {@link
   * #toHumanString(Type, Object)}，因为它能按类型正确格式化日期时间等。
   *
   * @param value 变换后的值
   * @return 人类可读字符串
   * @deprecated 使用 {@link #toHumanString(Type, Object)} 替代；将在 2.0.0 移除
   */
  @Deprecated
  default String toHumanString(T value) {
    if (value instanceof ByteBuffer) {
      return TransformUtil.base64encode(((ByteBuffer) value).duplicate());
    } else if (value instanceof byte[]) {
      return TransformUtil.base64encode(ByteBuffer.wrap((byte[]) value));
    } else {
      return String.valueOf(value);
    }
  }

  /**
   * 按目标类型把变换值格式化为人类可读字符串。
   *
   * <p>逻辑：null 统一返回 "null"；DATE/TIME/TIMESTAMP 调用 {@link TransformUtil} 对应方法 转为 ISO 字符串；TIMESTAMP
   * 根据 shouldAdjustToUTC 区分带/不带时区； FIXED/BINARY 走 base64 编码；其余类型直接 toString。
   *
   * @param type 变换产出类型
   * @param value 变换后的值
   * @return 人类可读字符串
   */
  default String toHumanString(Type type, T value) {
    if (value == null) {
      return "null";
    }

    switch (type.typeId()) {
      case DATE:
        return TransformUtil.humanDay((Integer) value);
      case TIME:
        return TransformUtil.humanTime((Long) value);
      case TIMESTAMP:
        if (((Types.TimestampType) type).shouldAdjustToUTC()) {
          return TransformUtil.humanTimestampWithZone((Long) value);
        } else {
          return TransformUtil.humanTimestampWithoutZone((Long) value);
        }
      case FIXED:
      case BINARY:
        if (value instanceof ByteBuffer) {
          return TransformUtil.base64encode(((ByteBuffer) value).duplicate());
        } else if (value instanceof byte[]) {
          return TransformUtil.base64encode(ByteBuffer.wrap((byte[]) value));
        } else {
          throw new UnsupportedOperationException("Unsupported binary type: " + value.getClass());
        }
      default:
        return value.toString();
    }
  }

  /**
   * 返回本变换的去重名称，供 PartitionSpec 构造器判断是否对同一源字段重复添加相似变换。
   *
   * <p>设计要点：默认返回 toString()，部分变换可能覆盖以表达"同源同变换只允许一次"。
   *
   * @return 用于去重的名称
   */
  default String dedupName() {
    return toString();
  }
}
