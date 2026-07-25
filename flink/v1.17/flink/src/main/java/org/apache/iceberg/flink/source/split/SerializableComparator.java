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

import java.io.Serializable;
import java.util.Comparator;

/**
 * 文件级说明：可序列化的比较器接口。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source split 子包）。
 *
 * <p>职责：组合 {@link Comparator} 与 {@link Serializable}， 使比较器可在 Flink 各进程间序列化传输。
 *
 * <p>设计意图：分片排序比较器需要在 JobManager 与 TaskManager 之间传递， 因此必须同时具备比较能力与序列化能力。
 *
 * <p>上下游关系：被 {@link SplitComparators} 用于构造各种分片排序策略。
 */
public interface SerializableComparator<T> extends Comparator<T>, Serializable {}
