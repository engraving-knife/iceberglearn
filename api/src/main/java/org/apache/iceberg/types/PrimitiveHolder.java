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

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * 原始类型序列化占位符：在 Java 序列化时替代原始类型实例，反序列化时还原为单例。
 *
 * <p>所属模块：iceberg-api（被 {@link Type.PrimitiveType#writeReplace} 使用）。
 *
 * <p>职责：序列化时保存原始类型的字符串表示，反序列化时通过 {@link Types#fromPrimitiveString} 还原为对应的单例实例。
 *
 * <p>设计意图：保证原始类型在序列化/反序列化后仍指向同一单例，避免创建多余实例。
 */
class PrimitiveHolder implements Serializable {
  private String typeAsString = null;

  /** Java 序列化用的无参构造器。 */
  PrimitiveHolder() {}

  PrimitiveHolder(String typeAsString) {
    this.typeAsString = typeAsString;
  }

  Object readResolve() throws ObjectStreamException {
    return Types.fromPrimitiveString(typeAsString);
  }
}
