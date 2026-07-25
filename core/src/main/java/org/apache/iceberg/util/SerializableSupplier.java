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
import java.util.function.Supplier;

/**
 * 可序列化的 {@link Supplier}：同时实现 {@link Supplier} 与 {@link Serializable}， 使 Supplier 可以在分布式引擎中序列化传输。
 *
 * <p>所属模块：iceberg-core。常用于 lambda 捕获 Hadoop Configuration 等场景。
 */
public interface SerializableSupplier<T> extends Supplier<T>, Serializable {}
