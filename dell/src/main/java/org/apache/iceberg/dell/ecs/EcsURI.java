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
package org.apache.iceberg.dell.ecs;

import java.net.URI;
import java.util.Set;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;

/**
 * ECS（Dell EMC Elastic Cloud Storage）位置解析类，不可变值对象。
 *
 * <p>所属模块：iceberg-dell。职责：把 {@code ecs://bucket/name}、{@code s3://...} 等 URI 解析为 bucket 与 object
 * name 两个分量，供 {@code ECSFileIO} 定位对象使用。
 *
 * <p>设计意图：作为不可变记录（Immutable Record），构造时即完成解析并缓存 bucket/name/location， 后续读取无开销；支持两类构造入口——完整 URI
 * 字符串与分离的 bucket+name，统一产出规范化的 {@code ecs://} location。校验 scheme 白名单（ecs/s3/s3a/s3n）以尽早暴露非法路径。
 */
class EcsURI {

  private static final Set<String> VALID_SCHEME = ImmutableSet.of("ecs", "s3", "s3a", "s3n");

  private final String location;
  private final String bucket;
  private final String name;

  /**
   * 根据完整 URI 字符串构造 {@link EcsURI}。
   *
   * <p>逻辑：用 {@link URI#create} 解析，校验 scheme 在白名单内，取 host 为 bucket、 path 去除前导斜杠为 name。
   *
   * @param location 完整 URI
   */
  EcsURI(String location) {
    Preconditions.checkNotNull(location == null, "Location %s can not be null", location);

    this.location = location;

    URI uri = URI.create(location);
    ValidationException.check(
        VALID_SCHEME.contains(uri.getScheme().toLowerCase()), "Invalid ecs location: %s", location);
    this.bucket = uri.getHost();
    this.name = uri.getPath().replaceAll("^/*", "");
  }

  /**
   * 根据 bucket 与 name 构造 {@link EcsURI}，并规范化为 {@code ecs://bucket/name} 形式。
   *
   * <p>name 的前导斜杠会被忽略。
   */
  EcsURI(String bucket, String name) {
    this.bucket = bucket;
    this.name = name.replaceAll("^/*", "");
    this.location = String.format("ecs://%s/%s", bucket, name);
  }

  /** 返回 ECS bucket 名称。 */
  public String bucket() {
    return bucket;
  }

  /** 返回 ECS object name。 */
  public String name() {
    return name;
  }

  /** 返回原始 location。 */
  public String location() {
    return location;
  }

  @Override
  public String toString() {
    return location;
  }
}
