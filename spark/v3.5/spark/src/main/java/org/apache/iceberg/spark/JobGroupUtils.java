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
package org.apache.iceberg.spark;

import java.util.function.Supplier;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkContext$;
import org.apache.spark.api.java.JavaSparkContext;

/**
 * Spark 作业组（job group）信息工具类：读写并临时切换作业组上下文。
 *
 * <p>所属模块：iceberg-spark（核心包，服务于 Iceberg 动作在 Spark 中的可观测性与取消控制）。
 *
 * <p>职责：在 Spark {@link SparkContext} 的本地属性中读取/设置作业组 ID、描述、 取消时是否中断等属性，并提供在指定作业组上下文中执行代码块的工具方法。
 *
 * <p>设计意图：Spark 通过线程本地属性区分作业组，便于在 UI 中追踪与按组取消。 Iceberg 的表维护动作（如重写、过期快照）使用此类把整个动作标记为独立作业组。 {@link
 * #withJobGroupInfo} 采用 try-finally 保存并恢复先前作业组信息，保证嵌套调用不互相污染。
 *
 * <p>上下游关系：被各 Spark Action（如 RewriteDataFiles、ExpireSnapshots）调用。
 */
public class JobGroupUtils {

  private static final String JOB_GROUP_ID = SparkContext$.MODULE$.SPARK_JOB_GROUP_ID();
  private static final String JOB_GROUP_DESC = SparkContext$.MODULE$.SPARK_JOB_DESCRIPTION();
  private static final String JOB_INTERRUPT_ON_CANCEL =
      SparkContext$.MODULE$.SPARK_JOB_INTERRUPT_ON_CANCEL();

  private JobGroupUtils() {}

  /**
   * 从 SparkContext 本地属性中读取当前作业组信息。
   *
   * @param sparkContext Spark 上下文
   * @return 当前作业组信息
   */
  public static JobGroupInfo getJobGroupInfo(SparkContext sparkContext) {
    String groupId = sparkContext.getLocalProperty(JOB_GROUP_ID);
    String description = sparkContext.getLocalProperty(JOB_GROUP_DESC);
    String interruptOnCancel = sparkContext.getLocalProperty(JOB_INTERRUPT_ON_CANCEL);
    return new JobGroupInfo(groupId, description, Boolean.parseBoolean(interruptOnCancel));
  }

  /**
   * 将作业组信息写入 SparkContext 本地属性。
   *
   * @param sparkContext Spark 上下文
   * @param info 要设置的作业组信息
   */
  public static void setJobGroupInfo(SparkContext sparkContext, JobGroupInfo info) {
    sparkContext.setLocalProperty(JOB_GROUP_ID, info.groupId());
    sparkContext.setLocalProperty(JOB_GROUP_DESC, info.description());
    sparkContext.setLocalProperty(
        JOB_INTERRUPT_ON_CANCEL, String.valueOf(info.interruptOnCancel()));
  }

  /**
   * 在指定作业组上下文中执行代码块（{@link JavaSparkContext} 重载）。
   *
   * @param sparkContext Spark Java 上下文
   * @param info 作业组信息
   * @param supplier 要执行的代码块
   * @param <T> 返回类型
   * @return 代码块的返回值
   */
  public static <T> T withJobGroupInfo(
      JavaSparkContext sparkContext, JobGroupInfo info, Supplier<T> supplier) {
    return withJobGroupInfo(sparkContext.sc(), info, supplier);
  }

  /**
   * 在指定作业组上下文中执行代码块，执行完毕后恢复原有作业组信息。
   *
   * <p>逻辑：先保存当前作业组信息，设置新信息后执行 supplier，无论成功与否都在 finally 中恢复先前作业组信息。
   *
   * @param sparkContext Spark 上下文
   * @param info 作业组信息
   * @param supplier 要执行的代码块
   * @param <T> 返回类型
   * @return 代码块的返回值
   */
  public static <T> T withJobGroupInfo(
      SparkContext sparkContext, JobGroupInfo info, Supplier<T> supplier) {
    JobGroupInfo previousInfo = getJobGroupInfo(sparkContext);
    try {
      setJobGroupInfo(sparkContext, info);
      return supplier.get();
    } finally {
      setJobGroupInfo(sparkContext, previousInfo);
    }
  }
}
