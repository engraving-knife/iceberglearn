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
package org.apache.iceberg.inmemory;

import java.util.Map;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：内存版 FileIO 实现，用于测试和演示。
 *
 * <p>所属模块：iceberg-core（inmemory 子包）。职责：实现 {@link org.apache.iceberg.io.FileIO} 接口，在内存中存储文件内容，提供
 * newInputFile/newOutputFile/deleteFile 等方法。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不依赖外部存储（无 HDFS/S3），用于单元测试和快速原型验证。
 *   <li>线程安全：内部用 ConcurrentHashMap 存储文件。
 * </ul>
 *
 * <p>上下游关系：被测试代码和 InMemoryCatalog 使用；产出 {@link InMemoryInputFile} / {@link InMemoryOutputFile}。
 */
public class InMemoryFileIO implements FileIO {

  private final Map<String, byte[]> inMemoryFiles = Maps.newConcurrentMap();
  private boolean closed = false;

  public void addFile(String location, byte[] contents) {
    Preconditions.checkState(!closed, "Cannot call addFile after calling close()");
    inMemoryFiles.put(location, contents);
  }

  public boolean fileExists(String location) {
    return inMemoryFiles.containsKey(location);
  }

  @Override
  public InputFile newInputFile(String location) {
    Preconditions.checkState(!closed, "Cannot call newInputFile after calling close()");
    byte[] contents = inMemoryFiles.get(location);
    if (null == contents) {
      throw new NotFoundException("No in-memory file found for location: %s", location);
    }
    return new InMemoryInputFile(location, contents);
  }

  @Override
  public OutputFile newOutputFile(String location) {
    Preconditions.checkState(!closed, "Cannot call newOutputFile after calling close()");
    return new InMemoryOutputFile(location, this);
  }

  @Override
  /**
   * 从内存存储中删除指定路径的文件。
   *
   * @param location 文件路径标识
   */
  public void deleteFile(String location) {
    Preconditions.checkState(!closed, "Cannot call deleteFile after calling close()");
    if (null == inMemoryFiles.remove(location)) {
      throw new NotFoundException("No in-memory file found for location: %s", location);
    }
  }

  public boolean isClosed() {
    return closed;
  }

  @Override
  /** 关闭 FileIO，清空内存存储。 */
  public void close() {
    closed = true;
  }
}
