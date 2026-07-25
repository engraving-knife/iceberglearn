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
package org.apache.iceberg.aliyun.oss;

import com.aliyun.oss.internal.OSSUtils;
import java.util.Set;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableSet;

/**
 * 模块：aliyun-oss，属于 Iceberg 存储接入层。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>表示阿里云 OSS 上一个完全限定的资源位置（URI），封装 bucket 与 object key
 *   <li>解析并校验 OSS URI 的合法性（scheme、bucket、key）
 *   <li>屏蔽 Hadoop Path 实现可能引入的编码差异，保证 Iceberg 读写路径一致
 * </ul>
 *
 * <p>设计意图：直接基于字符串解析而非 {@link java.net.URI}，避免 Hadoop Path 在编码处理上的不一致； 仅支持 https 与 oss 两种
 * scheme，不支持已废弃的 path-style 访问方式。该类为不可变对象，线程安全。
 *
 * <p>上下游关系：被 {@link org.apache.iceberg.aliyun.oss.OSSFileIO} 等阿里云 OSS 文件系统实现使用， 用于将 Iceberg 中的
 * location 字符串转换为可操作的 bucket/key。
 *
 * <p>Note: Path-style access is deprecated and not supported by this implementation.
 */
public class OSSURI {
  private static final String SCHEME_DELIM = "://";
  private static final String PATH_DELIM = "/";
  private static final String QUERY_DELIM = "\\?";
  private static final String FRAGMENT_DELIM = "#";
  private static final Set<String> VALID_SCHEMES = ImmutableSet.of("https", "oss");
  private final String location;
  private final String bucket;
  private final String key;

  /**
   * 根据传入的 location 字符串构造 {@link OSSURI}，并解析出 bucket 与 object key。
   *
   * <p>location 字符串遵循 RFC2396 语法：[scheme:][//bucket][object key][?query][#fragment]， 字符集限制参考阿里云 OSS
   * 文档：Bucket 与 Object 的合法命名。
   *
   * <p>支持的访问风格为 https://... 与 oss://... 两种 URI。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>校验 location 非空，并按 "://" 拆分得到 scheme 与剩余部分
   *   <li>校验 scheme 必须为 https 或 oss
   *   <li>按首个 "/" 拆分出 bucket 与 path，并校验两者均存在且非空
   *   <li>剥离 path 中的 query 与 fragment 部分，得到最终 object key
   *   <li>通过 {@link OSSUtils} 校验 bucket 名与 object key 的合法性
   * </ol>
   *
   * @param location 完全限定的 OSS URI 字符串
   */
  public OSSURI(String location) {
    Preconditions.checkNotNull(location, "OSS location cannot be null.");

    this.location = location;
    String[] schemeSplit = location.split(SCHEME_DELIM, -1);
    ValidationException.check(schemeSplit.length == 2, "Invalid OSS location: %s", location);

    String scheme = schemeSplit[0];
    ValidationException.check(
        VALID_SCHEMES.contains(scheme.toLowerCase()),
        "Invalid scheme: %s in OSS location %s",
        scheme,
        location);

    String[] authoritySplit = schemeSplit[1].split(PATH_DELIM, 2);
    ValidationException.check(
        authoritySplit.length == 2, "Invalid bucket or key in OSS location: %s", location);
    ValidationException.check(
        !authoritySplit[1].trim().isEmpty(), "Missing key in OSS location: %s", location);
    this.bucket = authoritySplit[0];
    OSSUtils.ensureBucketNameValid(bucket);

    // Strip query and fragment if they exist
    String path = authoritySplit[1];
    path = path.split(QUERY_DELIM, -1)[0];
    path = path.split(FRAGMENT_DELIM, -1)[0];
    this.key = path;
    OSSUtils.ensureObjectKeyValid(key);
  }

  /** 返回 OSS bucket 名称。 */
  public String bucket() {
    return bucket;
  }

  /** 返回 OSS object key 名称。 */
  public String key() {
    return key;
  }

  /** 返回原始未修改的 OSS URI 字符串。 */
  public String location() {
    return location;
  }

  @Override
  public String toString() {
    return location;
  }
}
