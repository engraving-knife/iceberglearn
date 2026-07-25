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

import java.util.Collection;
import org.apache.iceberg.exceptions.CommitFailedException;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Type;

/**
 * Schema 演进 API：对表 schema 进行列增删改、重命名、移动、类型变更等操作。
 *
 * <p>所属模块：iceberg-api（顶层公共 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>新增列（顶层/嵌套、可选/必填）；
 *   <li>重命名列、删除列、移动列位置；
 *   <li>更新列类型（仅允许拓宽）、更新列文档；
 *   <li>设置标识字段、按名称 union 合并 schema。
 * </ul>
 *
 * <p>设计意图：提交时变更应用到当前表元数据，冲突时不自动重试而直接抛 {@link CommitFailedException}。不兼容变更（如新增必填列、把列改为必填）需先调用 {@link
 * #allowIncompatibleChanges()} 显式允许，以防止破坏旧数据读取。
 *
 * <p>上下游关系：由 {@link Table#updateSchema()} 或 {@link Transaction#updateSchema()} 创建； 下游实现位于 core 模块。
 */
public interface UpdateSchema extends PendingUpdate<Schema> {

  /**
   * 允许对 schema 做不兼容变更。
   *
   * <p>不兼容变更可能导致读取旧数据文件失败。例如新增必填列后再读取不含该列的旧文件会失败； 但若不存在不兼容的旧数据，则可以允许。
   *
   * <p>设计意图：调用方需自行验证变更不会破坏现有数据后方可启用。例如某列新增时为可选但 实际总有值、且早于该列新增的数据已被删除，则可配合 {@link
   * #requireColumn(String)} 把该列改为必填。
   *
   * @return this，便于链式调用
   */
  UpdateSchema allowIncompatibleChanges();

  /**
   * 新增一个顶层列（无文档）。
   *
   * <p>名称中不允许包含 "."（会被解释为路径分隔符）；如需添加嵌套列或名称含 "." 的列， 请使用 {@link #addColumn(String, String, Type)}。
   *
   * <p>若 type 为嵌套类型，其内部字段 ID 会在加入 schema 时被重新分配。
   *
   * @param name 新列名
   * @param type 新列类型
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 含 "."
   */
  default UpdateSchema addColumn(String name, Type type) {
    return addColumn(name, type, null);
  }

  /**
   * 新增一个顶层列（带文档）。
   *
   * <p>名称中不允许包含 "."；如需添加嵌套列或名称含 "." 的列， 请使用 {@link #addColumn(String, String, Type)}。
   *
   * <p>若 type 为嵌套类型，其内部字段 ID 会在加入 schema 时被重新分配。
   *
   * @param name 新列名
   * @param type 新列类型
   * @param doc 新列文档说明
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 含 "."
   */
  UpdateSchema addColumn(String name, Type type, String doc);

  /**
   * 向嵌套 struct 中新增列（无文档）。
   *
   * <p>逻辑：通过 {@link Schema#findField(String)} 查找 parent。parent 为 null 时加到根； parent 是 struct 时加到该
   * struct；是 list 时加到元素 struct；是 map 时加到 value struct。
   *
   * <p>name 原样使用，含 "." 的名称不做特殊处理。若 type 为嵌套类型，其字段 ID 会被重新分配。
   *
   * @param parent 父 struct 名称
   * @param name 新列名
   * @param type 新列类型
   * @return this，便于链式调用
   * @throws IllegalArgumentException parent 未标识一个 struct
   */
  default UpdateSchema addColumn(String parent, String name, Type type) {
    return addColumn(parent, name, type, null);
  }

  /**
   * 向嵌套 struct 中新增列（带文档）。
   *
   * <p>逻辑：通过 {@link Schema#findField(String)} 查找 parent。parent 为 null 时加到根； parent 是 struct 时加到该
   * struct；是 list 时加到元素 struct；是 map 时加到 value struct。
   *
   * <p>name 原样使用，含 "." 的名称不做特殊处理。若 type 为嵌套类型，其字段 ID 会被重新分配。
   *
   * @param parent 父 struct 名称
   * @param name 新列名
   * @param type 新列类型
   * @param doc 新列文档说明
   * @return this，便于链式调用
   * @throws IllegalArgumentException parent 未标识一个 struct
   */
  UpdateSchema addColumn(String parent, String name, Type type, String doc);

