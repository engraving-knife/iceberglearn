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
package org.apache.iceberg.rest;

import java.util.Map;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.base.Joiner;

/**
 * REST 接口资源路径生成器。
 *
 * <p>所属模块：iceberg-core，REST 协议层（{@code rest} 包）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>统一构造 REST Catalog 各操作对应的 URL 路径片段；
 *   <li>封装 REST 协议规定的路径前缀（{@code v1}）与 catalog 自定义前缀（prefix）；
 *   <li>对命名空间与表标识进行 URL 编码，保证路径安全；
 *   <li>提供配置、令牌、命名空间、表、注册、重命名、指标上报、事务提交等接口路径。
 * </ul>
 *
 * <p>设计意图：实例不可变（prefix 字段 final），保证线程安全；通过 {@link #forCatalogProperties(Map)} 工厂方法从 catalog
 * 属性构造，便于不同 catalog 使用不同 URL 前缀。路径拼接使用 {@link Joiner} 跳过 null 段，使 prefix 可选。
 *
 * <p>上下游关系：由 {@code RESTCatalog} 及其相关操作类在发起 HTTP 请求前调用， 生成请求路径；底层依赖 {@link RESTUtil} 进行命名空间与字符串的
 * URL 编码。
 */
public class ResourcePaths {
  private static final Joiner SLASH = Joiner.on("/").skipNulls();
  private static final String PREFIX = "prefix";

  /**
   * 根据 catalog 属性构造 {@link ResourcePaths} 实例。
   *
   * <p>逻辑：从属性 Map 中读取 {@code prefix} 键的值作为 URL 前缀；若不存在则为 null， 后续路径拼接时会自动跳过 null 段。
   *
   * @param properties catalog 属性键值对
   * @return 新的 {@link ResourcePaths} 实例
   */
  public static ResourcePaths forCatalogProperties(Map<String, String> properties) {
    return new ResourcePaths(properties.get(PREFIX));
  }

  /**
   * 返回获取 REST Catalog 配置的路径。
   *
   * @return 路径字符串 {@code v1/config}
   */
  public static String config() {
    return "v1/config";
  }

  /**
   * 返回获取 OAuth 令牌的路径。
   *
   * @return 路径字符串 {@code v1/oauth/tokens}
   */
  public static String tokens() {
    return "v1/oauth/tokens";
  }

  private final String prefix;

  /**
   * 构造指定前缀的 {@link ResourcePaths}。
   *
   * @param prefix URL 前缀，可为 null
   */
  public ResourcePaths(String prefix) {
    this.prefix = prefix;
  }

  /**
   * 返回列出命名空间的路径。
   *
   * @return 路径字符串，形如 {@code v1/[prefix]/namespaces}
   */
  public String namespaces() {
    return SLASH.join("v1", prefix, "namespaces");
  }

  /**
   * 返回指定命名空间的路径。
   *
   * @param ns 命名空间
   * @return 路径字符串，形如 {@code v1/[prefix]/namespaces/<ns>}
   */
  public String namespace(Namespace ns) {
    return SLASH.join("v1", prefix, "namespaces", RESTUtil.encodeNamespace(ns));
  }

  /**
   * 返回指定命名空间属性的路径。
   *
   * @param ns 命名空间
   * @return 路径字符串，形如 {@code v1/[prefix]/namespaces/<ns>/properties}
   */
  public String namespaceProperties(Namespace ns) {
    return SLASH.join("v1", prefix, "namespaces", RESTUtil.encodeNamespace(ns), "properties");
  }

  /**
   * 返回列出指定命名空间下表的路径。
   *
   * @param ns 命名空间
   * @return 路径字符串，形如 {@code v1/[prefix]/namespaces/<ns>/tables}
   */
  public String tables(Namespace ns) {
    return SLASH.join("v1", prefix, "namespaces", RESTUtil.encodeNamespace(ns), "tables");
  }

  /**
   * 返回指定表标识的路径。
   *
   * <p>逻辑：将命名空间部分与表名部分分别 URL 编码后拼接，形如 {@code v1/[prefix]/namespaces/<ns>/tables/<tableName>}。
   *
   * @param ident 表标识
   * @return 路径字符串
   */
  public String table(TableIdentifier ident) {
    return SLASH.join(
        "v1",
        prefix,
        "namespaces",
        RESTUtil.encodeNamespace(ident.namespace()),
        "tables",
        RESTUtil.encodeString(ident.name()));
  }

  /**
   * 返回在指定命名空间下注册表的路径。
   *
   * @param ns 命名空间
   * @return 路径字符串，形如 {@code v1/[prefix]/namespaces/<ns>/register}
   */
  public String register(Namespace ns) {
    return SLASH.join("v1", prefix, "namespaces", RESTUtil.encodeNamespace(ns), "register");
  }

  /**
   * 返回重命名表的路径。
   *
   * @return 路径字符串，形如 {@code v1/[prefix]/tables/rename}
   */
  public String rename() {
    return SLASH.join("v1", prefix, "tables", "rename");
  }

  /**
   * 返回上报指定表指标的路径。
   *
   * <p>逻辑：将命名空间部分与表名部分分别 URL 编码后拼接，形如 {@code
   * v1/[prefix]/namespaces/<ns>/tables/<tableName>/metrics}。
   *
   * @param identifier 表标识
   * @return 路径字符串
   */
  public String metrics(TableIdentifier identifier) {
    return SLASH.join(
        "v1",
        prefix,
        "namespaces",
        RESTUtil.encodeNamespace(identifier.namespace()),
        "tables",
        RESTUtil.encodeString(identifier.name()),
        "metrics");
  }

  /**
   * 返回提交事务的路径。
   *
   * @return 路径字符串，形如 {@code v1/[prefix]/transactions/commit}
   */
  public String commitTransaction() {
    return SLASH.join("v1", prefix, "transactions", "commit");
  }
}
