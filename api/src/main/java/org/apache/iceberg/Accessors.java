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
package org.apache.iceberg;

import java.util.List;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.TypeUtil;
import org.apache.iceberg.types.Types;

/**
 * 嵌套字段访问器工厂与实现集合。
 *
 * <p>所属模块：iceberg-api（最顶层的公共接口模块，定义表/扫描/元数据等核心抽象）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为 {@link Schema} 中每个字段（含嵌套字段）构建按位置（position）访问的 {@link Accessor}，避免每次访问都按字段名查找。
 *   <li>提供 {@link PositionAccessor}、{@code Position2Accessor}、{@code Position3Accessor}
 *       三种针对常见深度（1/2/3）的特化实现，以及针对更深嵌套或可空层的 {@link WrappedPositionAccessor} 递归实现。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>特化优化：对于形如
 *       <pre>
 *       root
 *        |-- a: struct (nullable = false)
 *        |    |-- b: struct (nullable = false)
 *        |        | -- c: string (containsNull = false)
 *       </pre>
 *       的嵌套结构，访问深度 1/2/3 的字段使用对应特化类，可一次性以 {@code row.get(p0, StructLike.class).get(p1,
 *       StructLike.class).get(p2, javaClass)} 完成，避免方法调用与对象分配开销。深度大于 3 时退回到 {@link
 *       WrappedPositionAccessor} 的递归访问。
 *   <li>对可空（optional）嵌套层使用 WrappedPositionAccessor，以处理中间层为 null 的情形。
 * </ul>
 *
 * <p>上下游关系：由 {@link TypeUtil#visit} 驱动的 {@link BuildPositionAccessors} 在 schema 遍历时构建访问器映射；被 core
 * 模块的扫描/读取路径用于按字段 ID 取值。
 */
public class Accessors {
  private Accessors() {}

  /**
   * 将单层位置访问器转换为其所记录的字段位置。
   *
   * @param accessor 待转换的访问器
   * @return 该访问器对应的字段位置
   * @throws IllegalArgumentException 若访问器是嵌套访问器（非单层 PositionAccessor）
   */
  public static Integer toPosition(Accessor<StructLike> accessor) {
    if (accessor instanceof PositionAccessor) {
      return ((PositionAccessor) accessor).position();
    }
    throw new IllegalArgumentException("Cannot convert nested accessor to position");
  }

  /**
   * 为给定 schema 构建字段 ID 到访问器的映射。
   *
   * <p>逻辑：通过 {@link TypeUtil#visit} 驱动 {@link BuildPositionAccessors} 遍历 schema，
   * 对每个字段（含嵌套字段）生成对应深度的位置访问器并按字段 ID 收集到 Map 中。
   *
   * @param schema 表 schema
   * @return 字段 ID 到 {@link Accessor} 的映射
   */
  static Map<Integer, Accessor<StructLike>> forSchema(Schema schema) {
    return TypeUtil.visit(schema, new BuildPositionAccessors());
  }

  /**
   * 单层位置访问器：直接按位置从 {@link StructLike} 取值。
   *
   * <p>设计要点：缓存字段的 Java 类型对应的 javaClass，避免每次 get 时重复查询。
   */
  private static class PositionAccessor implements Accessor<StructLike> {
    private final int position;
    private final Type type;
    private final Class<?> javaClass;

    PositionAccessor(int pos, Type type) {
      this.position = pos;
      this.type = type;
      this.javaClass = type.typeId().javaClass();
    }

    @Override
    public Object get(StructLike row) {
      return row.get(position, javaClass);
    }

    @Override
    public Type type() {
      return type;
    }

    public int position() {
      return position;
    }

    public Class<?> javaClass() {
      return javaClass;
    }

    @Override
    public String toString() {
      return "Accessor(positions=[" + position + "], type=" + type + ")";
    }
  }

  /**
   * 两层位置访问器：从根 struct 取第一层，再取第二层目标字段。
   *
   * <p>设计要点：把外层位置和内层 PositionAccessor 的位置都展平为字段，使 get 操作 不再经过中间对象方法分派。
   */
  private static class Position2Accessor implements Accessor<StructLike> {
    private final int p0;
    private final int p1;
    private final Type type;
    private final Class<?> javaClass;

    Position2Accessor(int pos, PositionAccessor wrapped) {
      this.p0 = pos;
      this.p1 = wrapped.position();
      this.type = wrapped.type();
      this.javaClass = wrapped.javaClass();
    }

    @Override
    public Object get(StructLike row) {
      return row.get(p0, StructLike.class).get(p1, javaClass);
    }

    @Override
    public Type type() {
      return type;
    }

    public Class<?> javaClass() {
      return javaClass;
    }

    @Override
    public String toString() {
      return "Accessor(positions=[" + p0 + ", " + p1 + "], type=" + type + ")";
    }
  }

  /**
   * 三层位置访问器：从根 struct 连续取三层后取目标字段。
   *
   * <p>设计要点：与 {@code Position2Accessor} 类似，进一步展平第三层位置，是嵌套访问的 性能甜点（绝大多数业务字段深度不超过 3）。
   */
  private static class Position3Accessor implements Accessor<StructLike> {
    private final int p0;
    private final int p1;
    private final int p2;
    private final Type type;
    private final Class<?> javaClass;