  /**
   * 新增一个顶层必填列（无文档）。
   *
   * <p>这是不兼容变更，可能破坏旧数据读取；除非先调用 {@link #allowIncompatibleChanges()}， 否则抛异常。名称中不允许包含 "."，嵌套场景请用
   * {@link #addRequiredColumn(String, String, Type)}。嵌套类型字段 ID 会被重新分配。
   *
   * @param name 新列名
   * @param type 新列类型
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 含 "."
   */
  default UpdateSchema addRequiredColumn(String name, Type type) {
    return addRequiredColumn(name, type, null);
  }

  /**
   * 新增一个顶层必填列（带文档）。
   *
   * <p>这是不兼容变更，可能破坏旧数据读取；除非先调用 {@link #allowIncompatibleChanges()}， 否则抛异常。名称中不允许包含 "."，嵌套场景请用
   * {@link #addRequiredColumn(String, String, Type)}。嵌套类型字段 ID 会被重新分配。
   *
   * @param name 新列名
   * @param type 新列类型
   * @param doc 新列文档说明
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 含 "."
   */
  UpdateSchema addRequiredColumn(String name, Type type, String doc);

  /**
   * 向嵌套 struct 中新增必填列（无文档）。
   *
   * <p>这是不兼容变更，需先调用 {@link #allowIncompatibleChanges()}。逻辑同 {@link #addColumn(String, String,
   * Type)}：通过 {@link Schema#findField(String)} 查找 parent， 按类型加到对应 struct。嵌套类型字段 ID 会被重新分配。
   *
   * @param parent 父 struct 名称
   * @param name 新列名
   * @param type 新列类型
   * @return this，便于链式调用
   * @throws IllegalArgumentException parent 未标识一个 struct
   */
  default UpdateSchema addRequiredColumn(String parent, String name, Type type) {
    return addRequiredColumn(parent, name, type, null);
  }

  /**
   * 向嵌套 struct 中新增必填列（带文档）。
   *
   * <p>这是不兼容变更，需先调用 {@link #allowIncompatibleChanges()}。逻辑同 {@link #addColumn(String, String, Type,
   * String)}：通过 {@link Schema#findField(String)} 查找 parent，按类型加到对应 struct。嵌套类型字段 ID 会被重新分配。
   *
   * @param parent 父 struct 名称
   * @param name 新列名
   * @param type 新列类型
   * @param doc 新列文档说明
   * @return this，便于链式调用
   * @throws IllegalArgumentException parent 未标识一个 struct
   */
  UpdateSchema addRequiredColumn(String parent, String name, Type type, String doc);

  /**
   * 重命名 schema 中的列。
   *
   * <p>逻辑：通过 {@link Schema#findField(String)} 查找待重命名列。新名称可含 "."， 不做特殊解析。列可在同一次更新中同时被重命名和更新。
   *
   * @param name 待重命名的列名
   * @param newName 新列名
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 未标识列，或与其他增删改冲突
   */
  UpdateSchema renameColumn(String name, String newName);

  /**
   * 把列更新为新的基本类型。
   *
   * <p>逻辑：通过 {@link Schema#findField(String)} 查找列。仅允许类型拓宽（如 int→long）。 列可在同一次更新中同时被重命名和更新。
   *
   * @param name 待更新列名
   * @param newType 新的基本类型
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 未标识列，或类型不兼容，或与其他变更冲突
   */
  UpdateSchema updateColumn(String name, Type.PrimitiveType newType);

  /**
   * 把列更新为新的基本类型并更新文档。
   *
   * <p>逻辑：委托 {@link #updateColumn(String, Type.PrimitiveType)} 更新类型， 再调用 {@link
   * #updateColumnDoc(String, String)} 更新文档。
   *
   * @param name 待更新列名
   * @param newType 新的基本类型
   * @param newDoc 新的文档说明
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 未标识列，或类型不兼容，或与其他变更冲突
   */
  default UpdateSchema updateColumn(String name, Type.PrimitiveType newType, String newDoc) {
    return updateColumn(name, newType).updateColumnDoc(name, newDoc);
  }

