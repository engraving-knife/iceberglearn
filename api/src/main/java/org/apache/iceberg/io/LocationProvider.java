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
package org.apache.iceberg.io;

import java.io.Serializable;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.StructLike;

/**
 * 文件级说明：数据文件路径定位接口，为写入任务提供数据文件的目标位置。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>根据文件名生成全限定数据文件路径（非分区场景）。
 *   <li>根据分区规格与分区值生成全限定数据文件路径（分区场景）。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>实现必须可序列化（{@link Serializable}）：实例会被序列化后分发到写入任务中执行。
 *   <li>把路径生成策略与表写逻辑解耦：不同表可自定义路径布局（如按哈希分桶、按时间分层等）， 只需提供不同的 LocationProvider 实现。
 * </ul>
 *
 * <p>上下游关系：由表在创建时根据 catalog 属性构造，被写入任务（DataWriter 等）调用以确定 数据文件落盘位置。
 */
public interface LocationProvider extends Serializable {
  /**
   * 根据文件名返回全限定数据文件路径。
   *
   * @param filename 文件名
   * @return 数据文件的全限定路径 URI
   */
  String newDataLocation(String filename);

  /**
   * 根据分区规格和分区值返回全限定数据文件路径。
   *
   * @param spec 分区规格
   * @param partitionData 与该文件数据匹配 {@code spec} 的分区值元组
   * @param filename 文件名
   * @return 数据文件的全限定路径 URI
   */
  String newDataLocation(PartitionSpec spec, StructLike partitionData, String filename);
}
