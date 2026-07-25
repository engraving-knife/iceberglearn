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
package org.apache.iceberg.types;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.iceberg.StructLike;

/**
 * Iceberg 类型系统根接口：定义表 schema 中所有数据类型的公共契约。
 *
 * <p>所属模块：iceberg-api（类型系统是 schema、分区、表达式等模块的基础，被 core 实现及各引擎集成模块依赖）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>通过 {@link TypeID} 枚举标识所有支持的类型（布尔、整数、长整型、浮点、双精度、日期、时间、
 *       时间戳、字符串、UUID、定长二进制、变长二进制、Decimal、Struct、List、Map）。
 *   <li>提供类型分类判断（isPrimitiveType/isNestedType/isStructType/isListType/isMapType）
 *       与安全转型方法（asPrimitiveType/asStructType/asListType/asMapType/asNestedType）。
 *   <li>定义 {@link PrimitiveType} 与 {@link NestedType} 两个抽象基类，分别承载原子类型与 复合类型的公共行为。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>类型系统采用接口 + 抽象基类 + 具体内部类的层次结构，{@code Type} 仅定义契约， 具体实现集中在 {@link Types}，避免类型定义分散。
 *   <li>{@link TypeID} 同时绑定每个类型对应的 Java 表示类（javaClass），便于在运行时做类型校验与 值转换（如 {@link Conversions}）。
 *   <li>转型方法采用默认方法实现：匹配类型返回自身，不匹配抛 {@link IllegalArgumentException}， 避免 instanceof + 强转的样板代码。
 *   <li>{@link PrimitiveType#writeReplace} 通过 {@link PrimitiveHolder} 在序列化时用字符串替代实例，
 *       保证单例语义跨序列化保留（反序列化后仍指向同一单例）。
 * </ul>
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.Schema}、{@link org.apache.iceberg.types.Types.NestedField}、
 * {@link org.apache.iceberg.PartitionSpec}、表达式系统（{@link org.apache.iceberg.expressions}）等依赖； {@link
 * TypeUtil} 提供类型遍历与改写工具。
 */
public interface Type extends Serializable {
  /**
   * 类型标识枚举：每个值对应一种 Iceberg 支持的数据类型，并绑定其 Java 表示类。
   *
   * <p>设计要点：javaClass 用于运行期类型校验与值转换，例如 {@link Conversions} 依据它判断 字面量是否可赋给某类型。
   */
  enum TypeID {
    BOOLEAN(Boolean.class),
    INTEGER(Integer.class),
    LONG(Long.class),
    FLOAT(Float.class),
    DOUBLE(Double.class),
    DATE(Integer.class),
    TIME(Long.class),
    TIMESTAMP(Long.class),
    STRING(CharSequence.class),
    UUID(java.util.UUID.class),
    FIXED(ByteBuffer.class),
    BINARY(ByteBuffer.class),
    DECIMAL(BigDecimal.class),
    STRUCT(StructLike.class),
    LIST(List.class),
    MAP(Map.class);

    private final Class<?> javaClass;

    TypeID(Class<?> javaClass) {
      this.javaClass = javaClass;
    }

    /**
     * 返回该类型对应的 Java 表示类。
     *
     * @return Java 表示类
     */
    public Class<?> javaClass() {
      return javaClass;
    }
  }

  /**
   * 返回本类型的 {@link TypeID}。
   *
   * @return 类型标识
   */
  TypeID typeId();

  /**
   * 是否为原始类型（非 struct/list/map）。默认 false，由 {@link PrimitiveType} 覆盖为 true。
   *
   * @return 原始类型返回 true
   */
  default boolean isPrimitiveType() {
    return false;
  }

  /**
   * 转型为 {@link PrimitiveType}，非原始类型抛异常。
   *
   * @return 本类型自身
   * @throws IllegalArgumentException 若非原始类型
   */
  default PrimitiveType asPrimitiveType() {
    throw new IllegalArgumentException("Not a primitive type: " + this);
  }

  /**
   * 转型为 {@link Types.StructType}，非 struct 类型抛异常。
   *
   * @return 本类型自身
   * @throws IllegalArgumentException 若非 struct 类型
   */
  default Types.StructType asStructType() {
    throw new IllegalArgumentException("Not a struct type: " + this);
  }

