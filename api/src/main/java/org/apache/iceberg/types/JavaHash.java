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
package org.apache.iceberg.types;

import java.util.Objects;

/**
 * Java 哈希函数接口：为 Iceberg 类型提供 Java 语义的哈希值计算。
 *
 * <p>所属模块：iceberg-api（被 core 的数据哈希、去重等场景使用）。
 *
 * <p>职责：定义统一的 hash(T value) 方法；提供 {@link #forType(Type)} 工厂按类型返回 合适的哈希实现。
 *
 * <p>设计意图：字符串、struct、list 有专用哈希实现（见 {@link JavaHashes}）， 其余类型用 {@link
 * Objects#hashCode}。函数式接口便于用方法引用实现。
 *
 * @param <T> 待哈希值的类型
 */
@FunctionalInterface
public interface JavaHash<T> {
  int hash(T value);

  @SuppressWarnings("unchecked")
  static <T> JavaHash<T> forType(Type type) {
    switch (type.typeId()) {
      case STRING:
        return (JavaHash<T>) JavaHashes.strings();
      case STRUCT:
        return (JavaHash<T>) JavaHashes.struct(type.asStructType());
      case LIST:
        return (JavaHash<T>) JavaHashes.list(type.asListType());
      default:
        return Objects::hashCode;
    }
  }
}
