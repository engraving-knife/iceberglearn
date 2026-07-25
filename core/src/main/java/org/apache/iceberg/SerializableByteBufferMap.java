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
package org.apache.iceberg;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.util.ByteBuffers;

/**
 * 可被 Java 序列化的 {@code Map<Integer,ByteBuffer>} 包装类。
 *
 * <p>所属模块：iceberg-core。职责：把一个普通 {@link Map}（值含 {@link ByteBuffer}）包装为可序列化形式， 解决 ByteBuffer
 * 直接序列化不可靠的问题。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>序列化代理：通过 {@link MapSerializationProxy} 在写入时把 ByteBuffer 转为字节数组， 读回时再还原，规避 ByteBuffer 的
 *       position/limit 状态问题。
 *   <li>懒初始化不可变视图：{@code immutableMap} 用 transient volatile 缓存，避免重复拷贝。
 *   <li>静态 wrap 工厂：避免重复包装已包装过的 map。
 * </ul>
 *
 * <p>上下游关系：被 {@link SerializableMap} 等可序列化容器用于持有二进制指标数据。
 */
class SerializableByteBufferMap implements Map<Integer, ByteBuffer>, Serializable {
  private final Map<Integer, ByteBuffer> wrapped;
  private transient volatile Map<Integer, ByteBuffer> immutableMap;

  /**
   * 包裹一个普通 Map；若已经是 SerializableByteBufferMap 则原样返回，避免重复包装。
   *
   * @param map 待包装的 map，可为 null
   * @return 可序列化包装后的 map，map 为 null 时返回 null
   */
  static Map<Integer, ByteBuffer> wrap(Map<Integer, ByteBuffer> map) {
    if (map == null) {
      return null;
    }

    if (map instanceof SerializableByteBufferMap) {
      return map;
    }

    return new SerializableByteBufferMap(map);
  }

  SerializableByteBufferMap() {
    this.wrapped = Maps.newLinkedHashMap();
  }

  /**
   * 私有构造：直接持有传入的 wrapped map（不拷贝）。
   *
   * @param wrapped 被包装的 map
   */
  private SerializableByteBufferMap(Map<Integer, ByteBuffer> wrapped) {
    this.wrapped = wrapped;
  }

  private static class MapSerializationProxy implements Serializable {
    private int[] keys = null;
    private byte[][] values = null;

    /** Constructor for Java serialization. */
    MapSerializationProxy() {}

    MapSerializationProxy(int[] keys, byte[][] values) {
      this.keys = keys;
      this.values = values;
    }

    Object readResolve() throws ObjectStreamException {
      Map<Integer, ByteBuffer> map = Maps.newLinkedHashMap();

      for (int i = 0; i < keys.length; i += 1) {
        map.put(keys[i], ByteBuffer.wrap(values[i]));
      }

      return SerializableByteBufferMap.wrap(map);
    }
  }

  /**
   * 序列化时被 Java 调用：把 wrapped 中的 ByteBuffer 全部转为字节数组， 通过 {@link MapSerializationProxy} 写入流，规避
   * ByteBuffer 序列化问题。
   *
   * @return 序列化代理对象
   * @throws ObjectStreamException 不会抛出
   */
  Object writeReplace() throws ObjectStreamException {
    Collection<Map.Entry<Integer, ByteBuffer>> entries = wrapped.entrySet();
    int[] keys = new int[entries.size()];
    byte[][] values = new byte[keys.length][];

    int keyIndex = 0;
    for (Map.Entry<Integer, ByteBuffer> entry : entries) {
      keys[keyIndex] = entry.getKey();
      values[keyIndex] = ByteBuffers.toByteArray(entry.getValue());
      keyIndex += 1;
    }

    return new MapSerializationProxy(keys, values);
  }

  /**
   * 返回 wrapped 的不可变视图，使用双重检查锁定懒初始化。
   *
   * @return 不可变 map 视图
   */
  public Map<Integer, ByteBuffer> immutableMap() {
    if (immutableMap == null) {
      synchronized (this) {
        if (immutableMap == null) {
          immutableMap = Collections.unmodifiableMap(wrapped);
        }
      }
    }

    return immutableMap;
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public int size() {
    return wrapped.size();
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public boolean isEmpty() {
    return wrapped.isEmpty();
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public boolean containsKey(Object key) {
    return wrapped.containsKey(key);
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public boolean containsValue(Object value) {
    return wrapped.containsValue(value);
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public ByteBuffer get(Object key) {
    return wrapped.get(key);
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public ByteBuffer put(Integer key, ByteBuffer value) {
    return wrapped.put(key, value);
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public ByteBuffer remove(Object key) {
    return wrapped.remove(key);
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public void putAll(Map<? extends Integer, ? extends ByteBuffer> m) {
    wrapped.putAll(m);
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public void clear() {
    wrapped.clear();
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public Set<Integer> keySet() {
    return wrapped.keySet();
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public Collection<ByteBuffer> values() {
    return wrapped.values();
  }

  @Override
  public Set<Entry<Integer, ByteBuffer>> entrySet() {
    return wrapped.entrySet();
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    return wrapped.equals(o);
  }

  /** 委托给 wrapped。{@inheritDoc} */
  @Override
  public int hashCode() {
    return wrapped.hashCode();
  }
}
