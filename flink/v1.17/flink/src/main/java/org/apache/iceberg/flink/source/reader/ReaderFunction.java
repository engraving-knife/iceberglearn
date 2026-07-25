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
package org.apache.iceberg.flink.source.reader;

import java.io.Serializable;
import java.util.function.Function;
import org.apache.flink.connector.base.source.reader.RecordsWithSplitIds;
import org.apache.iceberg.flink.source.split.IcebergSourceSplit;
import org.apache.iceberg.io.CloseableIterator;

/**
 * 文件级说明：从 IcebergSourceSplit 创建批量记录迭代器的函数式接口。
 *
 * <p>所属模块：iceberg-flink（source/reader 子包），是 FLIP-27 Source SplitReader 的核心抽象。
 *
 * <p>职责：接收一个 {@link IcebergSourceSplit}，返回该 split 对应的批量记录迭代器。
 *
 * <p>设计意图：函数式接口，可序列化。将 split 到记录的读取逻辑抽象为接口， 支持不同数据类型（RowData、GenericRecord 等）的读取实现。
 *
 * <p>上下游关系：被 {@link IcebergSourceSplitReader} 调用； 实现类如 {@link RowDataReaderFunction}。
 *
 * @param <T> 记录类型
 */
@FunctionalInterface
public interface ReaderFunction<T>
    extends Serializable,
        Function<
            IcebergSourceSplit, CloseableIterator<RecordsWithSplitIds<RecordAndPosition<T>>>> {}
