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

import java.util.Collections;
import java.util.List;
import org.apache.iceberg.DataFile;

/**
 * 文件级说明：数据文件写入结果。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：封装一次数据写入操作产生的 {@link DataFile} 列表，作为 {@link FileWriter} 的返回值。
 *
 * <p>设计意图：此类本身不可序列化。Task/Delta 写入器会将本结果包装进可序列化的 {@link WriteResult} 再回传给查询引擎，从而将"文件级结果"与"可序列化结果"解耦。
 *
 * <p>上下游关系：由 {@link DataWriter}、{@link RollingDataWriter}、{@link FanoutDataWriter} 等产生；由 {@link
 * ClusteredDataWriter}、{@link BasePositionDeltaWriter} 聚合。
 */
public class DataWriteResult {
  private final List<DataFile> dataFiles;

  /** 单文件构造器。 */
  public DataWriteResult(DataFile dataFile) {
    this.dataFiles = Collections.singletonList(dataFile);
  }

  /** 多文件构造器。 */
  public DataWriteResult(List<DataFile> dataFiles) {
    this.dataFiles = dataFiles;
  }

  /** 返回本次写入产生的数据文件列表。 */
  public List<DataFile> dataFiles() {
    return dataFiles;
  }
}
