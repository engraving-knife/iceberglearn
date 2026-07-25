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
package org.apache.iceberg.actions;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.apache.iceberg.io.SupportsBulkOperations;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * 删除表中孤儿（orphan）元数据、数据及删除文件的动作。
 *
 * <p>所属模块：iceberg-api。继承自 {@link Action}，用于表存储层的空间回收与清理。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>列出底层存储中的实际文件，与表有效快照可达的文件集合比对，找出"孤儿文件"并删除。
 *   <li>提供安全阈值（olderThan）、自定义删除函数、并行删除执行器等可配置项。
 *   <li>处理路径前缀（scheme/authority）不一致的冲突场景。
 * </ul>
 *
 * <p>设计意图：孤儿文件判定需要列出存储目录，开销较大，故作为独立 Action 而非常规表操作。 通过 olderThan 默认 3
 * 天的安全阈值，避免误删并发写入尚未被元数据引用的新文件。prefix mismatch 相关配置用于应对同文件在不同 scheme/authority（如
 * s3a/s3/s3n）下的等价判定问题， 默认 ERROR 模式保证安全，仅在人工确认后切换为 DELETE。
 *
 * <p>上下游关系：由引擎模块实现；删除执行依赖 {@link org.apache.iceberg.io.FileIO} 或自定义 deleteFunc；结果通过 {@link Result}
 * 返回被删除文件位置。
 */
public interface DeleteOrphanFiles extends Action<DeleteOrphanFiles, DeleteOrphanFiles.Result> {
  /**
   * 指定扫描孤儿文件的存储位置。
   *
   * <p>若未设置，则扫描表根目录，可能同时删除孤儿数据文件与元数据文件。
   *
   * @param location 待扫描的存储位置
   * @return this，便于链式调用
   */
  DeleteOrphanFiles location(String location);

  /**
   * 仅删除早于给定时间戳的孤儿文件。
   *
   * <p>这是一项安全措施，避免删除正在被并发写入但尚未被元数据引用的新文件。若未设置，默认取 3 天前的时间戳。
   *
   * @param olderThanTimestamp 时间戳，单位毫秒，由 {@link System#currentTimeMillis()} 返回
   * @return this，便于链式调用
   */
  DeleteOrphanFiles olderThan(long olderThanTimestamp);

  /**
   * 指定用于删除孤儿文件的自定义删除函数。
   *
   * <p>允许调用方自定义删除行为，例如仅收集孤儿文件路径而不真正物理删除。若未设置，默认使用 表的 {@link org.apache.iceberg.io.FileIO io} 实现。
   *
   * @param deleteFunc 接收文件路径的删除函数
   * @return this，便于链式调用
   */
  DeleteOrphanFiles deleteWith(Consumer<String> deleteFunc);

  /**
   * 指定用于删除孤儿文件的替代执行器服务。
   *
   * <p>仅当通过 {@link #deleteWith(Consumer)} 提供自定义删除函数、或 FileIO 不 {@link SupportsBulkOperations
   * 支持批量删除}时才会使用该执行器；否则并行度由 IO 专属的 {@link SupportsBulkOperations#deleteFiles(Iterable) deleteFiles}
   * 控制。若未调用且不支持 批量删除，孤儿清单与数据文件仍会在当前线程被删除。
   *
   * @param executorService 使用的执行器服务
   * @return this，便于链式调用
   */
  DeleteOrphanFiles executeDeleteWith(ExecutorService executorService);

  /**
   * 指定前缀不匹配（prefix mismatch）模式，决定当元数据引用的文件与列出文件除 authority/scheme 外完全一致时的处理方式。
   *
   * <p>可选值为 "ERROR"、"IGNORE"、"DELETE"。默认为 "ERROR"：只要 authority/scheme 不一致即抛
   * 异常，这是推荐模式，仅在极少数情况下修改。若出现不匹配，可先通过 {@link #equalSchemes(Map)} 与 {@link #equalAuthorities(Map)}
   * 提供等价 scheme/authority 来 解决冲突；若无法判断是否等价，可设为 "IGNORE" 跳过这些文件；若已人工核对全部冲突、提供等价 映射且确信剩余确属不同，可设为
   * "DELETE" 将不匹配文件视为孤儿删除。删除后不可恢复，"DELETE" 模式须极度谨慎使用。
   *
   * @param newPrefixMismatchMode 前缀不匹配处理模式
   * @return this，便于链式调用
   */
  default DeleteOrphanFiles prefixMismatchMode(PrefixMismatchMode newPrefixMismatchMode) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement prefixMismatchMode");
  }

  /**
   * 指定应被视为等价的 scheme 集合。
   *
   * <p>Map 的 key 可包含逗号分隔的多个 scheme，例如 Map("s3a,s3,s3n", "s3")。
   *
   * @param newEqualSchemes 等价 scheme 映射
   * @return this，便于链式调用
   */
  default DeleteOrphanFiles equalSchemes(Map<String, String> newEqualSchemes) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement equalSchemes");
  }

  /**
   * 指定应被视为等价的 authority 集合。
   *
   * <p>Map 的 key 可包含逗号分隔的多个 authority，例如 Map("s1name,s2name", "servicename")。
   *
   * @param newEqualAuthorities 等价 authority 映射
   * @return this，便于链式调用
   */
  default DeleteOrphanFiles equalAuthorities(Map<String, String> newEqualAuthorities) {
    throw new UnsupportedOperationException(
        this.getClass().getName() + " does not implement equalAuthorities");
  }

  /** 动作执行结果，包含执行摘要。 */
  interface Result {
    /**
     * 返回被删除的孤儿文件位置。
     *
     * @return 孤儿文件路径可迭代集合
     */
    Iterable<String> orphanFileLocations();
  }

  /**
   * 定义路径前缀（scheme/authority）不匹配时动作行为的枚举。
   *
   * <p>{@link #ERROR} 抛出异常；{@link #IGNORE} 跳过不处理；{@link #DELETE} 删除文件。
   */
  enum PrefixMismatchMode {
    ERROR,
    IGNORE,
    DELETE;

    /**
     * 将字符串解析为 {@link PrefixMismatchMode}。
     *
     * <p>逻辑：先校验入参非 null，再以英文大写形式匹配枚举常量；匹配失败时抛出 {@link IllegalArgumentException}，异常信息包含原始输入。
     *
     * @param modeAsString 模式字符串
     * @return 解析得到的枚举值
     * @throws IllegalArgumentException 当入参为 null 或无法匹配时
     */
    public static PrefixMismatchMode fromString(String modeAsString) {
      Preconditions.checkArgument(modeAsString != null, "Invalid mode: null");
      try {
        return PrefixMismatchMode.valueOf(modeAsString.toUpperCase(Locale.ENGLISH));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(String.format("Invalid mode: %s", modeAsString), e);
      }
    }
  }
}
