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
package org.apache.iceberg.rest.auth;

/**
 * 文件级说明：REST Catalog 客户端 OAuth2 认证相关的配置常量集合。
 *
 * <p>所属模块：iceberg-core（REST Catalog 客户端实现，承担与远端 REST Catalog Server 之间的 OAuth2 令牌交互常量定义）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>集中定义 OAuth2 认证流程使用的属性键名（如 token、credential、scope 等），供配置解析与 {@link
 *       org.apache.iceberg.rest.RESTSessionCatalog} 初始化时引用。
 *   <li>定义 token 类型 URN（access_token、refresh_token、id_token、SAML、JWT），用于 token 交换流程标识 subject/actor
 *       令牌类型。
 *   <li>定义 OAuth2 错误响应中的 error 字段取值，便于 {@link org.apache.iceberg.rest.ErrorHandlers} 识别错误类别。
 * </ul>
 *
 * <p>设计意图：将这些常量集中在一处，避免散落在各调用点导致拼写不一致；常量值与 RFC 6749、 RFC 8693（Token Exchange）及 Iceberg REST Catalog
 * 规范保持一致。
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.rest.auth.OAuth2Util}、{@link
 * org.apache.iceberg.rest.RESTSessionCatalog} 及配置加载逻辑引用。
 */
public class OAuth2Properties {
  private OAuth2Properties() {}

  /** 与服务端交互时使用的 Bearer 令牌（直接提供，无需兑换）。 */
  public static final String TOKEN = "token";

  /** 用于在 OAuth2 客户端凭证流程中兑换令牌的凭证（client credentials）。 */
  public static final String CREDENTIAL = "credential";

  /** 在尝试兑换已配置的 catalog Bearer 令牌前等待的间隔（毫秒）。默认 1 小时后尝试令牌兑换。 */
  public static final String TOKEN_EXPIRES_IN_MS = "token-expires-in-ms";

  /** {@link #TOKEN_EXPIRES_IN_MS} 的默认值：1 小时（毫秒）。 */
  public static final long TOKEN_EXPIRES_IN_MS_DEFAULT = 3_600_000; // 1 hour

  /** 控制当能够获取令牌过期时间信息时，是否对该令牌进行刷新。 */
  public static final String TOKEN_REFRESH_ENABLED = "token-refresh-enabled";

  /** {@link #TOKEN_REFRESH_ENABLED} 的默认值：开启刷新。 */
  public static final boolean TOKEN_REFRESH_ENABLED_DEFAULT = true;

  /** OAuth2 的额外 scope 配置键。 */
  public static final String SCOPE = "scope";

  /** OAuth2 流程中默认使用的 catalog scope 值。 */
  public static final String CATALOG_SCOPE = "catalog";

  // 令牌类型 URN 常量（参见 RFC 8693）
  /** access_token 类型 URN。 */
  public static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
  /** refresh_token 类型 URN。 */
  public static final String REFRESH_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:refresh_token";
  /** id_token 类型 URN。 */
  public static final String ID_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:id_token";
  /** SAML 1.0 断言类型 URN。 */
  public static final String SAML1_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:saml1";
  /** SAML 2.0 断言类型 URN。 */
  public static final String SAML2_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:saml2";
  /** JWT 类型 URN。 */
  public static final String JWT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt";

  // OAuth2 错误响应 error 字段取值常量（参见 RFC 6749 §5.2）
  /** 请求格式错误或缺少必要参数。 */
  public static final String INVALID_REQUEST_ERROR = "invalid_request";
  /** 客户端认证失败。 */
  public static final String INVALID_CLIENT_ERROR = "invalid_client";
  /** 提供的授权凭证或刷新令牌无效/过期。 */
  public static final String INVALID_GRANT_ERROR = "invalid_grant";
  /** 已认证客户端无权使用该授权方式。 */
  public static final String UNAUTHORIZED_CLIENT_ERROR = "unauthorized_client";
  /** 授权服务器不支持该 grant_type。 */
  public static final String UNSUPPORTED_GRANT_TYPE_ERROR = "unsupported_grant_type";
  /** 请求的 scope 无效/未知/格式错误。 */
  public static final String INVALID_SCOPE_ERROR = "invalid_scope";
}
