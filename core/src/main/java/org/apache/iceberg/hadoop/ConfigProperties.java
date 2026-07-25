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

/**
 * 文件级说明：Iceberg 与 Hadoop/Hive 引擎集成相关的配置项常量定义。
 *
 * <p>所属模块：iceberg-core。该模块位于 iceberg-api 之下，提供具体的元数据存储、文件 IO、 引擎集成等实现；本类属于其中的 hadoop 包，负责对接基于 HDFS
 * 的存储与 Hive 引擎。
 *
 * <p>职责：集中声明影响 Hive 引擎行为与 Hive 锁行为的配置项 key，供 {@code HadoopCatalog}、{@code HadoopTables} 等组件读取。
 *
 * <p>设计意图：将散落的配置 key 字符串集中到一处，避免字面量重复，便于检索与统一修改。 仅作为常量持有者，私有构造方法禁止实例化。
 *
 * <p>上下游关系：被 iceberg-hive / iceberg-core 内的 Hive 集成代码引用。
 */
public class ConfigProperties {

  private ConfigProperties() {}

  /** 是否启用 Hive 引擎，值为 "iceberg.engine.hive.enabled"。 */
  public static final String ENGINE_HIVE_ENABLED = "iceberg.engine.hive.enabled";

  /** 是否启用 Hive 引擎的表锁机制，值为 "iceberg.engine.hive.lock-enabled"。 */
  public static final String LOCK_HIVE_ENABLED = "iceberg.engine.hive.lock-enabled";

  /** 删除 Hive 表时是否保留其统计信息，值为 "iceberg.hive.keep.stats"。 */
  public static final String KEEP_HIVE_STATS = "iceberg.hive.keep.stats";
}
