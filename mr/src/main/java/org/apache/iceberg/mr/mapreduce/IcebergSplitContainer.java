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
package org.apache.iceberg.mr.mapreduce;

/**
 * 文件级说明：标记接口，表示持有内部 {@link IcebergSplit} 的切分容器。
 *
 * <p>所属模块：iceberg-mr（mapreduce 子包；为需要在外层 split（如 Hive FileSplit）中 嵌入 IcebergSplit 的实现提供统一访问契约）。
 *
 * <p>职责：定义 {@link #icebergSplit()} 方法，让外层 split 实现该接口即可暴露内部的 IcebergSplit，供 RecordReader 取用。
 *
 * <p>设计意图：Hive 等引擎要求 split 是特定 FileSplit 子类，因此 Iceberg 用外层包装类 携带 Hive 要求的字段，同时实现本接口以暴露真正的 Iceberg
 * 切分。
 *
 * <p>上下游关系：上游由 {@link org.apache.iceberg.mr.hive.HiveIcebergSplit} 实现； 被 {@link
 * org.apache.iceberg.mr.hive.HiveIcebergInputFormat#getRecordReader} 等调用。
 */
public interface IcebergSplitContainer {

  /** 返回内部封装的 Iceberg 切分。 */
  IcebergSplit icebergSplit();
}
