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
package org.apache.iceberg.flink.source.assigner;

import org.apache.flink.annotation.Internal;
import org.apache.flink.util.Preconditions;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;

/**
 * 文件级说明：Split 分配请求的结果。
 *
 * <p>所属模块：iceberg-flink（source/assigner 子包），表示 {@link SplitAssigner#getNext} 的返回值。
 *
 * <p>职责：封装 split 分配的三种状态——AVAILABLE（有可用 split）、CONSTRAINED（有待分配 split 但受约束）、 UNAVAILABLE（无待分配
 * split）。
 *
 * <p>设计意图：使用不可变值对象 + 静态工厂方法模式。UNAVAILABLE 和 CONSTRAINED 为单例， AVAILABLE 携带具体的 split。状态机明确，避免 null
 * 返回值的歧义。
 *
 * <p>上下游关系：由 {@link SplitAssigner} 返回；被 enumerator 消费以决定是否继续分配或等待。
 */
@Internal
public class GetSplitResult {

  public enum Status {
    AVAILABLE,

    /** 有待分配的 split，但因约束（如 event time 对齐）暂不能分配。 */
    CONSTRAINED,

    /** Assigner 没有待分配的 split。 */
    UNAVAILABLE
  }

  private final Status status;
  private final IcebergSourceSplit split;

  private GetSplitResult(Status status) {
    this.status = status;
    this.split = null;
  }

  private GetSplitResult(IcebergSourceSplit split) {
    Preconditions.checkNotNull(split, "Split cannot be null");
    this.status = Status.AVAILABLE;
    this.split = split;
  }

  public Status status() {
    return status;
  }

  public IcebergSourceSplit split() {
    return split;
  }

  private static final GetSplitResult UNAVAILABLE = new GetSplitResult(Status.UNAVAILABLE);
  private static final GetSplitResult CONSTRAINED = new GetSplitResult(Status.CONSTRAINED);

  /** 返回 UNAVAILABLE 结果（单例）。 */
  public static GetSplitResult unavailable() {
    return UNAVAILABLE;
  }

  /** 返回 CONSTRAINED 结果（单例）。 */
  public static GetSplitResult constrained() {
    return CONSTRAINED;
  }

  /** 返回 AVAILABLE 结果，携带指定的 split。 */
  public static GetSplitResult forSplit(IcebergSourceSplit split) {
    return new GetSplitResult(split);
  }
}
