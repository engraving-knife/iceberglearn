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
package org.apache.iceberg.hadoop;

import java.io.Serializable;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

/**
 * 文件级说明：过滤"隐藏路径"的 {@link PathFilter} 实现。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：在列目录时排除以 {@code '_'} 或 {@code '.'} 开头的文件/目录， 与 Hadoop/Spark 等引擎对"临时文件/隐藏文件"的约定保持一致。
 *
 * <p>设计意图：采用饿汉式单例 + 私有构造方法，全局共享一个无状态过滤器实例，避免重复创建。 实现 {@link Serializable} 以便在分布式任务中随算子一起序列化传输。
 *
 * <p>上下游关系：被 {@link HadoopCatalog} 列表目录、{@link Util} 等场景使用； 通常作为 {@link FileSystem#listStatus(Path,
 * PathFilter)} 的过滤参数。
 */
public class HiddenPathFilter implements PathFilter, Serializable {

  private static final HiddenPathFilter INSTANCE = new HiddenPathFilter();

  private HiddenPathFilter() {}

  /** 获取全局唯一的 {@link HiddenPathFilter} 实例。 */
  public static HiddenPathFilter get() {
    return INSTANCE;
  }

  /**
   * 判断给定路径是否应被接受（即非隐藏路径）。
   *
   * <p>规则：路径名以 {@code '_'} 或 {@code '.'} 开头视为隐藏，返回 {@code false}； 否则返回 {@code true}。
   *
   * @param p 待判定的 Hadoop 路径
   * @return {@code true} 表示路径可见（应保留），{@code false} 表示隐藏（应过滤）
   */
  @Override
  public boolean accept(Path p) {
    return !p.getName().startsWith("_") && !p.getName().startsWith(".");
  }
}
