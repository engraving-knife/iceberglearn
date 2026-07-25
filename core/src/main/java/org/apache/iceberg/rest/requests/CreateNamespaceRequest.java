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

import java.util.Map;
import java.util.Objects;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.rest.RESTRequest;

/**
 * 所属模块：iceberg-core；REST Catalog 请求模型层。
 *
 * <p>职责：封装"创建命名空间"的 REST 请求体，包含命名空间及其可选属性。具体包括：
 *
 * <ul>
 *   <li>持有 {@link Namespace} 与可选的属性 Map
 *   <li>提供 {@link #validate()} 校验命名空间非空
 *   <li>提供 Builder 链式构造请求，并校验属性键值非空
 * </ul>
 *
 * <p>设计意图：作为 {@link RESTRequest} 的实现，遵循 Iceberg REST Catalog 规范的创建命名空间协议； 提供无参构造器以支持 Jackson
 * 反序列化；属性使用不可变 Map 保证传输中不被修改。
 *
 * <p>上下游关系：由 {@code RESTSessionCatalog} 在创建命名空间时构造；由对应的 Parser 序列化为 JSON； 服务端接收后执行命名空间创建。
 */
public class CreateNamespaceRequest implements RESTRequest {

  // 待创建的命名空间
  private Namespace namespace;
  // 命名空间的可选属性
  private Map<String, String> properties;

  /** 无参构造器，仅供 Jackson 反序列化使用。 */
  public CreateNamespaceRequest() {
    // Needed for Jackson Deserialization.
  }

  /**
   * 私有构造器，由 {@link Builder#build()} 调用，构造后立即执行 {@link #validate()} 校验。
   *
   * @param namespace 命名空间
   * @param properties 命名空间属性
   */
  private CreateNamespaceRequest(Namespace namespace, Map<String, String> properties) {
    this.namespace = namespace;
    this.properties = properties;
    validate();
  }

  /** 校验请求体合法性：命名空间不能为 null。 */
  @Override
  public void validate() {
    Preconditions.checkArgument(namespace != null, "Invalid namespace: null");
  }

  /**
   * 返回待创建的命名空间。
   *
   * @return 命名空间
   */
  public Namespace namespace() {
    return namespace;
  }

  /**
   * 返回命名空间属性，若未设置则返回空 Map。
   *
   * @return 不可变的属性 Map
   */
  public Map<String, String> properties() {
    return properties != null ? properties : ImmutableMap.of();
  }

  /**
   * 返回该请求的字符串表示，便于调试与日志输出。
   *
   * @return 包含 namespace 与 properties 的字符串
   */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("namespace", namespace)
        .add("properties", properties)
        .toString();
  }

  /**
   * 创建 {@link Builder} 实例。
   *
   * @return 新的 Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * {@link CreateNamespaceRequest} 的构建器，采用链式 API 配置命名空间与属性。
   *
   * <p>设计意图：将请求构造与表示分离，构造时校验属性键值非空，避免运行时才发现非法参数。
   */
  public static class Builder {
    private Namespace namespace;
    private final ImmutableMap.Builder<String, String> properties = ImmutableMap.builder();

    /** 构造构建器。 */
    private Builder() {}

    /**
     * 设置待创建的命名空间，不能为 null。
     *
     * @param ns 命名空间
     * @return 当前 Builder
     */
    public Builder withNamespace(Namespace ns) {
      Preconditions.checkNotNull(ns, "Invalid namespace: null");
      this.namespace = ns;
      return this;
    }

    /**
     * 批量设置命名空间属性，键与值均不能为 null。
     *
     * <p>逻辑：校验 props 非 null、不含 null 键、不含 null 值（null 值时报告对应键名）， 通过后追加到属性 Builder。
     *
     * @param props 属性映射
     * @return 当前 Builder
     */
    public Builder setProperties(Map<String, String> props) {
      Preconditions.checkNotNull(props, "Invalid collection of properties: null");
      Preconditions.checkArgument(!props.containsKey(null), "Invalid property: null");
      Preconditions.checkArgument(
          !props.containsValue(null),
          "Invalid value for properties %s: null",
          Maps.filterValues(props, Objects::isNull).keySet());
      properties.putAll(props);
      return this;
    }

    /**
     * 构造 {@link CreateNamespaceRequest} 实例，构造时触发 {@link CreateNamespaceRequest#validate()}。
     *
     * @return 新建的请求对象
     */
    public CreateNamespaceRequest build() {
      return new CreateNamespaceRequest(namespace, properties.build());
    }
  }
}
