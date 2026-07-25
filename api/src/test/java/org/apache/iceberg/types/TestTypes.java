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
package org.apache.iceberg.types;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestTypes 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestTypes 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestTypes {

  /**
   * 测试场景：from Primitive String。
   *
   * <p>验证该方法在 from Primitive String 条件下的行为是否符合预期。
   */
  @Test
  public void fromPrimitiveString() {
    Assertions.assertThat(Types.fromPrimitiveString("boolean")).isSameAs(Types.BooleanType.get());
    Assertions.assertThat(Types.fromPrimitiveString("BooLean")).isSameAs(Types.BooleanType.get());

    Assertions.assertThat(Types.fromPrimitiveString("timestamp"))
        .isSameAs(Types.TimestampType.withoutZone());

    Assertions.assertThat(Types.fromPrimitiveString("Fixed[ 3 ]"))
        .isEqualTo(Types.FixedType.ofLength(3));

    Assertions.assertThat(Types.fromPrimitiveString("Decimal( 2 , 3 )"))
        .isEqualTo(Types.DecimalType.of(2, 3));

    Assertions.assertThat(Types.fromPrimitiveString("Decimal(2,3)"))
        .isEqualTo(Types.DecimalType.of(2, 3));

    Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> Types.fromPrimitiveString("Unknown"))
        .withMessageContaining("Unknown");
  }
}
