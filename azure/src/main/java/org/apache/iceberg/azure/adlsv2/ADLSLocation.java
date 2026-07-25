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
package org.apache.iceberg.azure.adlsv2;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Azure Data Lake Storage Gen2（ADLSv2）位置解析类，不可变值对象。
 *
 * <p>所属模块：iceberg-azure。职责：把 Azure ADLSv2 的 URI 解析为 storage account、container、 path 三个分量，供 {@code
 * ADLSFileIO} 定位对象使用。
 *
 * <p>设计意图：位置遵循 Hadoop Azure 约定，格式为：
 *
 * <pre>{@code abfs[s]://[<container>@]<storage account host>/<file path>}</pre>
 *
 * 构造时用正则一次性解析并缓存各分量，后续读取零开销；对非法 scheme/空 location 尽早抛 {@link ValidationException}。详见 <a
 * href="https://hadoop.apache.org/docs/stable/hadoop-azure/abfs.html">Hadoop Azure Support</a>
 */
class ADLSLocation {
  private static final Pattern URI_PATTERN = Pattern.compile("^abfss?://([^/?#]+)(.*)?$");

  private final String storageAccount;
  private final String container;
  private final String path;

  /**
   * 根据完全限定 URI 构造 {@link ADLSLocation}。
   *
   * <p>逻辑：用 {@link #URI_PATTERN} 正则匹配 location，提取 authority 与 path； authority 按 "@" 拆分为 container 与
   * storage account（无 "@" 时 container 为 null）； path 去除前导 "/" 并剥离 query/fragment。
   *
   * @param location 完全限定的 ADLS URI
   */
  ADLSLocation(String location) {
    Preconditions.checkArgument(location != null, "Invalid location: null");

    Matcher matcher = URI_PATTERN.matcher(location);

    ValidationException.check(matcher.matches(), "Invalid ADLS URI: %s", location);

    String authority = matcher.group(1);
    String[] parts = authority.split("@", -1);
    if (parts.length > 1) {
      this.container = parts[0];
      this.storageAccount = parts[1];
    } else {
      this.container = null;
      this.storageAccount = authority;
    }

    String uriPath = matcher.group(2);
    uriPath = uriPath == null ? "" : uriPath.startsWith("/") ? uriPath.substring(1) : uriPath;
    this.path = uriPath.split("\\?", -1)[0].split("#", -1)[0];
  }

  /** 返回 Azure storage account。 */
  public String storageAccount() {
    return storageAccount;
  }

  /** 返回 Azure container 名称（可能为空）。 */
  public Optional<String> container() {
    return Optional.ofNullable(container);
  }

  /** 返回 ADLS 对象路径。 */
  public String path() {
    return path;
  }
}
