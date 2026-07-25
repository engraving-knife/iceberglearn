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
package org.apache.iceberg.rest.requests;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.iceberg.exceptions.UnprocessableEntityException;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.rest.RESTRequest;

/**
 * 更新命名空间属性的 REST 请求。
 *
 * <p>所属模块：iceberg-core，REST 请求模型层（{@code rest.requests} 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>承载对命名空间属性的更新操作：设置（updates）与移除（removals）；
 *   <li>校验同一键不能同时出现在 updates 和 removals 中，避免语义冲突；
 *   <li>通过内部 Builder 收敛不可变对象的构造。
 * </ul>
 *
 * <p>设计意图：采用不可变值对象 + Builder 模式；内部 Builder 使用 {@link ImmutableSet.Builder} 与 {@link
 * ImmutableMap.Builder} 收集变更，最终在 {@link #build()} 一次性构建不可变集合，保证请求对象的线程安全。无参构造仅为 Jackson 反序列化保留。
 *
 * <p>上下游关系：由 {@code RESTCatalog} 等上层组件通过 Builder 构造，经 {@link org.apache.iceberg.rest.RESTClient}
 * 序列化后发送至 REST 服务端的 命名空间属性更新接口。
 */
public class UpdateNamespacePropertiesRequest implements RESTRequest {

  private List<String> removals;
  private Map<String, String> updates;

  /** Jackson 反序列化所需的无参构造器，外部代码不应直接调用。 */
  public UpdateNamespacePropertiesRequest() {
    // Required for Jackson deserialization.
  }

  /**
   * 私有全参构造器，仅由 {@link Builder#build()} 调用。
   *
   * <p>逻辑：赋值后立即调用 {@link #validate()} 进行基础校验。
   *
   * @param removals 待移除的属性键列表
   * @param updates 待设置或更新的属性键值对
   */
  private UpdateNamespacePropertiesRequest(List<String> removals, Map<String, String> updates) {
    this.removals = removals;
    this.updates = updates;
    validate();
  }

  /**
   * 校验请求字段合法性。
   *
   * <p>逻辑：计算 updates 键集合与 removals 集合的交集；若交集非空，说明同一键被同时 设置与移除，存在语义冲突，抛出 {@link
   * UnprocessableEntityException}。
   */
  @Override
  public void validate() {
    Set<String> commonKeys = Sets.intersection(updates().keySet(), Sets.newHashSet(removals()));
    if (!commonKeys.isEmpty()) {
      throw new UnprocessableEntityException(
          "Invalid namespace update, cannot simultaneously set and remove keys: %s", commonKeys);
    }
  }

  /**
   * 返回待移除的属性键列表。
   *
   * @return 不可变列表；当未设置时返回空列表而非 null
   */
  public List<String> removals() {
    return removals == null ? ImmutableList.of() : removals;
  }

  /**
   * 返回待设置或更新的属性键值对。
   *
   * @return 不可变 Map；当未设置时返回空 Map 而非 null
   */
  public Map<String, String> updates() {
    return updates == null ? ImmutableMap.of() : updates;
  }

  /** 返回该请求的可读字符串表示，便于日志输出与调试。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("removals", removals)
        .add("updates", updates)
        .toString();
  }

  /** 创建并返回一个新的 {@link Builder} 实例，用于构造 {@link UpdateNamespacePropertiesRequest}。 */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * {@link UpdateNamespacePropertiesRequest} 的构造器。
   *
   * <p>设计意图：采用链式调用风格，分别通过 removalsBuilder 与 updatesBuilder 收集 移除项与更新项；使用 ImmutableSet 去重移除项，最终在
   * {@link #build()} 一次性 构建不可变集合，保证构造完成的对象不可变、线程安全。
   */
  public static class Builder {
    private final ImmutableSet.Builder<String> removalsBuilder = ImmutableSet.builder();
    private final ImmutableMap.Builder<String, String> updatesBuilder = ImmutableMap.builder();

    /** 私有构造器，仅可通过 {@link UpdateNamespacePropertiesRequest#builder()} 获取实例。 */
    private Builder() {}

    /**
     * 添加一个待移除的属性键。
     *
     * @param removal 待移除的属性键，不可为 null
     * @return 当前 Builder
     */
    public Builder remove(String removal) {
      Preconditions.checkNotNull(removal, "Invalid property to remove: null");
      removalsBuilder.add(removal);
      return this;
    }

    /**
     * 批量添加待移除的属性键。
     *
     * <p>逻辑：先断言集合非 null 且不含 null 元素，再批量加入 removalsBuilder。
     *
     * @param removals 待移除的属性键集合
     * @return 当前 Builder
     */
    public Builder removeAll(Collection<String> removals) {
      Preconditions.checkNotNull(removals, "Invalid list of properties to remove: null");
      Preconditions.checkArgument(!removals.contains(null), "Invalid property to remove: null");
      removalsBuilder.addAll(removals);
      return this;
    }

    /**
     * 设置或更新单个属性键值对。
     *
     * <p>逻辑：先断言键与值均非 null（值为 null 时提示应改用 {@link #remove(String)}）， 再加入 updatesBuilder。
     *
     * @param key 属性键，不可为 null
     * @param value 属性值，不可为 null（如需移除请使用 {@link #remove(String)}）
     * @return 当前 Builder
     */
    public Builder update(String key, String value) {
      Preconditions.checkNotNull(key, "Invalid property to update: null");
      Preconditions.checkNotNull(
          value, "Invalid value to update for key [%s]: null. Use remove instead", key);
      updatesBuilder.put(key, value);
      return this;
    }

    /**
     * 批量设置或更新属性键值对。
     *
     * <p>逻辑：先断言集合非 null，且不含 null 键或 null 值（值为 null 时通过 {@link Maps#filterValues(Map,
     * java.util.function.Predicate)} 定位出错的键并提示 应改用 remove），再批量合并到 updatesBuilder。
     *
     * @param updates 属性键值对集合
     * @return 当前 Builder
     */
    public Builder updateAll(Map<String, String> updates) {
      Preconditions.checkNotNull(updates, "Invalid collection of properties to update: null");
      Preconditions.checkArgument(!updates.containsKey(null), "Invalid property to update: null");
      Preconditions.checkArgument(
          !updates.containsValue(null),
          "Invalid value to update for properties %s: null. Use remove instead",
          Maps.filterValues(updates, Objects::isNull).keySet());
      updatesBuilder.putAll(updates);
      return this;
    }

    /**
     * 构建并返回不可变的 {@link UpdateNamespacePropertiesRequest} 实例。
     *
     * <p>逻辑：将 removalsBuilder 构建为不可变 Set 后转为 List，updatesBuilder 构建 为不可变 Map；传入私有构造器，构造器内部会执行
     * {@link UpdateNamespacePropertiesRequest#validate()} 校验。
     *
     * @return 新的 {@link UpdateNamespacePropertiesRequest} 实例
     */
    public UpdateNamespacePropertiesRequest build() {
      List<String> removals = removalsBuilder.build().asList();
      Map<String, String> updates = updatesBuilder.build();

      return new UpdateNamespacePropertiesRequest(removals, updates);
    }
  }
}
