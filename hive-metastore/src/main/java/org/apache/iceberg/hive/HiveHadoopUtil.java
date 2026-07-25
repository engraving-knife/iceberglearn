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
package org.apache.iceberg.hive;

import java.io.IOException;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hadoop 用户信息获取工具类。
 *
 * <p>所属模块：iceberg-hive-metastore（Hive Metastore 集成的辅助工具层）。
 *
 * <p>职责：获取当前 Hadoop 执行用户的短用户名，供 Hive Metastore 鉴权与表 owner 推断使用。
 *
 * <p>设计意图：Hive Metastore 的部分操作（如建表时设置 owner）依赖执行用户身份。 直接调用 {@link
 * UserGroupInformation#getCurrentUser()} 可能因 Kerberos 未初始化等原因失败， 因此提供一层兜底：失败时回退到 JVM 系统属性 {@code
 * user.name}，保证调用方始终能拿到非空用户名， 避免阻断主流程。
 *
 * <p>上下游关系：被本模块内 Hive Catalog 表操作相关流程调用（如设置表属性 owner）。
 */
public class HiveHadoopUtil {

  private static final Logger LOG = LoggerFactory.getLogger(HiveHadoopUtil.class);

  private HiveHadoopUtil() {}

  /**
   * 获取当前 Hadoop 用户的短用户名。
   *
   * <p>逻辑：优先通过 {@link UserGroupInformation#getCurrentUser()} 获取短用户名；若抛出 {@link IOException}（如 UGI
   * 未初始化），则告警并回退到 JVM 系统属性 {@code user.name}。
   *
   * @return 当前用户名（保证非空，最坏情况返回 user.name 系统属性）
   */
  public static String currentUser() {
    String username = null;
    try {
      username = UserGroupInformation.getCurrentUser().getShortUserName();
    } catch (IOException e) {
      LOG.warn("Failed to get Hadoop user", e);
    }

    if (username != null) {
      return username;
    } else {
      LOG.warn("Hadoop user is null, defaulting to user.name");
      return System.getProperty("user.name");
    }
  }
}
