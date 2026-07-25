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
package org.apache.iceberg.parquet;

/**
 * 文件级说明：Avro Record 的 Parquet 读取器占位类（当前为空）。
 *
 * <p>所属模块：iceberg-parquet（与 Avro 集成的读取侧入口，预留扩展点）。
 *
 * <p>职责：当前未实现具体读取逻辑，作为后续 Avro IndexedRecord 读取扩展的占位类。
 *
 * <p>设计意图：保持 API 对称性，与 {@link ParquetAvroWriter} 配套；实际 Avro 读取 可通过 data 模块的 GenericParquetReader
 * 或引擎层 Reader 实现。
 *
 * <p>上下游关系：未来可被需要直接读取 Avro Record 的上层调用。
 */
public class ParquetAvroReader {}
