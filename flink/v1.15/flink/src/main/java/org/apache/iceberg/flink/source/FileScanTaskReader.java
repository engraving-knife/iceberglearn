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
package org.apache.iceberg.flink.source;

import java.io.Serializable;
import org.apache.flink.annotation.Internal;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.encryption.InputFilesDecryptor;
import org.apache.iceberg.io.CloseableIterator;

/**
 * Read a {@link FileScanTask} into a {@link CloseableIterator}
 *
 * @param <T> is the output data type returned by this iterator.
 */
@Internal
/**
 * 文件扫描任务读取器接口，定义按 FileScanTask 读取记录的契约。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：抽象读取逻辑，支持不同输出类型（RowData/Avro 等）。
 *
 * <p>设计意图：策略接口；被读取算子调用。
 */
public interface FileScanTaskReader<T> extends Serializable {
  CloseableIterator<T> open(FileScanTask fileScanTask, InputFilesDecryptor inputFilesDecryptor);
}
