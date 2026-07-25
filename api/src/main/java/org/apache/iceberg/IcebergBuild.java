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
package org.apache.iceberg;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.apache.iceberg.relocated.com.google.common.base.Splitter;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 加载 iceberg-build.properties 构建信息工具类。
 *
 * <p>所属模块：iceberg-api（构建/版本元信息层）。
 *
 * <p>职责：从 classpath 读取 {@code iceberg-build.properties}，解析其中由 git-commit-id-plugin
 * 注入的构建信息（commit、branch、tags、version 等），并对外提供查询方法。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>懒加载 + 双检锁：仅在首次访问任一查询方法时才加载 properties，避免启动开销； 通过 {@code volatile isLoaded} 保证多线程下只加载一次。
 *   <li>加载失败时回退为 "unknown"，保证运行不中断。
 * </ul>
 *
 * <p>上下游关系：被各模块用于打印版本信息或运行时诊断；属性文件由 maven 构建时生成。
 */
public class IcebergBuild {
  private IcebergBuild() {}

  private static final Logger LOG = LoggerFactory.getLogger(IcebergBuild.class);
  private static final String VERSION_PROPERTIES_FILE = "/iceberg-build.properties";
  private static final String UNKNOWN_DEFAULT = "unknown";

  private static volatile boolean isLoaded = false;

  private static String shortId; // 10 character short git hash of the build
  private static String commitId; // 40 character full git hash of the build
  private static String branch;
  private static List<String> tags;
  private static String version;
  private static String fullVersion;

  /**
   * 加载本模块的 version.properties 文件并填充各静态字段。
   *
   * <p>逻辑：读取 classpath 上的 {@code iceberg-build.properties}，逐项解析 git commit、
   * branch、tags（逗号分隔）、version；tags 为空时返回空列表；最后拼出 fullVersion。 加载异常仅记录日志，不抛出。
   */
  public static void loadBuildInfo() {
    Properties buildProperties = new Properties();
    try (InputStream is = readResource(VERSION_PROPERTIES_FILE)) {
      buildProperties.load(is);
    } catch (Exception e) {
      LOG.warn("Failed to load version properties from {}", VERSION_PROPERTIES_FILE, e);
    }

    IcebergBuild.shortId = buildProperties.getProperty("git.commit.id.abbrev", UNKNOWN_DEFAULT);
    IcebergBuild.commitId = buildProperties.getProperty("git.commit.id", UNKNOWN_DEFAULT);
    IcebergBuild.branch = buildProperties.getProperty("git.branch", UNKNOWN_DEFAULT);
    String tagList = buildProperties.getProperty("git.tags", "");
    if (!tagList.isEmpty()) {
      IcebergBuild.tags = ImmutableList.copyOf(Splitter.on(",").split(tagList));
    } else {
      IcebergBuild.tags = ImmutableList.of();
    }
    IcebergBuild.version = buildProperties.getProperty("git.build.version", UNKNOWN_DEFAULT);
    IcebergBuild.fullVersion = String.format("Apache Iceberg %s (commit %s)", version, commitId);
  }

  /** 返回构建对应的完整 git commit ID（40 字符）。 */
  public static String gitCommitId() {
    ensureLoaded();
    return commitId;
  }

  /** 返回构建对应的短 git commit ID（10 字符）。 */
  public static String gitCommitShortId() {
    ensureLoaded();
    return shortId;
  }

  /** 返回构建对应的 git 分支名。 */
  public static String gitBranch() {
    ensureLoaded();
    return branch;
  }

  /** 返回构建对应的 git tag 列表。 */
  public static List<String> gitTags() {
    ensureLoaded();
    return tags;
  }

  /** 返回 Iceberg 构建版本号字符串。 */
  public static String version() {
    ensureLoaded();
    return version;
  }

  /** 返回包含版本与 commit 的完整版本描述（如 "Apache Iceberg x.y.z (commit abc...)"）。 */
  public static String fullVersion() {
    ensureLoaded();
    return fullVersion;
  }

  /**
   * 确保构建信息已加载：若未加载则用双检锁触发 {@link #loadBuildInfo()}。
   *
   * <p>逻辑：先检查 volatile {@code isLoaded}，未加载时进入 synchronized 块再次检查， 仍为 false 时调用 {@link
   * #loadBuildInfo()} 并置位。保证多线程下仅加载一次。
   */
  private static void ensureLoaded() {
    if (!isLoaded) {
      synchronized (IcebergBuild.class) {
        if (!isLoaded) {
          loadBuildInfo();
          isLoaded = true;
        }
      }
    }
  }

  /**
   * 以字节流方式读取 classpath 资源。
   *
   * @param resourceName 资源名
   * @return 资源输入流
   * @throws IOException 资源不存在或读取失败
   */
  private static InputStream readResource(String resourceName) throws IOException {
    return Resources.asByteSource(Resources.getResource(IcebergBuild.class, resourceName))
        .openStream();
  }
}
