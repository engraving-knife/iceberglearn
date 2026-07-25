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
package org.apache.iceberg.transforms;

import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * 模块：api，transforms（分区变换）层序列化辅助。
 *
 * <p>职责：为各类 Transform 单例提供 Java 序列化代理。
 *
 * <p>设计意图：Transform 类设计为单例以保证相等性（identical equality），无法直接被 Java 序列化， 因此通过代理类承担序列化，并在 {@code
 * readResolve} 中还原为对应单例。
 *
 * <p>上下游关系：被 {@link VoidTransform}、{@link Identity}、{@link Years}、{@link Months}、 {@link
 * Days}、{@link Hours} 等单例 Transform 在 {@code writeReplace} 中使用。
 */
class SerializationProxies {
  private SerializationProxies() {}

  /** {@link VoidTransform} 的序列化代理，反序列化时还原为单例。 */
  static class VoidTransformProxy implements Serializable {
    private static final VoidTransformProxy INSTANCE = new VoidTransformProxy();

    static VoidTransformProxy get() {
      return INSTANCE;
    }

    /** Java 序列化使用的无参构造。 */
    VoidTransformProxy() {}

    Object readResolve() throws ObjectStreamException {
      return VoidTransform.get();
    }
  }

  /** {@link Identity} 的序列化代理，反序列化时还原为单例。 */
  static class IdentityTransformProxy implements Serializable {
    private static final IdentityTransformProxy INSTANCE = new IdentityTransformProxy();

    static IdentityTransformProxy get() {
      return INSTANCE;
    }

    /** Java 序列化使用的无参构造。 */
    IdentityTransformProxy() {}

    Object readResolve() throws ObjectStreamException {
      return Identity.get();
    }
  }

  /** {@link Years} 的序列化代理，反序列化时还原为单例。 */
  static class YearsTransformProxy implements Serializable {
    private static final YearsTransformProxy INSTANCE = new YearsTransformProxy();

    static YearsTransformProxy get() {
      return INSTANCE;
    }

    /** Java 序列化使用的无参构造。 */
    YearsTransformProxy() {}

    Object readResolve() throws ObjectStreamException {
      return Years.get();
    }
  }

  /** {@link Months} 的序列化代理，反序列化时还原为单例。 */
  static class MonthsTransformProxy implements Serializable {
    private static final MonthsTransformProxy INSTANCE = new MonthsTransformProxy();

    static MonthsTransformProxy get() {
      return INSTANCE;
    }

    /** Java 序列化使用的无参构造。 */
    MonthsTransformProxy() {}

    Object readResolve() throws ObjectStreamException {
      return Months.get();
    }
  }

  /** {@link Days} 的序列化代理，反序列化时还原为单例。 */
  static class DaysTransformProxy implements Serializable {
    private static final DaysTransformProxy INSTANCE = new DaysTransformProxy();

    static DaysTransformProxy get() {
      return INSTANCE;
    }

    /** Java 序列化使用的无参构造。 */
    DaysTransformProxy() {}

    Object readResolve() throws ObjectStreamException {
      return Days.get();
    }
  }

  /** {@link Hours} 的序列化代理，反序列化时还原为单例。 */
  static class HoursTransformProxy implements Serializable {
    private static final HoursTransformProxy INSTANCE = new HoursTransformProxy();

    static HoursTransformProxy get() {
      return INSTANCE;
    }

    /** Java 序列化使用的无参构造。 */
    HoursTransformProxy() {}

    Object readResolve() throws ObjectStreamException {
      return Hours.get();
    }
  }
}
