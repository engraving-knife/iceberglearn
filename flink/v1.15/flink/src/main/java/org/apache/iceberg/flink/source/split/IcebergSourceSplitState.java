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
package org.apache.iceberg.flink.source.split;

/**
 * Iceberg Source 分片状态，记录分片读取进度。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：维护当前分片的已读位置与状态枚举，支持 checkpoint。
 *
 * <p>设计意图：可变状态对象；被 SourceReader 维护。
 */
public class IcebergSourceSplitState {
  private final IcebergSourceSplit split;
  private final IcebergSourceSplitStatus status;

  public IcebergSourceSplitState(IcebergSourceSplit split, IcebergSourceSplitStatus status) {
    this.split = split;
    this.status = status;
  }

  public IcebergSourceSplit split() {
    return split;
  }

  public IcebergSourceSplitStatus status() {
    return status;
  }
}
