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
package org.apache.iceberg.orc;

import java.util.List;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.orc.TypeDescription;

/**
 * 清除 ORC schema 树上 Iceberg id 属性的访问器。
 *
 * <p>所属模块：iceberg-orc。用于生成不带 Iceberg id 的“干净”ORC schema， 供需要与原生 ORC 工具交互的场景使用。
 *
 * <p>职责：遍历 ORC TypeDescription 树，重建新的 TypeDescription（clone 后移除 id 属性）。
 *
 * <p>设计意图：primitive 节点 clone 后调 removeIcebergAttributes 清除 id， struct/list/map 重建新的复合类型，保证不修改原对象。
 *
 * <p>上下游关系：被 {@link ORCSchemaUtil#removeIds} 调用。
 */
class RemoveIds extends OrcSchemaVisitor<TypeDescription> {

  @Override
  public TypeDescription record(
      TypeDescription record, List<String> names, List<TypeDescription> fields) {
    Preconditions.checkArgument(names.size() == fields.size(), "All fields must have names.");
    TypeDescription struct = TypeDescription.createStruct();

    for (int i = 0; i < fields.size(); i++) {
      struct.addField(names.get(i), fields.get(i));
    }
    return struct;
  }

  @Override
  public TypeDescription list(TypeDescription array, TypeDescription element) {
    return TypeDescription.createList(element);
  }

  @Override
  public TypeDescription map(TypeDescription map, TypeDescription key, TypeDescription value) {
    return TypeDescription.createMap(key, value);
  }

  @Override
  public TypeDescription primitive(TypeDescription primitive) {
    return removeIcebergAttributes(primitive.clone());
  }

  /** 移除 ORC TypeDescription 上的 Iceberg id 属性。 */
  private static TypeDescription removeIcebergAttributes(TypeDescription orcType) {
    orcType.removeAttribute(ORCSchemaUtil.ICEBERG_ID_ATTRIBUTE);
    return orcType;
  }
}
