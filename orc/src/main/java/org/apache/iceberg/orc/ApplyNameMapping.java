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
import org.apache.iceberg.mapping.MappedField;
import org.apache.iceberg.mapping.NameMapping;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.orc.TypeDescription;

/**
 * 基于 {@link NameMapping} 为 ORC schema 树各节点设置 Iceberg 字段 id 的访问器。
 *
 * <p>所属模块：iceberg-orc。当 ORC 文件缺少 Iceberg id 属性时，可通过 NameMapping （名称→id 的映射表）回溯并补全 id，使文件能被 Iceberg
 * 读取。
 *
 * <p>职责：遍历 ORC TypeDescription 树，按当前路径在 NameMapping 中查找对应 MappedField， 命中则把 id 写入 TypeDescription 的
 * attribute（{@value ORCSchemaUtil#ICEBERG_ID_ATTRIBUTE}）。
 *
 * <p>设计意图：继承 {@link OrcSchemaVisitor}，返回新的 TypeDescription 树（primitive 节点 clone 后再设属性，避免修改原对象）；null
 * 子节点会被 record 过滤掉，实现投影裁剪。
 *
 * <p>上下游关系：被 {@link ORCSchemaUtil} 在需要补全 id 时调用。
 */
class ApplyNameMapping extends OrcSchemaVisitor<TypeDescription> {
  private final NameMapping nameMapping;

  ApplyNameMapping(NameMapping nameMapping) {
    this.nameMapping = nameMapping;
  }

  @Override
  public String elementName() {
    return "element";
  }

  @Override
  public String keyName() {
    return "key";
  }

  @Override
  public String valueName() {
    return "value";
  }

  /**
   * 若 MappedField 非空，将 Iceberg id 设为 TypeDescription 的属性。
   *
   * @param type 目标 TypeDescription
   * @param mappedField 名称映射结果（可能为 null）
   * @return 设好属性的 type
   */
  TypeDescription setId(TypeDescription type, MappedField mappedField) {
    if (mappedField != null) {
      type.setAttribute(ORCSchemaUtil.ICEBERG_ID_ATTRIBUTE, mappedField.id().toString());
    }
    return type;
  }

  @Override
  /**
   * 处理 struct 节点：查找映射 id，构建新的 struct TypeDescription。
   *
   * <p>逻辑：先按当前路径在 NameMapping 中查找 MappedField；创建新 struct； 遍历字段，跳过 null 字段（投影裁剪），添加非 null 字段；最后设 id。
   */
  public TypeDescription record(
      TypeDescription record, List<String> names, List<TypeDescription> fields) {
    Preconditions.checkArgument(names.size() == fields.size(), "All fields must have names");
    MappedField field = nameMapping.find(currentPath());
    TypeDescription structType = TypeDescription.createStruct();

    for (int i = 0; i < fields.size(); i++) {
      String fieldName = names.get(i);
      TypeDescription fieldType = fields.get(i);
      if (fieldType != null) {
        structType.addField(fieldName, fieldType);
      }
    }
    return setId(structType, field);
  }

  @Override
  /** 处理 list 节点：查找映射 id 后创建新 list TypeDescription。 */
  public TypeDescription list(TypeDescription array, TypeDescription element) {
    Preconditions.checkArgument(element != null, "List type must have element type");

    MappedField field = nameMapping.find(currentPath());
    TypeDescription listType = TypeDescription.createList(element);
    return setId(listType, field);
  }

  @Override
  /** 处理 map 节点：查找映射 id 后创建新 map TypeDescription。 */
  public TypeDescription map(TypeDescription map, TypeDescription key, TypeDescription value) {
    Preconditions.checkArgument(
        key != null && value != null, "Map type must have both key and value types");

    MappedField field = nameMapping.find(currentPath());
    TypeDescription mapType = TypeDescription.createMap(key, value);
    return setId(mapType, field);
  }

  @Override
  /**
   * 处理叶子节点：查找映射 id，对原始 TypeDescription 做 clone 后设属性。
   *
   * <p>设计要点：clone 避免修改原始 schema 对象，保证幂等安全。
   */
  public TypeDescription primitive(TypeDescription primitive) {
    MappedField field = nameMapping.find(currentPath());
    return setId(primitive.clone(), field);
  }
}
