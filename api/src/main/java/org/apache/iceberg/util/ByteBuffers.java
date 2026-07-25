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

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * {@link ByteBuffer} 工具类：提供字节缓冲与字节数组之间的转换、buffer 复用与拷贝能力。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>将 ByteBuffer 转换为 byte[]（尽量零拷贝复用底层数组）。
 *   <li>复用已有堆 buffer 作为定长容器，避免反复分配。
 *   <li>拷贝 buffer 内容为独立副本。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>{@link #toByteArray} 区分堆 buffer 与直接 buffer：堆 buffer 在条件满足时直接返回底层数组 引用（零拷贝），否则按区间拷贝；直接
 *       buffer 则走只读视图拷贝。
 *   <li>{@link #reuse} 通过校验容量与数组偏移，确保复用安全，重置 position/limit 即可重新填充， 减少高频率场景下的 GC 压力。
 * </ul>
 *
 * <p>上下游关系：被 api/core 模块及引擎集成模块广泛用于字节缓冲的通用操作。
 */
public class ByteBuffers {

  /**
   * 将 {@link ByteBuffer} 转换为 byte[]。
   *
   * <p>逻辑：null 返回 null；若 buffer 有底层数组且整个数组正好是 buffer 内容则直接返回该数组 （零拷贝）；否则按 arrayOffset+position
   * 起始区间拷贝；直接 buffer 则用只读视图读取到新数组。
   *
   * @param buffer 待转换的字节缓冲，可为 null
   * @return 对应的字节数组，或 null
   */
  public static byte[] toByteArray(ByteBuffer buffer) {
    if (buffer == null) {
      return null;
    }

    if (buffer.hasArray()) {
      byte[] array = buffer.array();
      if (buffer.arrayOffset() == 0
          && buffer.position() == 0
          && array.length == buffer.remaining()) {
        return array;
      } else {
        int start = buffer.arrayOffset() + buffer.position();
        int end = start + buffer.remaining();
        return Arrays.copyOfRange(array, start, end);
      }
    } else {
      byte[] bytes = new byte[buffer.remaining()];
      buffer.asReadOnlyBuffer().get(bytes);
      return bytes;
    }
  }

  /**
   * 复用一个已有的堆 {@link ByteBuffer} 作为定长容器。
   *
   * <p>逻辑：校验 buffer 有底层数组、数组偏移为 0、容量等于指定 length；满足后重置 position 为 0、 limit 为 length 并返回，使其可被重新填充。
   *
   * @param reuse 待复用的字节缓冲
   * @param length 期望的可用长度
   * @return 重置后的同一 buffer
   * @throws IllegalArgumentException 若 buffer 非堆 buffer、数组偏移非 0 或容量不等于 length
   */
  public static ByteBuffer reuse(ByteBuffer reuse, int length) {
    Preconditions.checkArgument(reuse.hasArray(), "Cannot reuse a buffer not backed by an array");
    Preconditions.checkArgument(
        reuse.arrayOffset() == 0, "Cannot reuse a buffer whose array offset is not 0");
    Preconditions.checkArgument(
        reuse.capacity() == length,
        "Cannot use a buffer whose capacity (%s) is not equal to the requested length (%s)",
        length,
        reuse.capacity());
    reuse.position(0);
    reuse.limit(length);
    return reuse;
  }

  /**
   * 拷贝 {@link ByteBuffer} 的剩余内容为独立副本。
   *
   * <p>逻辑：null 返回 null；否则创建新字节数组，通过只读视图读取原 buffer 内容，包装为新 ByteBuffer 返回。原 buffer 的 position 不变。
   *
   * @param buffer 待拷贝的字节缓冲，可为 null
   * @return 内容相同的独立 ByteBuffer，或 null
   */
  public static ByteBuffer copy(ByteBuffer buffer) {
    if (buffer == null) {
      return null;
    }

    byte[] copyArray = new byte[buffer.remaining()];
    ByteBuffer readerBuffer = buffer.asReadOnlyBuffer();
    readerBuffer.get(copyArray);

    return ByteBuffer.wrap(copyArray);
  }

  private ByteBuffers() {}
}
