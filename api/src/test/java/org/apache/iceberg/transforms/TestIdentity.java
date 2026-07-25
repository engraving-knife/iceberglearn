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
package org.apache.iceberg.transforms;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestIdentity 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestIdentity 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestIdentity {
  /**
   * 测试场景：Null Human String。
   *
   * <p>验证该方法在 Null Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testNullHumanString() {
    Types.LongType longType = Types.LongType.get();
    Transform<Long, Long> identity = Transforms.identity();

    assertThat(identity.toHumanString(longType, null))
        .as("Should produce \"null\" for null")
        .isEqualTo("null");
  }

  /**
   * 测试场景：Binary Human String。
   *
   * <p>验证该方法在 Binary Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testBinaryHumanString() {
    Types.BinaryType binary = Types.BinaryType.get();
    Transform<ByteBuffer, ByteBuffer> identity = Transforms.identity();

    assertThat(identity.toHumanString(binary, ByteBuffer.wrap(new byte[] {1, 2, 3})))
        .as("Should base64-encode binary")
        .isEqualTo("AQID");
  }

  /**
   * 测试场景：Fixed Human String。
   *
   * <p>验证该方法在 Fixed Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testFixedHumanString() {
    Types.FixedType fixed3 = Types.FixedType.ofLength(3);
    Transform<byte[], byte[]> identity = Transforms.identity();

    assertThat(identity.toHumanString(fixed3, new byte[] {1, 2, 3}))
        .as("Should base64-encode binary")
        .isEqualTo("AQID");
  }

  /**
   * 测试场景：Date Human String。
   *
   * <p>验证该方法在 Date Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testDateHumanString() {
    Types.DateType date = Types.DateType.get();
    Transform<Integer, Integer> identity = Transforms.identity();

    String dateString = "2017-12-01";
    Literal<Integer> dateLit = Literal.of(dateString).to(date);

    assertThat(identity.toHumanString(date, dateLit.value()))
        .as("Should produce identical date")
        .isEqualTo(dateString);
  }

  /**
   * 测试场景：Date Human String Deprecated。
   *
   * <p>验证该方法在 Date Human String Deprecated 条件下的行为是否符合预期。
   */
  @Test
  public void testDateHumanStringDeprecated() {
    Types.DateType date = Types.DateType.get();
    Transform<Integer, Integer> identity = Transforms.identity(date);

    String dateString = "2017-12-01";
    Literal<Integer> dateLit = Literal.of(dateString).to(date);

    assertThat(identity.toHumanString(dateLit.value()))
        .as("Should produce identical date")
        .isEqualTo(dateString);
  }

  /**
   * 测试场景：Time Human String。
   *
   * <p>验证该方法在 Time Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testTimeHumanString() {
    Types.TimeType time = Types.TimeType.get();
    Transform<Long, Long> identity = Transforms.identity();

    String timeString = "10:12:55.038194";
    Literal<Long> timeLit = Literal.of(timeString).to(time);

    assertThat(identity.toHumanString(time, timeLit.value()))
        .as("Should produce identical time")
        .isEqualTo(timeString);
  }

  /**
   * 测试场景：Timestamp With Zone Human String。
   *
   * <p>验证该方法在 Timestamp With Zone Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testTimestampWithZoneHumanString() {
    Types.TimestampType timestamptz = Types.TimestampType.withZone();
    Transform<Long, Long> identity = Transforms.identity();

    Literal<Long> ts = Literal.of("2017-12-01T10:12:55.038194-08:00").to(timestamptz);

    // value will always be in UTC
    assertThat(identity.toHumanString(timestamptz, ts.value()))
        .as("Should produce timestamp with time zone adjusted to UTC")
        .isEqualTo("2017-12-01T18:12:55.038194Z");
  }

  /**
   * 测试场景：Timestamp Without Zone Human String。
   *
   * <p>验证该方法在 Timestamp Without Zone Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testTimestampWithoutZoneHumanString() {
    Types.TimestampType timestamp = Types.TimestampType.withoutZone();
    Transform<Long, Long> identity = Transforms.identity();

    String tsString = "2017-12-01T10:12:55.038194";
    Literal<Long> ts = Literal.of(tsString).to(timestamp);

    // value is not changed
    assertThat(identity.toHumanString(timestamp, ts.value()))
        .as("Should produce identical timestamp without time zone")
        .isEqualTo(tsString);
  }

  /**
   * 测试场景：Long To Human String。
   *
   * <p>验证该方法在 Long To Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testLongToHumanString() {
    Types.LongType longType = Types.LongType.get();
    Transform<Long, Long> identity = Transforms.identity();

    assertThat(identity.toHumanString(longType, -1234567890000L))
        .as("Should use Long toString")
        .isEqualTo("-1234567890000");
  }

  /**
   * 测试场景：String To Human String。
   *
   * <p>验证该方法在 String To Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testStringToHumanString() {
    Types.StringType string = Types.StringType.get();
    Transform<String, String> identity = Transforms.identity();

    String withSlash = "a/b/c=d";
    assertThat(identity.toHumanString(string, withSlash))
        .as("Should not modify Strings")
        .isEqualTo(withSlash);
  }

  /**
   * 测试场景：Big Decimal To Human String。
   *
   * <p>验证该方法在 Big Decimal To Human String 条件下的行为是否符合预期。
   */
  @Test
  public void testBigDecimalToHumanString() {
    Types.DecimalType decimal = Types.DecimalType.of(9, 2);
    Transform<BigDecimal, BigDecimal> identity = Transforms.identity();

    String decimalString = "-1.50";
    BigDecimal bigDecimal = new BigDecimal(decimalString);
    assertThat(identity.toHumanString(decimal, bigDecimal))
        .as("Should not modify Strings")
        .isEqualTo(decimalString);
  }
}
