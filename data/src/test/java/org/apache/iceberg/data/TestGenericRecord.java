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
package org.apache.iceberg.data;

import static org.apache.iceberg.types.Types.NestedField.optional;

import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestGenericRecord 的功能。
 *
 * <p>所属模块：iceberg-data。职责：验证 TestGenericRecord 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestGenericRecord {

  /**
   * 测试场景：Get Null Value。
   *
   * <p>验证该方法在 Get Null Value 条件下的行为是否符合预期。
   */
  @Test
  public void testGetNullValue() {
    Types.LongType type = Types.LongType.get();
    Schema schema = new Schema(optional(1, "id", type));
    GenericRecord record = GenericRecord.create(schema);
    record.set(0, null);

    Assert.assertNull(record.get(0, type.typeId().javaClass()));
  }

  /**
   * 测试场景：Get Not Null Value。
   *
   * <p>验证该方法在 Get Not Null Value 条件下的行为是否符合预期。
   */
  @Test
  public void testGetNotNullValue() {
    Types.LongType type = Types.LongType.get();
    Schema schema = new Schema(optional(1, "id", type));
    GenericRecord record = GenericRecord.create(schema);
    record.set(0, 10L);

    Assert.assertEquals(10L, record.get(0, type.typeId().javaClass()));
  }

  /**
   * 测试场景：Get Incorrect Class Instance。
   *
   * <p>验证该方法在 Get Incorrect Class Instance 条件下的行为是否符合预期。
   */
  @Test
  public void testGetIncorrectClassInstance() {
    Schema schema = new Schema(optional(1, "id", Types.LongType.get()));
    GenericRecord record = GenericRecord.create(schema);
    record.set(0, 10L);

    Assertions.assertThatThrownBy(() -> record.get(0, CharSequence.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Not an instance of java.lang.CharSequence: 10");
  }
}
