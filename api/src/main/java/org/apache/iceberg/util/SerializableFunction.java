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
package org.apache.iceberg.util;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的转换函数接口：对特定类型的值应用某种转换（transform）。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：在 JDK {@link java.util.function.Function} 的基础上增加 {@link Serializable}，
 * 使转换函数可随表对象/分区规范一起序列化与分布式传递。
 *
 * <p>设计意图：Iceberg 的分区转换（如 bucket、truncate、year 等）需要生成可序列化的函数实例， 以便在引擎执行端反序列化后对每行数据求值；直接用 {@link
 * Function} 无法保证可序列化， 故定义本接口显式约束。
 *
 * <p>上下游关系：被 core 模块的 transform 实现作为求值函数类型；由表达式与分区逻辑持有。
 *
 * @param <S> 源值的 Java 类型
 * @param <T> 转换后值的 Java 类型
 */
public interface SerializableFunction<S, T> extends Function<S, T>, Serializable {}
