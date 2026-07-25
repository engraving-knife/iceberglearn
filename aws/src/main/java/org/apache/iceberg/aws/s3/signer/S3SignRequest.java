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
package org.apache.iceberg.aws.s3.signer;

import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.iceberg.rest.RESTRequest;
import org.immutables.value.Value;

/**
 * S3 签名请求 DTO：客户端通过 REST API 向签名服务发送的签名请求。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成模块，处于引擎层之下）。
 *
 * <p>职责：承载 S3 请求的 region、HTTP method、URI、headers、properties 和可选 body， 供 {@link
 * S3V4RestSignerClient} 发送给远程签名服务进行 SigV4 签名。
 *
 * <p>设计意图：使用 Immutables 生成不可变实现，实现 {@link RESTRequest} 接口 以便通过 Iceberg REST 框架序列化/反序列化。支持 body
 * 字段用于需要对 body 做哈希的签名场景。
 *
 * <p>上下游关系：由 {@link S3V4RestSignerClient} 构建并通过 HTTP 发送给签名服务端， 服务端返回 {@link S3SignResponse}。
 */
@Value.Immutable
public interface S3SignRequest extends RESTRequest {
  String region();

  String method();

  URI uri();

  Map<String, List<String>> headers();

  Map<String, String> properties();

  @Value.Default
  @Nullable
  default String body() {
    return null;
  }

  @Override
  default void validate() {}
}