  /**
   * 更新列的文档说明。
   *
   * <p>逻辑：通过 {@link Schema#findField(String)} 查找列。列可在同一次更新中同时被重命名和更新。
   *
   * @param name 待更新列名
   * @param newDoc 新的文档说明
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 未标识列，或与其他变更冲突
   */
  UpdateSchema updateColumnDoc(String name, String newDoc);

  /**
   * 把列改为可选。
   *
   * @param name 待改为可选的列名
   * @return this，便于链式调用
   */
  UpdateSchema makeColumnOptional(String name);

  /**
   * 把列改为必填。
   *
   * <p>这是不兼容变更，可能破坏旧数据读取；除非先调用 {@link #allowIncompatibleChanges()}， 否则抛异常。
   *
   * @param name 待改为必填的列名
   * @return this，便于链式调用
   */
  UpdateSchema requireColumn(String name);

  /**
   * 删除 schema 中的列。
   *
   * <p>逻辑：通过 {@link Schema#findField(String)} 查找待删除列。
   *
   * @param name 待删除列名
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 未标识列，或与其他变更冲突
   */
  UpdateSchema deleteColumn(String name);

  /**
   * 把列移到 schema 或其父 struct 的最前面。
   *
   * @param name 待移动列名
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 未标识列，或与其他变更冲突
   */
  UpdateSchema moveFirst(String name);

  /**
   * 把列移到某参考列的正前方。
   *
   * <p>逻辑：通过 {@link Schema#findField(String)} 查找待移动列。若为嵌套列，只能在其所在 struct 内移动。
   *
   * @param name 待移动列名
   * @param beforeName 参考列名
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 未标识列，或与其他变更冲突
   */
  UpdateSchema moveBefore(String name, String beforeName);

  /**
   * 把列移到某参考列的正后方。
   *
   * <p>逻辑：通过 {@link Schema#findField(String)} 查找待移动列。若为嵌套列，只能在其所在 struct 内移动。
   *
   * @param name 待移动列名
   * @param afterName 参考列名
   * @return this，便于链式调用
   * @throws IllegalArgumentException name 未标识列，或与其他变更冲突
   */
  UpdateSchema moveAfter(String name, String afterName);

  /**
   * 按名称把给定新 schema 的字段新增与更新应用到现有 schema，生成 union schema。
   *
   * <p>逻辑：
   *
   * <ul>
   *   <li>对两 schema 中同名字段，要求类型拓宽可通过 {@link #updateColumn(String, Type.PrimitiveType)} 完成；
   *   <li>仅支持在新 schema 中标为可选时，把原必填字段改为可选（{@link #makeColumnOptional(String)}）；
   *   <li>仅支持用新 schema 的字段文档更新现有文档（{@link #updateColumnDoc(String, String)}）。
   * </ul>
   *
   * @param newSchema 用于与现有 schema 合并的新 schema
   * @return this，便于链式调用
   * @throws IllegalStateException 遍历新 schema 时出错
   * @throws IllegalArgumentException name 未标识列，或类型不兼容，或与其他变更冲突
   */
  UpdateSchema unionByNameWith(Schema newSchema);

  /**
   * 按字段名集合设置标识字段。
   *
   * <p>标识字段唯一，重复名称会被忽略。详见 {@link Schema#identifierFieldIds()}。
   *
   * @param names 设为标识字段的列名集合
   * @return this，便于链式调用
   */
  UpdateSchema setIdentifierFields(Collection<String> names);

  /**
   * 按变参设置标识字段。详见 {@link #setIdentifierFields(Collection)}。
   *
   * @param names 设为标识字段的列名
   * @return this，便于链式调用
   */
  default UpdateSchema setIdentifierFields(String... names) {
    return setIdentifierFields(Sets.newHashSet(names));
  }

  /**
   * 设置比较列名时是否大小写敏感。
   *
   * @param caseSensitive false 时不区分大小写
   * @return this，便于链式调用
   */
  default UpdateSchema caseSensitive(boolean caseSensitive) {
    throw new UnsupportedOperationException();
  }
}
