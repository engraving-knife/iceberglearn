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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Function;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.hadoop.HadoopConfigurable;
import org.apache.iceberg.hadoop.SerializableConfiguration;

/**
 * 序列化工具类，提供 Java 对象与字节数组/Base64 字符串之间的互转，支持 Hadoop Configuration 的 可序列化处理。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：把对象序列化为 byte[] 或 Base64 字符串，以及反向反序列化；对实现了 {@link HadoopConfigurable} 的对象，在序列化时把 Hadoop
 * Configuration 转为 {@link SerializableConfiguration} 以保证可序列化。
 *
 * <p>设计意图：Iceberg 的任务对象常携带 Hadoop Configuration，而 Configuration 本身不可直接 Java 序列化。 通过
 * HadoopConfigurable 接口 + confSerializer 回调，在序列化前注入可序列化的 Configuration 代理。
 *
 * <p>上下游关系：被 core 的任务序列化、引擎集成层使用；依赖 Hadoop 与 {@link SerializableConfiguration}。
 */
public class SerializationUtil {

  private SerializationUtil() {}

  /**
   * 把对象序列化为字节数组。若对象实现了 {@link HadoopConfigurable}，其 Hadoop Configuration 会被序列化为 {@link
   * SerializableConfiguration}。
   *
   * @param obj 待序列化对象
   * @return 序列化后的字节数组
   */
  public static byte[] serializeToBytes(Object obj) {
    return serializeToBytes(obj, conf -> new SerializableConfiguration(conf)::get);
  }

  /**
   * 把对象序列化为字节数组，使用指定的 confSerializer 序列化 Hadoop Configuration。
   *
   * @param obj 待序列化对象
   * @param confSerializer Hadoop Configuration 的序列化器
   * @return 序列化后的字节数组
   */
  public static byte[] serializeToBytes(
      Object obj, Function<Configuration, SerializableSupplier<Configuration>> confSerializer) {
    if (obj instanceof HadoopConfigurable) {
      ((HadoopConfigurable) obj).serializeConfWith(confSerializer);
    }

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeObject(obj);
      return baos.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to serialize object", e);
    }
  }

  /**
   * 从字节数组反序列化对象。
   *
   * @param bytes 字节数组，为 null 时返回 null
   * @param <T> 目标类型
   * @return 反序列化得到的对象
   * @throws UncheckedIOException 反序列化 IO 异常
   * @throws RuntimeException 类找不到时
   */
  @SuppressWarnings("unchecked")
  public static <T> T deserializeFromBytes(byte[] bytes) {
    if (bytes == null) {
      return null;
    }

    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bais)) {
      return (T) ois.readObject();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to deserialize object", e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("Could not read object ", e);
    }
  }

  /**
   * 把对象序列化为 Base64 字符串（MIME 编码）。
   *
   * @param obj 待序列化对象
   * @return Base64 编码字符串
   */
  public static String serializeToBase64(Object obj) {
    byte[] bytes = serializeToBytes(obj);
    return new String(Base64.getMimeEncoder().encode(bytes), StandardCharsets.UTF_8);
  }

  /**
   * 从 Base64 字符串反序列化对象。
   *
   * @param base64 Base64 编码字符串，为 null 时返回 null
   * @param <T> 目标类型
   * @return 反序列化得到的对象
   */
  public static <T> T deserializeFromBase64(String base64) {
    if (base64 == null) {
      return null;
    }
    byte[] bytes = Base64.getMimeDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
    return deserializeFromBytes(bytes);
  }
}
