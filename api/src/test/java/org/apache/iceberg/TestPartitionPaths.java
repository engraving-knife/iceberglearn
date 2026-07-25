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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.iceberg.TestHelpers.Row;
import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.transforms.Transform;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestPartitionPaths 的功能。
 *
 * <p>所属模块：iceberg-api。职责：验证 TestPartitionPaths 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestPartitionPaths {
  private static final Schema SCHEMA =
      new Schema(
          Types.NestedField.required(1, "id", Types.IntegerType.get()),
          Types.NestedField.optional(2, "data", Types.StringType.get()),
          Types.NestedField.optional(3, "ts", Types.TimestampType.withoutZone()));

  /**
   * 测试场景：Partition Path。
   *
   * <p>验证该方法在 Partition Path 条件下的行为是否符合预期。
   */
  @Test
  public void testPartitionPath() {
    PartitionSpec spec = PartitionSpec.builderFor(SCHEMA).hour("ts").bucket("id", 10).build();

    Transform<Long, Integer> hour = Transforms.hour();
    Transform<Integer, Integer> bucket = Transforms.bucket(10);

    Literal<Long> ts =
        Literal.of("2017-12-01T10:12:55.038194").to(Types.TimestampType.withoutZone());
    Object tsHour = hour.bind(Types.TimestampType.withoutZone()).apply(ts.value());
    Object idBucket = bucket.bind(Types.IntegerType.get()).apply(1);

    Row partition = Row.of(tsHour, idBucket);

    assertThat(spec.partitionToPath(partition))
        .as("Should produce expected partition key")
        .isEqualTo("ts_hour=2017-12-01-10/id_bucket=" + idBucket);
  }

  /**
   * 测试场景：Escaped Strings。
   *
   * <p>验证该方法在 Escaped Strings 条件下的行为是否符合预期。
   */
  @Test
  public void testEscapedStrings() {
    PartitionSpec spec =
        PartitionSpec.builderFor(SCHEMA).identity("data").truncate("data", 10).build();

    assertThat(spec.partitionToPath(Row.of("a/b/c/d", "a/b/c/d")))
        .as("Should escape / as %2F")
        .isEqualTo("data=a%2Fb%2Fc%2Fd/data_trunc=a%2Fb%2Fc%2Fd");
  }
}
