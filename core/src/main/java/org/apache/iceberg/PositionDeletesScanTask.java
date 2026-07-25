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
package org.apache.iceberg;

/**
 * 位置删除文件的扫描任务接口。
 *
 * <p>所属模块：iceberg-core（核心实现层），扩展 {@link ContentScanTask} 以专门描述位置删除文件扫描任务。
 *
 * <p>职责：在扫描场景下承载一个位置删除文件（position delete file）的读取信息， 供引擎执行 MVCC 删除合并。
 *
 * <p>设计意图：作为标记接口，区分位置删除与等值删除的扫描任务类型，便于引擎按类型分发处理。
 *
 * <p>上下游关系：被 {@link PositionDeletesScan} 等扫描规划器产出；被引擎删除合并逻辑消费。
 */
public interface PositionDeletesScanTask extends ContentScanTask<DeleteFile> {}
