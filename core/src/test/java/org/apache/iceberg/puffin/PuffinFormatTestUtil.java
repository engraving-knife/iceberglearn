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
package org.apache.iceberg.puffin;

import org.apache.iceberg.relocated.com.google.common.io.Resources;

/**
 * 测试类：PuffinFormatTestUtil，用于验证 Puffin Format Test Util 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Puffin Format Test Util 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public final class PuffinFormatTestUtil {
  /** 辅助方法：puffin format test util。 */
  private PuffinFormatTestUtil() {}

  // footer size for v1/empty-puffin-uncompressed.bin
  public static final long EMPTY_PUFFIN_UNCOMPRESSED_FOOTER_SIZE = 28;

  // footer size for v1/sample-metric-data-compressed-zstd.bin
  public static final long SAMPLE_METRIC_DATA_COMPRESSED_ZSTD_FOOTER_SIZE = 314;

  static byte[] readTestResource(String resourceName) throws Exception {
    return Resources.toByteArray(Resources.getResource(PuffinFormatTestUtil.class, resourceName));
  }
}
