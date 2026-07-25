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

import java.time.ZonedDateTime;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestDateTimeUtil 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestDateTimeUtil 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestDateTimeUtil {

  /**
   * 测试场景：format Timestamp Millis。
   *
   * <p>验证该方法在 format Timestamp Millis 条件下的行为是否符合预期。
   */
  @Test
  public void formatTimestampMillis() {
    String timestamp = "1970-01-01T00:00:00.001+00:00";
    Assertions.assertThat(DateTimeUtil.formatTimestampMillis(1L)).isEqualTo(timestamp);
    Assertions.assertThat(ZonedDateTime.parse(timestamp).toInstant().toEpochMilli()).isEqualTo(1L);

    timestamp = "1970-01-01T00:16:40+00:00";
    Assertions.assertThat(DateTimeUtil.formatTimestampMillis(1000000L)).isEqualTo(timestamp);
    Assertions.assertThat(ZonedDateTime.parse(timestamp).toInstant().toEpochMilli())
        .isEqualTo(1000000L);
  }
}
