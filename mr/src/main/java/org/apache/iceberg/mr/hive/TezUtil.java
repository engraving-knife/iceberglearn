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
package org.apache.iceberg.mr.hive;

import java.util.Objects;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobContext;
import org.apache.hadoop.mapred.JobContextImpl;
import org.apache.hadoop.mapred.TaskAttemptContext;
import org.apache.hadoop.mapred.TaskAttemptContextImpl;
import org.apache.hadoop.mapred.TaskAttemptID;
import org.apache.hadoop.mapreduce.JobID;

/**
 * 文件级说明：Tez 执行引擎与 MapReduce 接口的适配工具。
 *
 * <p>所属模块：iceberg-mr（Hive/MapReduce 集成模块；本类位于 hive 子包，专门处理 Tez 与 MR 在 TaskAttemptID / JobID
 * 表示上的差异，使 Iceberg committer 能在 Tez 上正常工作）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供 enrichContextWithVertexId / enrichContextWithAttemptWrapper，把 Tez 的 vertex id 信息附加到
 *       JobID 或 TaskAttemptID 上。
 *   <li>提供 {@link TaskAttemptWrapper}：重写 equals/hashCode，使 mapper 与 reducer 线程能 用同一个 attempt ID 作为
 *       key 复用 {@link HiveIcebergRecordWriter} 缓存。
 * </ul>
 *
 * <p>设计意图：Tez 的 MROutput 在构造 TaskAttemptID 时会把 vertex id 拼到 JobID 末尾，而 mapper 中拿到的 TaskAttemptID 不含
 * vertex id，导致两者无法匹配同一个 writer 缓存 key。 通过 {@link TaskAttemptWrapper} 把 vertex id
 * 统一附加并重写相等性判断，解决该不一致问题。
 *
 * <p>上下游关系：上游由 {@link HiveIcebergOutputCommitter}、{@link HiveIcebergOutputFormat} 调用；下游不依赖其他模块。
 */
public class TezUtil {

  private static final String TASK_ATTEMPT_ID_KEY = "mapred.task.id";
  // TezProcessor (Hive) propagates the vertex id under this key - available during Task commit
  // phase
  private static final String TEZ_VERTEX_ID_HIVE = "hive.tez.vertex.index";
  // MROutputCommitter (Tez) propagates the vertex id under this key - available during DAG/Vertex
  // commit phase
  private static final String TEZ_VERTEX_ID_DAG = "mapreduce.task.vertex.id";

  /**
   * 若配置中存在 Tez vertex id，则创建一个追加了 vertex id 的新 JobContext。
   *
   * <p>设计原因详见 {@link TaskAttemptWrapper} 文档第 1 点。
   *
   * @param jobContext 原 JobContext
   * @return 已附加 vertex id 的 JobContext（无 vertex id 时原样返回）
   */
  public static JobContext enrichContextWithVertexId(JobContext jobContext) {
    String vertexId = jobContext.getJobConf().get(TEZ_VERTEX_ID_DAG);
    if (vertexId != null) {
      JobID jobID = getJobIDWithVertexAppended(jobContext.getJobID(), vertexId);
      return new JobContextImpl(jobContext.getJobConf(), jobID, jobContext.getProgressible());
    } else {
      return jobContext;
    }
  }

  /**
   * 用 {@link TaskAttemptWrapper} 包装 TaskAttemptID，构造新的 TaskAttemptContext。
   *
   * <p>设计原因详见 {@link TaskAttemptWrapper} 文档第 2 点。
   *
   * @param taskAttemptContext 原 TaskAttemptContext
   * @return 包装后的 TaskAttemptContext
   */
  public static TaskAttemptContext enrichContextWithAttemptWrapper(
      TaskAttemptContext taskAttemptContext) {
    TaskAttemptID wrapped = TezUtil.taskAttemptWrapper(taskAttemptContext.getTaskAttemptID());
    return new TaskAttemptContextImpl(taskAttemptContext.getJobConf(), wrapped);
  }

  /** 用 {@link TaskAttemptWrapper} 包装给定 TaskAttemptID（不带 vertex id）。 */
  public static TaskAttemptID taskAttemptWrapper(TaskAttemptID attemptID) {
    return new TaskAttemptWrapper(attemptID, "");
  }

  /**
   * 从 JobConf 中读取 TaskAttemptID 与 Tez vertex id，构造 {@link TaskAttemptWrapper}。
   *
   * @param jc JobConf，需包含 mapred.task.id 与 hive.tez.vertex.index
   * @return 包装后的 TaskAttemptID
   */
  public static TaskAttemptID taskAttemptWrapper(JobConf jc) {
    return new TaskAttemptWrapper(
        TaskAttemptID.forName(jc.get(TASK_ATTEMPT_ID_KEY)), jc.get(TEZ_VERTEX_ID_HIVE));
  }

  /**
   * 把 vertex id 追加到 JobID 的 jtIdentifier 末尾，生成新 JobID。
   *
   * @param jobID 原 JobID
   * @param vertexId Tez vertex id
   * @return 追加 vertex id 后的 JobID（vertexId 为空时原样返回）
   */
  private static JobID getJobIDWithVertexAppended(JobID jobID, String vertexId) {
    if (vertexId != null && !vertexId.isEmpty()) {
      return new JobID(jobID.getJtIdentifier() + vertexId, jobID.getId());
    } else {
      return jobID;
    }
  }

  private TezUtil() {}

  /**
   * {@link TaskAttemptID} 子类，解决 Tez 与 MR 之间 attempt ID 表示不一致与缓存 key 复用问题。
   *
   * <p>两个核心目的：
   *
   * <ol>
   *   <li>允许把可选的 vertex id 追加到 JobID 末尾。Tez 的 MROutput 在构造 attempt ID 时 会把 vertex id 拼到 JobID 末尾，而
   *       mapper 中拿到的 attempt ID 不含 vertex id， 导致创建并缓存 writer 的 mapper 与提交阶段的 committer 拿到不同
   *       attempt ID。
   *   <li>重写 equals/hashCode，使 task type（map/reduce）不参与比较，从而 mapper 与 reducer 线程能用同一个 attempt ID 作为
   *       key 取到缓存的 {@link HiveIcebergRecordWriter}。
   * </ol>
   */
  private static class TaskAttemptWrapper extends TaskAttemptID {

    TaskAttemptWrapper(TaskAttemptID attemptID, String vertexId) {
      super(
          getJobIDWithVertexAppended(attemptID.getJobID(), vertexId).getJtIdentifier(),
          attemptID.getJobID().getId(),
          attemptID.getTaskType(),
          attemptID.getTaskID().getId(),
          attemptID.getId());
    }

    /** 相等性比较：仅比较 attempt id、task id、job id，忽略 task type。 */
    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TaskAttemptWrapper that = (TaskAttemptWrapper) o;
      return getId() == that.getId()
          && getTaskID().getId() == that.getTaskID().getId()
          && Objects.equals(getJobID(), that.getJobID());
    }

    /** 哈希：基于 attempt id、task id、job id，忽略 task type。 */
    @Override
    public int hashCode() {
      return Objects.hash(getId(), getTaskID().getId(), getJobID());
    }
  }
}
