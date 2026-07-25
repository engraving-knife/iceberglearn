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
 * Flink 引擎下 Iceberg 表维护动作的统一入口工厂。
 *
 * <p>所属模块：iceberg-flink（Iceberg 与 Flink 引擎的集成层，位于 iceberg-core 之上， 为 Flink SQL/Table API 及
 * DataStream API 提供 Iceberg 表的读写与维护能力）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>持有 {@link StreamExecutionEnvironment} 与目标 {@link Table}，作为创建各类维护动作的上下文。
 *   <li>提供 {@link #forTable(Table)} / {@link #forTable(StreamExecutionEnvironment, Table)} 两种构造方式，
 *       屏蔽 Flink 环境配置细节。
 *   <li>暴露 {@link #rewriteDataFiles()} 入口，构造数据文件重写动作。
 * </ul>
 *
 * <p>设计意图：将环境与表这一对必要参数集中持有，避免每个 Action 类各自重复管理； 同时通过 {@link #CONFIG} 关闭 Flink 的 classloader 泄漏检查，因为
 * Avro 序列化器会在内部缓存 类/对象，触发该检查会误报。使用静态 Configuration 单例保证全局一致。
 *
 * <p>上下游关系：被用户作业（Flink Job）直接调用；下游创建 {@link RewriteDataFilesAction} 执行具体的小文件合并/数据压缩任务。
 */
public class Actions {

  /**
   * 默认 Flink 配置：关闭 {@code CHECK_LEAKED_CLASSLOADER} 检查。
   *
   * <p>设计要点：Avro 序列化器内部会缓存类与对象引用，Flink 默认的 classloader 泄漏检查 会因此抛出异常，故在此全局关闭。
   */
  public static final Configuration CONFIG =
      new Configuration()
          // disable classloader check as Avro may cache class/object in the serializers.
          .set(CoreOptions.CHECK_LEAKED_CLASSLOADER, false);

  private StreamExecutionEnvironment env;
  private Table table;

  private Actions(StreamExecutionEnvironment env, Table table) {
    this.env = env;
    this.table = table;
  }

  /**
   * 基于指定的 Flink 执行环境与 Iceberg 表创建 Actions 入口。
   *
   * @param env Flink 流执行环境，由调用方负责配置
   * @param table 目标 Iceberg 表
   * @return Actions 实例
   */
  public static Actions forTable(StreamExecutionEnvironment env, Table table) {
    return new Actions(env, table);
  }

  /**
   * 使用默认 Flink 执行环境（基于 {@link #CONFIG}）创建 Actions 入口。
   *
   * @param table 目标 Iceberg 表
   * @return Actions 实例
   */
  public static Actions forTable(Table table) {
    return new Actions(StreamExecutionEnvironment.getExecutionEnvironment(CONFIG), table);
  }

  /**
   * 创建数据文件重写动作，用于合并小文件、压缩数据文件以优化查询性能。
   *
   * @return {@link RewriteDataFilesAction} 实例，可进一步链式配置后提交执行
   */
  public RewriteDataFilesAction rewriteDataFiles() {
    return new RewriteDataFilesAction(env, table);
  }
}
