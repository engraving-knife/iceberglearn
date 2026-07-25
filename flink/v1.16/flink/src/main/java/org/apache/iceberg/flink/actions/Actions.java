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
package org.apache.iceberg.flink.actions;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CoreOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.iceberg.Table;

/**
 * Iceberg Flink 操作入口，用于在 Flink 环境中对 Iceberg 表执行数据压缩等操作。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：作为 actions 子模块的工厂门面，把 {@link StreamExecutionEnvironment} 与 {@link
 * Table} 绑定，并暴露 {@link RewriteDataFilesAction} 等具体动作的构造入口。
 *
 * <p>设计意图：静态工厂 + 门面模式，屏蔽底层 Iceberg 动作的构造细节。 上下游：被用户作业或上层工具调用，向下构造 {@link RewriteDataFilesAction}。
 */
public class Actions {

  /** Flink 全局配置，关闭 classloader 泄漏检查，因为 Avro 序列化器会缓存类/对象， 否则会在作业结束时抛出 ClassLoader 泄漏异常。 */
  public static final Configuration CONFIG =
      new Configuration()
          // disable classloader check as Avro may cache class/object in the serializers.
          .set(CoreOptions.CHECK_LEAKED_CLASSLOADER, false);

  private StreamExecutionEnvironment env;
  private Table table;

  /** 私有构造，绑定 Flink 执行环境与目标 Iceberg 表。 */
  private Actions(StreamExecutionEnvironment env, Table table) {
    this.env = env;
    this.table = table;
  }

  /** 基于显式 Flink 环境与表构造 Actions 入口。 */
  public static Actions forTable(StreamExecutionEnvironment env, Table table) {
    return new Actions(env, table);
  }

  /** 基于默认 Flink 执行环境（使用上面的 {@link #CONFIG}）构造 Actions 入口。 */
  public static Actions forTable(Table table) {
    return new Actions(StreamExecutionEnvironment.getExecutionEnvironment(CONFIG), table);
  }

  /** 创建并返回数据文件压缩重写动作。 */
  public RewriteDataFilesAction rewriteDataFiles() {
    return new RewriteDataFilesAction(env, table);
  }
}
