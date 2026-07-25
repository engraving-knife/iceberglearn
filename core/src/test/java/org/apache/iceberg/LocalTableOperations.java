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

import java.io.IOException;
import java.util.Map;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.LocationProvider;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.junit.rules.TemporaryFolder;

/**
 * 测试类：LocalTableOperations，用于验证 Local Table Operations 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Local Table Operations 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
class LocalTableOperations implements TableOperations {
  private final TemporaryFolder temp;
  private final FileIO io;

  private final Map<String, String> createdMetadataFilePaths = Maps.newHashMap();

  LocalTableOperations(TemporaryFolder temp) {
    this.temp = temp;
    this.io = new TestTables.LocalFileIO();
  }

  /** 辅助方法：current。 */
  @Override
  public TableMetadata current() {
    throw new UnsupportedOperationException("Not implemented for tests");
  }

  /** 辅助方法：refresh。 */
  @Override
  public TableMetadata refresh() {
    throw new UnsupportedOperationException("Not implemented for tests");
  }

  /** 辅助方法：commit。 */
  @Override
  public void commit(TableMetadata base, TableMetadata metadata) {
    throw new UnsupportedOperationException("Not implemented for tests");
  }

  /** 辅助方法：io。 */
  @Override
  public FileIO io() {
    return io;
  }

  /** 辅助方法：metadata file location。 */
  @Override
  public String metadataFileLocation(String fileName) {
    return createdMetadataFilePaths.computeIfAbsent(
        fileName,
        name -> {
          try {
            return temp.newFile(name).getAbsolutePath();
          } catch (IOException e) {
            throw new RuntimeIOException(e);
          }
        });
  }

  /** 辅助方法：location provider。 */
  @Override
  public LocationProvider locationProvider() {
    throw new UnsupportedOperationException("Not implemented for tests");
  }

  /** 辅助方法：new snapshot id。 */
  @Override
  public long newSnapshotId() {
    throw new UnsupportedOperationException("Not implemented for tests");
  }
}