    Position3Accessor(int pos, Position2Accessor wrapped) {
      this.p0 = pos;
      this.p1 = wrapped.p0;
      this.p2 = wrapped.p1;
      this.type = wrapped.type();
      this.javaClass = wrapped.javaClass();
    }

    @Override
    public Object get(StructLike row) {
      return row.get(p0, StructLike.class).get(p1, StructLike.class).get(p2, javaClass);
    }

    @Override
    public Type type() {
      return type;
    }

    @Override
    public String toString() {
      return "Accessor(positions=[" + p0 + ", " + p1 + ", " + p2 + "], type=" + type + ")";
    }
  }

  /**
   * 包装型位置访问器：在指定位置取内层 struct，再委托给被包装的访问器取值。
   *
   * <p>设计要点：当内层可能为 null（即字段 optional）或嵌套深度超过 3 时使用本类， 在 get 时先取出内层 struct 并判空，避免 NPE。
   */
  private static class WrappedPositionAccessor implements Accessor<StructLike> {
    private final int position;
    private final Accessor<StructLike> accessor;

    WrappedPositionAccessor(int pos, Accessor<StructLike> accessor) {
      this.position = pos;
      this.accessor = accessor;
    }

    @Override
    public Object get(StructLike row) {
      StructLike inner = row.get(position, StructLike.class);
      if (inner != null) {
        return accessor.get(inner);
      }
      return null;
    }

    @Override
    public Type type() {
      return accessor.type();
    }

    @Override
    public String toString() {
      return "WrappedAccessor(position=" + position + ", wrapped=" + accessor + ")";
    }
  }

  /** 创建单层位置访问器。 */
  private static Accessor<StructLike> newAccessor(int pos, Type type) {
    return new PositionAccessor(pos, type);
  }

  /**
   * 在已有访问器外层再包一层位置，按字段可空性与内层访问器类型选择最优实现。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>若该层字段 optional，使用 {@link WrappedPositionAccessor} 以处理中间层为 null；
   *   <li>否则若内层是 {@link PositionAccessor}，升级为 {@code Position2Accessor}；
   *   <li>若内层是 {@code Position2Accessor}，升级为 {@code Position3Accessor}；
   *   <li>深度已超过 3，则退回到 {@link WrappedPositionAccessor} 递归访问。
   * </ul>
   *
   * @param pos 当前层在父 struct 中的位置
   * @param isOptional 当前层字段是否可空
   * @param accessor 内层字段访问器
   * @return 包装后的访问器
   */
  private static Accessor<StructLike> newAccessor(
      int pos, boolean isOptional, Accessor<StructLike> accessor) {
    if (isOptional) {
      // the wrapped position handles null layers
      return new WrappedPositionAccessor(pos, accessor);
    } else if (accessor.getClass() == PositionAccessor.class) {
      return new Position2Accessor(pos, (PositionAccessor) accessor);
    } else if (accessor instanceof Position2Accessor) {
      return new Position3Accessor(pos, (Position2Accessor) accessor);
    } else {
      return new WrappedPositionAccessor(pos, accessor);
    }
  }

  /**
   * Schema 访问器：遍历 schema 并为每个字段（含嵌套字段）构建位置访问器。
   *
   * <p>设计意图：利用 {@link TypeUtil.SchemaVisitor} 的自顶向下遍历，将子 struct 已构建的 访问器向外层传递并叠加当前位置，最终汇聚到根 schema
   * 的字段 ID -> 访问器映射。
   */
  private static class BuildPositionAccessors
      extends TypeUtil.SchemaVisitor<Map<Integer, Accessor<StructLike>>> {

    @Override
    public Map<Integer, Accessor<StructLike>> schema(
        Schema schema, Map<Integer, Accessor<StructLike>> structResult) {
      return structResult;
    }

    /**
     * 处理 struct 节点：合并各子字段的结果，并为每个嵌套字段叠加当前位置生成新访问器。
     *
     * <p>逻辑：遍历 struct 的每个字段，若该字段有来自下层的结果（说明是 struct 类型且 已生成嵌套访问器），则把这些嵌套访问器外层再包一层当前位置；同时为本字段自身
     * 添加一个直接按位置取值的访问器。
     */
    @Override
    public Map<Integer, Accessor<StructLike>> struct(
        Types.StructType struct, List<Map<Integer, Accessor<StructLike>>> fieldResults) {
      Map<Integer, Accessor<StructLike>> accessors = Maps.newHashMap();
      List<Types.NestedField> fields = struct.fields();
      for (int i = 0; i < fieldResults.size(); i += 1) {
        Types.NestedField field = fields.get(i);
        Map<Integer, Accessor<StructLike>> result = fieldResults.get(i);
        if (result != null) {
          // Add accessors for nested fields.
          for (Map.Entry<Integer, Accessor<StructLike>> entry : result.entrySet()) {
            accessors.put(entry.getKey(), newAccessor(i, field.isOptional(), entry.getValue()));
          }
        }

        // Add an accessor for this field as an Object (may or may not be primitive).
        accessors.put(field.fieldId(), newAccessor(i, field.type()));
      }

      return accessors;
    }

    @Override
    public Map<Integer, Accessor<StructLike>> field(
        Types.NestedField field, Map<Integer, Accessor<StructLike>> fieldResult) {
      return fieldResult;
    }
  }
}