  /**
   * 转型为 {@link Types.ListType}，非 list 类型抛异常。
   *
   * @return 本类型自身
   * @throws IllegalArgumentException 若非 list 类型
   */
  default Types.ListType asListType() {
    throw new IllegalArgumentException("Not a list type: " + this);
  }

  /**
   * 转型为 {@link Types.MapType}，非 map 类型抛异常。
   *
   * @return 本类型自身
   * @throws IllegalArgumentException 若非 map 类型
   */
  default Types.MapType asMapType() {
    throw new IllegalArgumentException("Not a map type: " + this);
  }

  /**
   * 是否为嵌套类型（struct/list/map）。默认 false，由 {@link NestedType} 覆盖为 true。
   *
   * @return 嵌套类型返回 true
   */
  default boolean isNestedType() {
    return false;
  }

  /**
   * 是否为 struct 类型。默认 false，由 {@link Types.StructType} 覆盖为 true。
   *
   * @return struct 类型返回 true
   */
  default boolean isStructType() {
    return false;
  }

  /**
   * 是否为 list 类型。默认 false，由 {@link Types.ListType} 覆盖为 true。
   *
   * @return list 类型返回 true
   */
  default boolean isListType() {
    return false;
  }

  /**
   * 是否为 map 类型。默认 false，由 {@link Types.MapType} 覆盖为 true。
   *
   * @return map 类型返回 true
   */
  default boolean isMapType() {
    return false;
  }

  /**
   * 转型为 {@link NestedType}，非嵌套类型抛异常。
   *
   * @return 本类型自身
   * @throws IllegalArgumentException 若非嵌套类型
   */
  default NestedType asNestedType() {
    throw new IllegalArgumentException("Not a nested type: " + this);
  }

  /**
   * 原始类型抽象基类：所有原子类型（boolean/int/long/string/decimal 等）的父类。
   *
   * <p>设计要点：提供 isPrimitiveType/asPrimitiveType 的 true 实现；equals/hashCode 基于 {@link PrimitiveType}
   * 类与 {@link #typeId()}，故同 typeId 的原始类型视为相等 （但 {@link Types.TimestampType}、{@link
   * Types.FixedType}、{@link Types.DecimalType} 因带参数而自行覆盖 equals/hashCode）。序列化时用 {@link
   * PrimitiveHolder} 替代以保持单例语义。
   */
  abstract class PrimitiveType implements Type {
    @Override
    public boolean isPrimitiveType() {
      return true;
    }

    @Override
    public PrimitiveType asPrimitiveType() {
      return this;
    }

    /**
     * 序列化替换：用 {@link PrimitiveHolder} 包装本类型的字符串表示，反序列化时还原为单例。
     *
     * @return 序列化代理
     * @throws ObjectStreamException 不会抛出
     */
    Object writeReplace() throws ObjectStreamException {
      return new PrimitiveHolder(toString());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof PrimitiveType)) {
        return false;
      }

      PrimitiveType that = (PrimitiveType) o;
      return typeId() == that.typeId();
    }

    @Override
    public int hashCode() {
      return Objects.hash(PrimitiveType.class, typeId());
    }
  }

  /**
   * 嵌套类型抽象基类：struct/list/map 的父类，定义按字段名/字段 ID 访问子字段的契约。
   *
   * <p>设计要点：把 struct/list/map 统一抽象为"字段集合"，list 的元素与 map 的 key/value 也建模为带 ID 的 {@link
   * Types.NestedField}，便于在 schema 演进、列裁剪、ID 重分配等 场景统一处理。
   */
  abstract class NestedType implements Type {
    @Override
    public boolean isNestedType() {
      return true;
    }

    @Override
    public NestedType asNestedType() {
      return this;
    }

    /**
     * 返回本嵌套类型的所有子字段列表。
     *
     * @return 子字段列表
     */
    public abstract List<Types.NestedField> fields();

    /**
     * 按字段名查询字段类型。
     *
     * @param name 字段名
     * @return 字段类型；不存在返回 null
     */
    public abstract Type fieldType(String name);

    /**
     * 按字段 ID 查询子字段。
     *
     * @param id 字段 ID
     * @return 子字段；不存在返回 null
     */
    public abstract Types.NestedField field(int id);
  }
}
