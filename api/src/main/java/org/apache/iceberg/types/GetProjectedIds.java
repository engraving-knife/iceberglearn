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

import java.util.List;
import java.util.Set;
import org.apache.iceberg.Schema;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;

/**
 * 收集字段 ID 访问者：遍历 schema 收集所有字段的 ID。
 *
 * <p>所属模块：iceberg-api（被 {@link TypeUtil#getProjectedIds} 使用）。
 *
 * <p>职责：后序遍历类型树，收集 struct 字段、list 元素、map key/value 的字段 ID。
 *
 * <p>设计意图：includeStructIds 控制是否收集 struct 类型字段的 ID（struct 字段本身也是字段）； 原始类型字段始终收集；list 元素和 map
 * key/value 在子节点无结果时收集（避免重复）。
 */
class GetProjectedIds extends TypeUtil.SchemaVisitor<Set<Integer>> {
  private final boolean includeStructIds;
  private final Set<Integer> fieldIds = Sets.newHashSet();

  GetProjectedIds() {
    this(false);
  }

  GetProjectedIds(boolean includeStructIds) {
    this.includeStructIds = includeStructIds;
  }

  @Override
  public Set<Integer> schema(Schema schema, Set<Integer> structResult) {
    return fieldIds;
  }

  @Override
  public Set<Integer> struct(Types.StructType struct, List<Set<Integer>> fieldResults) {
    return fieldIds;
  }

  @Override
  public Set<Integer> field(Types.NestedField field, Set<Integer> fieldResult) {
    if ((includeStructIds && field.type().isStructType()) || field.type().isPrimitiveType()) {
      fieldIds.add(field.fieldId());
    }
    return fieldIds;
  }

  @Override
  public Set<Integer> list(Types.ListType list, Set<Integer> elementResult) {
    if (elementResult == null) {
      for (Types.NestedField field : list.fields()) {
        fieldIds.add(field.fieldId());
      }
    }
    return fieldIds;
  }

  @Override
  public Set<Integer> map(Types.MapType map, Set<Integer> keyResult, Set<Integer> valueResult) {
    if (valueResult == null) {
      for (Types.NestedField field : map.fields()) {
        fieldIds.add(field.fieldId());
      }
    }
    return fieldIds;
  }
}
