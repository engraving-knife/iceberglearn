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
package org.apache.iceberg.mr.mapred;

import java.io.DataInput;
import java.io.DataOutput;
import org.apache.hadoop.io.Writable;

/**
 * 文件级说明：保存单个对象的简单容器，作为 Iceberg Record 在 mapred 接口中的 Writable 包装。
 *
 * <p>所属模块：iceberg-mr（mapred 子包；为 MR v1 RecordReader 提供 Record 的 Writable 载体）。
 *
 * <p>职责：持有可 get/set 的对象引用，让 Iceberg Record 能通过 mapred RecordReader 的 value 参数传递。
 *
 * <p>设计意图：Iceberg Record 通常不可序列化为 Hadoop Writable，因此用一个容器包装其引用， 在 JVM 内传递；write/readFields 不支持（数据在
 * JVM 内通过引用传递，无需序列化）。
 *
 * <p>上下游关系：上游由 {@link MapredIcebergInputFormat} 创建并填充；被 Hive SerDe 等读取侧 通过 {@link #get()} 取出
 * Record。
 *
 * @param <T> 容器持有的对象类型
 */
public class Container<T> implements Writable {

  private T value;

  /** 返回当前持有的对象。 */
  public T get() {
    return value;
  }

  /** 设置持有的对象。 */
  public void set(T newValue) {
    this.value = newValue;
  }

  /** 不支持序列化写出，直接抛出异常。 */
  @Override
  public void write(DataOutput dataOutput) {
    throw new UnsupportedOperationException("write is not supported.");
  }

  /** 不支持反序列化读入，直接抛出异常。 */
  @Override
  public void readFields(DataInput dataInput) {
    throw new UnsupportedOperationException("readFields is not supported.");
  }
}
