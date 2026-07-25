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
package org.apache.iceberg.aws.s3;

import java.util.Map;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;

/**
 * 模块：aws-s3，属于 Iceberg 存储接入层。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>表示 S3 上一个完全限定的资源位置（URI），封装 scheme、bucket 与 object key
 *   <li>解析并校验 S3 URI 的合法性
 *   <li>支持 bucket 到 access point 的映射：若 bucket 命中映射，则用 access point 替代 bucket 进行所有 S3 操作
 * </ul>
 *
 * <p>设计意图：直接基于字符串解析而非 {@link java.net.URI}，避免 Hadoop Path 在编码处理上的不一致； 支持任意合法 scheme 以兼容 s3a/s3n 及其他
 * S3 兼容对象存储（如 GCS）。不支持已废弃的 path-style 访问。 该类为不可变对象，线程安全。
 *
 * <p>上下游关系：被 {@link S3FileIO} 等 S3 文件系统实现使用， 用于将 Iceberg 中的 location 字符串转换为可操作的 bucket/key。
 *
 * <p>Note: Path-style access is deprecated and not supported by this implementation.
 */
class S3URI {
  private static final String SCHEME_DELIM = "://";
  private static final String PATH_DELIM = "/";
  private static final String QUERY_DELIM = "\\?";
  private static final String FRAGMENT_DELIM = "#";

  private final String location;
  private final String scheme;
  private final String bucket;
  private final String key;

  /**
   * 构造形如 scheme://bucket/key?query#fragment 的 {@link S3URI}，不使用 access point 映射。
   *
   * <p>支持任意合法 scheme 以兼容 s3a/s3n，并允许通过 {@link S3FileIO} 接入其他 S3 兼容对象存储（如 GCS）。
   *
   * @param location 完全限定的 URI
   */
  S3URI(String location) {
    this(location, ImmutableMap.of());
  }

  /**
   * 构造形如 scheme://(bucket|accessPoint)/key?query#fragment 的 {@link S3URI}，附带 access point 映射。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 location 非空，按 "://" 拆分得到 scheme 与剩余部分
   *   <li>按首个 "/" 拆分出 authority（bucket 或 access point）与 path
   *   <li>若 bucket 命中 bucketToAccessPointMapping 则替换为对应 access point
   *   <li>剥离 path 中的 query 与 fragment，得到最终 object key
   * </ol>
   *
   * @param location 完全限定的 URI
   * @param bucketToAccessPointMapping bucket 到 access point 的映射
   */
  S3URI(String location, Map<String, String> bucketToAccessPointMapping) {
    Preconditions.checkNotNull(location, "Location cannot be null.");

    this.location = location;
    String[] schemeSplit = location.split(SCHEME_DELIM, -1);
    ValidationException.check(
        schemeSplit.length == 2, "Invalid S3 URI, cannot determine scheme: %s", location);
    this.scheme = schemeSplit[0];

    String[] authoritySplit = schemeSplit[1].split(PATH_DELIM, 2);

    this.bucket =
        bucketToAccessPointMapping == null
            ? authoritySplit[0]
            : bucketToAccessPointMapping.getOrDefault(authoritySplit[0], authoritySplit[0]);

    // Strip query and fragment if they exist
    String path = authoritySplit.length > 1 ? authoritySplit[1] : "";
    path = path.split(QUERY_DELIM, -1)[0];
    path = path.split(FRAGMENT_DELIM, -1)[0];
    this.key = path;
  }

  /** 返回 S3 bucket 名称（可能为 access point）。 */
  public String bucket() {
    return bucket;
  }

  /** 返回 S3 object key 名称。 */
  public String key() {
    return key;
  }

  /** 返回原始未修改的 S3 URI 字符串。 */
  public String location() {
    return location;
  }

  /**
   * 返回 location 中原始的 scheme。
   *
   * @return URI scheme
   */
  public String scheme() {
    return scheme;
  }

  @Override
  public String toString() {
    return location;
  }
}
