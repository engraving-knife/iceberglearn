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
package org.apache.iceberg.io;

import java.io.ByteArrayInputStream;

/**
 * 测试类：MockInputStream，用于验证 Mock Input Stream 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Mock Input Stream 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
class MockInputStream extends ByteArrayInputStream {

  static final byte[] TEST_ARRAY = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

  private int[] lengths;
  private int current = 0;

  MockInputStream(int... actualReadLengths) {
    super(TEST_ARRAY);
    this.lengths = actualReadLengths;
  }

  /** 辅助方法：read。 */
  @Override
  public synchronized int read(byte[] b, int off, int len) {
    if (current < lengths.length) {
      if (len <= lengths[current]) {
        // when len == lengths[current], the next read will by 0 bytes
        int bytesRead = super.read(b, off, len);
        lengths[current] -= bytesRead;
        return bytesRead;
      } else {
        int bytesRead = super.read(b, off, lengths[current]);
        current += 1;
        return bytesRead;
      }
    } else {
      return super.read(b, off, len);
    }
  }

  /** 辅助方法：get pos。 */
  public long getPos() {
    return this.pos;
  }
}
