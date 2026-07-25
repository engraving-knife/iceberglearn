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
package org.apache.iceberg.avro;

import java.io.IOException;
import org.apache.avro.io.Decoder;

/**
 * 文件级说明：Avro 值读取器接口，定义从 Avro {@link org.apache.avro.io.Decoder} 读取单个值的基本契约。
 *
 * <p>所属模块：iceberg-core（avro 子包）。职责：声明 {@link #read(Decoder, Object)} 方法， 由各类型的具体实现（见 {@link
 * ValueReaders}）完成解码。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>面向接口编程：读写栈只依赖此接口，具体实现可替换（反射读、Generic 读等）。
 *   <li>reuse 参数支持对象复用，减少 GC 压力。
 * </ul>
 *
 * <p>上下游关系：由 {@link GenericAvroReader} 等组合使用；具体实现在 {@link ValueReaders} 中。
 *
 * @param <T> 读取出的 Java 类型
 */
public interface ValueReader<T> {
  /**
   * 从解码器读取一个值。
   *
   * @param decoder Avro 解码器
   * @param reuse 可复用对象（可为 null，由实现决定是否使用）
   * @return 读取到的值
   * @throws IOException 读取失败时抛出
   */
  T read(Decoder decoder, Object reuse) throws IOException;
}
