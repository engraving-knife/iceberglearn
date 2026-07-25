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
package org.apache.iceberg.delta;

import io.delta.standalone.types.ArrayType;
import io.delta.standalone.types.DataType;
import io.delta.standalone.types.MapType;
import io.delta.standalone.types.StructField;
import io.delta.standalone.types.StructType;
import java.util.List;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;

/**
 * Delta Lake 数据类型访问者抽象基类（访问者模式）。
 *
 * <p>所属模块：iceberg-delta-lake（Delta Lake 表迁移到 Iceberg 的支持模块，位于迁移工具链最底层）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>对 Delta Lake 的 {@link DataType} 类型树进行递归遍历。
 *   <li>针对 Struct/Map/Array/原子类型四种节点，分别回调子类实现的对应方法。
 *   <li>把"类型结构遍历"与"类型转换/收集逻辑"解耦，便于不同的访问者复用同一套遍历骨架。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>访问者模式：Delta Lake 的 {@link DataType} 类层级由第三方依赖定义，无法在其内部添加方法。 通过外部 Visitor 既能扩展行为又避免修改既有类层级。
 *   <li>泛型返回值 {@code T}：使同一遍历骨架既可用于"转换类型"（如 {@link DeltaLakeTypeToType} 返回 Iceberg {@link
 *       org.apache.iceberg.types.Type}），也可用于其他用途。
 *   <li>递归自驱动：{@link #visit(DataType, DeltaLakeDataTypeVisitor)} 是静态入口，内部递归调用自身，
 *       子类只需实现叶子回调，无需关心遍历顺序。
 * </ul>
 *
 * <p>上下游关系：被 {@link DeltaLakeTypeToType} 等具体访问者使用，进而被 {@link BaseSnapshotDeltaLakeTableAction}
 * 在迁移表时调用以完成 schema 转换。
 *
 * @param <T> 访问者各回调方法的返回值类型
 */
abstract class DeltaLakeDataTypeVisitor<T> {
  /**
   * 对给定的 Delta Lake 类型发起访问（遍历入口）。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>若为 {@link StructType}：先递归访问每个字段的数据类型并收集结果，再回调 {@link #struct(StructType, List)}。
   *   <li>若为 {@link MapType}：分别递归访问 key 与 value 类型，再回调 {@link #map(MapType, Object, Object)}。
   *   <li>若为 {@link ArrayType}：递归访问元素类型，再回调 {@link #array(ArrayType, Object)}。
   *   <li>其余视为原子类型，直接回调 {@link #atomic(DataType)}。
   * </ol>
   *
   * @param type 待访问的 Delta Lake 类型，不可为 null
   * @param visitor 实现了各回调方法的具体访问者
   * @param <T> 访问者返回值类型
   * @return 访问者回调产生的最终结果
   */
  public static <T> T visit(DataType type, DeltaLakeDataTypeVisitor<T> visitor) {
    if (type instanceof StructType) {
      StructField[] fields = ((StructType) type).getFields();
      List<T> fieldResults = Lists.newArrayListWithExpectedSize(fields.length);

      for (StructField field : fields) {
        fieldResults.add(visitor.field(field, visit(field.getDataType(), visitor)));
      }

      return visitor.struct((StructType) type, fieldResults);

    } else if (type instanceof MapType) {
      return visitor.map(
          (MapType) type,
          visit(((MapType) type).getKeyType(), visitor),
          visit(((MapType) type).getValueType(), visitor));

    } else if (type instanceof ArrayType) {
      return visitor.array((ArrayType) type, visit(((ArrayType) type).getElementType(), visitor));

    } else {
      return visitor.atomic(type);
    }
  }

  /**
   * 处理结构体类型（Struct）的回调。
   *
   * @param struct Delta Lake 的 Struct 类型节点
   * @param fieldResults 各字段经递归访问后得到的结果列表，顺序与 {@code struct.getFields()} 一致
   * @return 该结构体对应的访问结果
   */
  public abstract T struct(StructType struct, List<T> fieldResults);

  /**
   * 处理结构体单个字段的回调。
   *
   * <p>本骨架在遍历字段时会先递归访问字段类型，再调用本方法，子类可在此处对字段本身 （如字段名、注释）做处理，或直接返回字段类型的结果。
   *
   * @param field Delta Lake 的字段定义
   * @param typeResult 该字段数据类型经递归访问后的结果
   * @return 该字段对应的访问结果
   */
  public abstract T field(StructField field, T typeResult);

  /**
   * 处理数组类型（Array）的回调。
   *
   * @param array Delta Lake 的 Array 类型节点
   * @param elementResult 元素类型经递归访问后的结果
   * @return 该数组对应的访问结果
   */
  public abstract T array(ArrayType array, T elementResult);

  /**
   * 处理映射类型（Map）的回调。
   *
   * @param map Delta Lake 的 Map 类型节点
   * @param keyResult key 类型经递归访问后的结果
   * @param valueResult value 类型经递归访问后的结果
   * @return 该映射对应的访问结果
   */
  public abstract T map(MapType map, T keyResult, T valueResult);

  /**
   * 处理原子类型（非 Struct/Map/Array）的回调。
   *
   * @param atomic Delta Lake 的原子类型（如 IntegerType、StringType 等）
   * @return 该原子类型对应的访问结果
   */
  public abstract T atomic(DataType atomic);
}
