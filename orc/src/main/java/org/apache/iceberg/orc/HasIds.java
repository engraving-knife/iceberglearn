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
import java.util.function.Predicate;
import org.apache.orc.TypeDescription;

/**
 * 检查 ORC schema 树中是否包含 Iceberg 字段 id 的访问器。
 *
 * <p>所属模块：iceberg-orc。用于判断 ORC 文件是否已携带 Iceberg id 属性， 从而决定读取时是否需要 NameMapping 回溯。
 *
 * <p>职责：遍历 ORC TypeDescription 树，只要任一节点（含自身）有 Iceberg id 即返回 true。
 *
 * <p>设计意图：递归短路——struct/list/map 只要自身或任一子节点有 id 即 true， primitive 仅检查自身。这是布尔 OR 的自底向上归约。
 *
 * <p>上下游关系：被 {@link ORCSchemaUtil} 调用，用于判断文件 schema 是否原生支持 Iceberg id。
 */
class HasIds extends OrcSchemaVisitor<Boolean> {

  @Override
  /** struct 有 id 或任一子字段有 id 即 true。 */
  public Boolean record(TypeDescription record, List<String> names, List<Boolean> fields) {
    return ORCSchemaUtil.icebergID(record).isPresent()
        || fields.stream().anyMatch(Predicate.isEqual(true));
  }

  @Override
  /** list 有 id 或元素有 id 即 true。 */
  public Boolean list(TypeDescription array, Boolean element) {
    return ORCSchemaUtil.icebergID(array).isPresent() || element;
  }

  @Override
  /** map 有 id 或 key/value 有 id 即 true。 */
  public Boolean map(TypeDescription map, Boolean key, Boolean value) {
    return ORCSchemaUtil.icebergID(map).isPresent() || key || value;
  }

  @Override
  /** 叶子节点仅检查自身是否有 id。 */
  public Boolean primitive(TypeDescription primitive) {
    return ORCSchemaUtil.icebergID(primitive).isPresent();
  }
}
