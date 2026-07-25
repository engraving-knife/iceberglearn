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

import org.mockito.Mockito;

/**
 * 测试类：MockFileScanTask，用于验证 Mock File Scan Task 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Mock File Scan Task 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class MockFileScanTask extends BaseFileScanTask {

  private final long length;

  /** 辅助方法：mock file scan task。 */
  public MockFileScanTask(long length) {
    super(null, null, null, null, null);
    this.length = length;
  }

  /** 辅助方法：mock file scan task。 */
  public MockFileScanTask(DataFile file) {
    super(file, null, null, null, null);
    this.length = file.fileSizeInBytes();
  }

  /** 辅助方法：mock file scan task。 */
  public MockFileScanTask(DataFile file, DeleteFile[] deleteFiles) {
    super(file, deleteFiles, null, null, null);
    this.length = file.fileSizeInBytes();
  }

  /** 辅助方法：mock file scan task。 */
  public MockFileScanTask(DataFile file, String schemaString, String specString) {
    super(file, null, schemaString, specString, null);
    this.length = file.fileSizeInBytes();
  }

  /** 辅助方法：mock task。 */
  public static MockFileScanTask mockTask(long length, int sortOrderId) {
    DataFile mockFile = Mockito.mock(DataFile.class);
    Mockito.when(mockFile.fileSizeInBytes()).thenReturn(length);
    Mockito.when(mockFile.sortOrderId()).thenReturn(sortOrderId);
    return new MockFileScanTask(mockFile);
  }

  /** 辅助方法：mock task with deletes。 */
  public static MockFileScanTask mockTaskWithDeletes(long length, int nDeletes) {
    DeleteFile[] mockDeletes = new DeleteFile[nDeletes];
    for (int i = 0; i < nDeletes; i++) {
      mockDeletes[i] = Mockito.mock(DeleteFile.class);
    }

    DataFile mockFile = Mockito.mock(DataFile.class);
    Mockito.when(mockFile.fileSizeInBytes()).thenReturn(length);
    return new MockFileScanTask(mockFile, mockDeletes);
  }

  /** 辅助方法：length。 */
  @Override
  public long length() {
    return length;
  }

  /** 辅助方法：to string。 */
  @Override
  public String toString() {
    return "Mock Scan Task Size: " + length;
  }

  /** 辅助方法：equals。 */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    MockFileScanTask that = (MockFileScanTask) o;
    return length == that.length;
  }

  /** 辅助方法：hash code。 */
  @Override
  public int hashCode() {
    return (int) (length ^ (length >>> 32));
  }
}
