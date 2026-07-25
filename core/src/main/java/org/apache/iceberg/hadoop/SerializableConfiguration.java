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
package org.apache.iceberg.hadoop;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.apache.hadoop.conf.Configuration;

/**
 * 文件级说明：将 Hadoop {@link Configuration} 包装为可序列化对象的工具类。
 *
 * <p>所属模块：iceberg-core 的 hadoop 包。
 *
 * <p>职责：通过自定义 Java 序列化逻辑（{@link #writeObject} / {@link #readObject}）， 把不可直接序列化的 Hadoop {@link
 * Configuration} 序列化到对象流， 使其能随 Spark/Flink 等任务一起分发到各执行节点。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>原生 {@link Configuration} 虽实现 {@link org.apache.hadoop.io.Writable}， 但默认 Java 序列化并不支持它，因此用
 *       {@code transient} 持有引用， 并在序列化时调用 {@link Configuration#write(java.io.DataOutput)} 写出二进制内容，
 *       反序列化时 {@code new Configuration(false)} 后 {@link Configuration#readFields} 读回。
 *   <li>构造时 {@code false} 关闭默认资源加载，避免反序列化时再次加载 Hadoop 默认配置。
 * </ul>
 *
 * <p>上下游关系：被 {@link HadoopFileIO}、{@link HadoopCatalog} 等需要把 Hadoop 配置 随对象一起序列化的场景使用；常以方法引用 {@code
 * serializableConf::get} 形式作为 {@link org.apache.iceberg.util.SerializableSupplier}。
 */
public class SerializableConfiguration implements Serializable {

  private transient Configuration hadoopConf;

  /**
   * 构造一个可序列化的 Hadoop 配置包装器。
   *
   * @param hadoopConf 待包装的 Hadoop {@link Configuration}
   */
  public SerializableConfiguration(Configuration hadoopConf) {
    this.hadoopConf = hadoopConf;
  }

  /**
   * 自定义序列化写出逻辑。
   *
   * <p>逻辑：先执行默认字段写出，再调用 {@link Configuration#write(java.io.DataOutput)} 把 Hadoop 配置项以二进制形式写入对象流。
   *
   * @param out 对象输出流
   * @throws IOException 写出失败时抛出
   */
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    hadoopConf.write(out);
  }

  /**
   * 自定义反序列化读取逻辑。
   *
   * <p>逻辑：先执行默认字段读取，再创建一个不加载默认资源的 {@link Configuration}， 通过 {@link
   * Configuration#readFields(java.io.DataInput)} 从流中恢复配置项。
   *
   * @param in 对象输入流
   * @throws ClassNotFoundException 默认反序列化失败时抛出
   * @throws IOException 读取配置失败时抛出
   */
  private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
    in.defaultReadObject();
    hadoopConf = new Configuration(false);
    hadoopConf.readFields(in);
  }

  /**
   * 获取包装的 Hadoop {@link Configuration} 实例。
   *
   * @return 当前持有的 {@link Configuration}
   */
  public Configuration get() {
    return hadoopConf;
  }
}
