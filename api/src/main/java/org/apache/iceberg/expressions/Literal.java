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
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.UUID;
import org.apache.iceberg.types.Type;

/**
 * 表达式谓词中的字面量：包裹一个固定值，并支持按目标类型转换与比较。
 *
 * <p>所属模块：iceberg-api（表达式体系中“值侧”的抽象；与 {@link Term} 配对构成谓词）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>以类型安全的方式持有谓词中的常量值（{@link #value()}）。
 *   <li>提供 {@link #to(Type)}：在谓词绑定到具体列类型时把字面量转换为列类型， 转换不支持时返回 null，越界时返回 {@link Literals#aboveMax}
 *       / {@link Literals#belowMin} 以驱动谓词化简。
 *   <li>提供 {@link #comparator()} 用于在求值时比较字段值与字面量。
 *   <li>提供 {@link #toByteBuffer()} 按规范单值序列化字面量。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>to 转换故意比 cast 更窄，只覆盖常见“易错替代”（如 34 与 34L）与避免暴露具体类 （如日期）的场景，确保转换语义可控。
 *   <li>越界哨兵值（aboveMax/belowMin）让绑定器可以把不可满足的谓词直接化简为 alwaysTrue/alwaysFalse，从源头避免无效求值。
 * </ul>
 *
 * <p>上下游关系：由 {@link UnboundPredicate} 持有；在 {@link Binder} 绑定时调用 to 完成类型 对齐；具体实现集中在 {@link
 * Literals}。
 *
 * @param <T> 字面量所包裹值的 Java 类型
 */
public interface Literal<T> extends Serializable {
  /** 创建 boolean 字面量。 */
  static Literal<Boolean> of(boolean value) {
    return new Literals.BooleanLiteral(value);
  }

  /** 创建 int 字面量。 */
  static Literal<Integer> of(int value) {
    return new Literals.IntegerLiteral(value);
  }

  /** 创建 long 字面量。 */
  static Literal<Long> of(long value) {
    return new Literals.LongLiteral(value);
  }

  /** 创建 float 字面量。 */
  static Literal<Float> of(float value) {
    return new Literals.FloatLiteral(value);
  }

  /** 创建 double 字面量。 */
  static Literal<Double> of(double value) {
    return new Literals.DoubleLiteral(value);
  }

  /** 创建 CharSequence 字面量。 */
  static Literal<CharSequence> of(CharSequence value) {
    return new Literals.StringLiteral(value);
  }

  /** 创建 UUID 字面量。 */
  static Literal<UUID> of(UUID value) {
    return new Literals.UUIDLiteral(value);
  }

  /** 创建定长字节字面量（由 byte[] 包装为 ByteBuffer）。 */
  static Literal<ByteBuffer> of(byte[] value) {
    return new Literals.FixedLiteral(ByteBuffer.wrap(value));
  }

  /** 创建变长二进制字面量。 */
  static Literal<ByteBuffer> of(ByteBuffer value) {
    return new Literals.BinaryLiteral(value);
  }

  /** 创建 BigDecimal 字面量。 */
  static Literal<BigDecimal> of(BigDecimal value) {
    return new Literals.DecimalLiteral(value);
  }

  /** 返回本字面量包裹的值。 */
  T value();

  /**
   * 把本字面量转换为目标类型对应的字面量。
   *
   * <p>语义：谓词绑定到具体列时，字面量需对齐列类型。该转换比 cast 更窄，仅覆盖常见 易错替代（如 34 与 34L）与避免暴露具体类的场景（如日期）。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>转换不支持时返回 null。
   *   <li>当目标类型窄于源类型且值越界时，返回 {@link Literals#aboveMax} 或 {@link Literals#belowMin}，使包含该字面量的谓词可被化简
   *       （例如 a &lt; Integer.MAX_VALUE+1 转换为 int 时得到 aboveMax， 进而化简为 {@link
   *       Expressions#alwaysTrue()}）。
   * </ul>
   *
   * @param type 目标原始 {@link Type}
   * @param <X> 转换后字面量的 Java 类型
   * @return 转换后的字面量，或 null（不可转换）
   */
  <X> Literal<X> to(Type type);

  /**
   * 返回适用于本字面量值类型的 {@link Comparator}。
   *
   * @return 值比较器
   */
  Comparator<T> comparator();

  /**
   * 按 Iceberg 表规范的单值序列化格式把字面量值序列化为二进制。
   *
   * <p>默认抛出 {@link UnsupportedOperationException}，由支持二进制序列化的具体实现覆盖。
   *
   * @return 包含序列化值的 ByteBuffer
   */
  default ByteBuffer toByteBuffer() {
    throw new UnsupportedOperationException("toByteBuffer is not supported");
  }
}
