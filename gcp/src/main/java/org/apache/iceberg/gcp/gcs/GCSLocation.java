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
package org.apache.iceberg.gcp.gcs;

import com.google.cloud.storage.BlobId;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Google Cloud Storage（GCS）位置解析类，不可变值对象。
 *
 * <p>所属模块：iceberg-gcp。职责：把 {@code gs://bucket/path?query#fragment} 形式的 URI 解析为 bucket 与 prefix
 * 两个分量，供 {@code GCSFileIO} 定位对象使用。
 *
 * <p>设计意图：与 {@link BlobId#fromGsUtilUri(String)} 不同，本类允许只有 bucket 而无 path 的 URI， 以适配 Iceberg 表
 * location 可能仅指向 bucket 根的场景。构造时拆分 scheme/path/query/fragment 并缓存，后续读取零开销；对非 {@code gs} scheme 尽早抛
 * {@link ValidationException}。
 */
class GCSLocation {
  private static final String SCHEME_DELIM = "://";
  private static final String PATH_DELIM = "/";
  private static final String QUERY_DELIM = "\\?";
  private static final String FRAGMENT_DELIM = "#";

  private static final String EXPECTED_SCHEME = "gs";

  private final String bucket;
  private final String prefix;

  /**
   * 根据形如 scheme://bucket/path?query#fragment 的 URI 构造 {@link GCSLocation}。
   *
   * <p>逻辑：按 "://" 拆分并校验 scheme 为 {@code gs}；按首个 "/" 拆分 bucket 与 path； 剥离 path 中的 query 与 fragment
   * 得到 prefix。
   *
   * @param location 完全限定的 URI
   */
  GCSLocation(String location) {
    Preconditions.checkArgument(location != null, "Invalid location: null");

    String[] schemeSplit = location.split(SCHEME_DELIM, -1);
    ValidationException.check(
        schemeSplit.length == 2, "Invalid GCS URI, cannot determine scheme: %s", location);

    String scheme = schemeSplit[0];
    ValidationException.check(
        EXPECTED_SCHEME.equals(scheme), "Invalid GCS URI, invalid scheme: %s", scheme);

    String[] authoritySplit = schemeSplit[1].split(PATH_DELIM, 2);

    this.bucket = authoritySplit[0];

    // Strip query and fragment if they exist
    String path = authoritySplit.length > 1 ? authoritySplit[1] : "";
    path = path.split(QUERY_DELIM, -1)[0];
    path = path.split(FRAGMENT_DELIM, -1)[0];
    this.prefix = path;
  }

  /** 返回 GCS bucket 名称。 */
  public String bucket() {
    return bucket;
  }

  /** 返回 GCS 对象名前缀。 */
  public String prefix() {
    return prefix;
  }
}
