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

import java.io.IOException;

/**
 * 文件级说明：测试 TestableCloseableIterable 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestableCloseableIterable 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestableCloseableIterable implements CloseableIterable<Integer> {
  private Boolean closed = false;
  private TestableCloseableIterator iterator = new TestableCloseableIterator();

  /** 辅助方法：iterator。 */
  @Override
  public CloseableIterator<Integer> iterator() {
    return iterator;
  }

  /** 辅助方法：close。 */
  @Override
  public void close() throws IOException {
    closed = true;
  }

  /** 辅助方法：closed。 */
  public Boolean closed() {
    return closed;
  }

  class TestableCloseableIterator implements CloseableIterator<Integer> {
    private Boolean closed = false;

    /** 辅助方法：close。 */
    @Override
    public void close() throws IOException {
      closed = true;
    }

    /** 辅助方法：hasNext。 */
    @Override
    public boolean hasNext() {
      return false;
    }

    /** 辅助方法：next。 */
    @Override
    public Integer next() {
      return null;
    }

    /** 辅助方法：closed。 */
    public Boolean closed() {
      return closed;
    }
  }
}
