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
import org.apache.iceberg.rest.RESTResponse;
import org.immutables.value.Value;

/**
 * S3 签名响应 DTO：签名服务返回的签名结果。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成模块，处于引擎层之下）。
 *
 * <p>职责：承载签名后的 URI（含签名参数）和 headers（含 Authorization 头）， 供 {@link S3V4RestSignerClient} 将签名信息注入到实际 S3
 * 请求中。
 *
 * <p>设计意图：使用 Immutables 生成不可变实现，实现 {@link RESTResponse} 接口 以便通过 Iceberg REST 框架序列化/反序列化。
 *
 * <p>上下游关系：由远程签名服务返回给 {@link S3V4RestSignerClient}，对应 {@link S3SignRequest}。
 */
@Value.Immutable
public interface S3SignResponse extends RESTResponse {
  URI uri();

  Map<String, List<String>> headers();

  @Override
  default void validate() {}
}
