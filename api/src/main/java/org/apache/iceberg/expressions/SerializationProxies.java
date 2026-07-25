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
package org.apache.iceberg.expressions;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.ByteBuffer;

/**
 * 模块：api，表达式层序列化辅助。
 *
 * <p>职责：为表达式相关类提供 Java 序列化的代理（stand-in）对象。
 *
 * <p>设计意图：表达式类本身是不可变且使用 final 字段的，无法提供无参构造供 Java 序列化使用， 因此借助代理类承担序列化/反序列化职责，再通过 {@code readResolve}
 * 还原为真正的不可变表达式实例。
 *
 * <p>上下游关系：被 {@link Literals}、{@link True}、{@link False} 等表达式类在 {@code writeReplace}/{@code
 * readResolve} 中使用。
 */
class SerializationProxies {
  /** 常量表达式（True/False）的序列化代理。 */
  static class ConstantExpressionProxy implements Serializable {
    private Boolean trueOrFalse = null;

    /** Java 序列化使用的无参构造。 */
    ConstantExpressionProxy() {}

    ConstantExpressionProxy(boolean trueOrFalse) {
      this.trueOrFalse = trueOrFalse;
    }

    /** 反序列化时还原为 {@link True} 或 {@link False} 单例。 */
    Object readResolve() throws ObjectStreamException {
      if (trueOrFalse) {
        return True.INSTANCE;
      } else {
        return False.INSTANCE;
      }
    }
  }

  /** 二进制字面量的序列化代理，继承自 {@link FixedLiteralProxy}。 */
  static class BinaryLiteralProxy extends FixedLiteralProxy {
    /** Java 序列化使用的无参构造。 */
    BinaryLiteralProxy() {}

    BinaryLiteralProxy(ByteBuffer buffer) {
      super(buffer);
    }

    /** 反序列化时还原为 {@link Literals.BinaryLiteral}。 */
    @Override
    Object readResolve() throws ObjectStreamException {
      return new Literals.BinaryLiteral(ByteBuffer.wrap(bytes()));
    }
  }

  /**
   * FixedLiteral 在 Java 序列化中的代理类。
   *
   * <p>将 {@link ByteBuffer} 内容拷贝为 byte 数组便于序列化传输。
   */
  static class FixedLiteralProxy implements Serializable {
    private byte[] bytes;

    /** Java 序列化使用的无参构造。 */
    FixedLiteralProxy() {}

    FixedLiteralProxy(ByteBuffer buffer) {
      this.bytes = new byte[buffer.remaining()];
      buffer.duplicate().get(bytes);
    }

    /** 反序列化时还原为 {@link Literals.FixedLiteral}。 */
    Object readResolve() throws ObjectStreamException {
      return new Literals.FixedLiteral(ByteBuffer.wrap(bytes));
    }

    /** 返回代理持有的字节数组，供子类复用。 */
    protected byte[] bytes() {
      return bytes;
    }
  }
}
