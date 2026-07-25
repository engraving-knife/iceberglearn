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
 * 可序列化的比较器接口，用于 split 排序。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：扩展 Comparator 与 Serializable，支持 checkpoint。
 *
 * <p>设计意图：标记接口；被 SplitComparators 使用。
 */
public interface SerializableComparator<T> extends Comparator<T>, Serializable {}
