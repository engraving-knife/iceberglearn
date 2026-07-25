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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.StructLike;
import org.apache.iceberg.data.GenericRecord;
import org.apache.iceberg.data.Record;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestStructLikeMap，用于验证 Struct Like Map 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Struct Like Map 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestStructLikeMap {
  private static final Types.StructType STRUCT_TYPE =
      Types.StructType.of(
          Types.NestedField.required(1, "id", Types.IntegerType.get()),
          Types.NestedField.optional(2, "data", Types.LongType.get()));

  /**
   * 测试场景：single record。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testSingleRecord() {
    Record gRecord = GenericRecord.create(STRUCT_TYPE);
    Record record1 = gRecord.copy(ImmutableMap.of("id", 1, "data", "aaa"));

    Map<StructLike, String> map = StructLikeMap.create(STRUCT_TYPE);
    assertThat(map).isEmpty();

    map.put(record1, "1-aaa");
    assertThat(map).hasSize(1).containsEntry(record1, "1-aaa");

    Set<StructLike> keySet = map.keySet();
    assertThat(keySet).hasSize(1).contains(record1);

    Collection<String> values = map.values();
    assertThat(values).hasSize(1).first().isEqualTo("1-aaa");

    Set<Map.Entry<StructLike, String>> entrySet = map.entrySet();
    assertThat(entrySet).hasSize(1);
    for (Map.Entry<StructLike, String> entry : entrySet) {
      assertThat(entry.getKey()).isEqualTo(record1);
      assertThat(entry.getValue()).isEqualTo("1-aaa");
      break;
    }
  }

  /**
   * 测试场景：multiple record。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testMultipleRecord() {
    Record gRecord = GenericRecord.create(STRUCT_TYPE);
    Record record1 = gRecord.copy(ImmutableMap.of("id", 1, "data", "aaa"));
    Record record2 = gRecord.copy(ImmutableMap.of("id", 2, "data", "bbb"));
    Record record3 = gRecord.copy();
    record3.setField("id", 3);
    record3.setField("data", null);

    Map<StructLike, String> map = StructLikeMap.create(STRUCT_TYPE);
    assertThat(map).isEmpty();

    map.putAll(ImmutableMap.of(record1, "1-aaa", record2, "2-bbb", record3, "3-null"));
    assertThat(map)
        .hasSize(3)
        .containsEntry(record1, "1-aaa")
        .containsEntry(record2, "2-bbb")
        .containsEntry(record3, "3-null");

    Set<StructLike> keySet = map.keySet();
    assertThat(keySet).hasSize(3).containsOnly(record1, record2, record3);

    Collection<String> values = map.values();
    assertThat(values).hasSize(3).containsExactlyInAnyOrder("1-aaa", "2-bbb", "3-null");

    Set<Map.Entry<StructLike, String>> entrySet = map.entrySet();
    assertThat(entrySet).hasSize(3);
    Set<StructLike> structLikeSet = Sets.newHashSet();
    Set<String> valueSet = Sets.newHashSet();
    for (Map.Entry<StructLike, String> entry : entrySet) {
      structLikeSet.add(entry.getKey());
      valueSet.add(entry.getValue());
    }
    assertThat(structLikeSet).containsExactlyInAnyOrder(record1, record2, record3);
    assertThat(valueSet).containsExactlyInAnyOrder("1-aaa", "2-bbb", "3-null");
  }

  /**
   * 测试场景：remove。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRemove() {
    Record gRecord = GenericRecord.create(STRUCT_TYPE);
    Record record = gRecord.copy(ImmutableMap.of("id", 1, "data", "aaa"));

    Map<StructLike, String> map = StructLikeMap.create(STRUCT_TYPE);
    map.put(record, "1-aaa");
    assertThat(map).hasSize(1).containsEntry(record, "1-aaa");
    assertThat(map.remove(record)).isEqualTo("1-aaa");
    assertThat(map).isEmpty();

    map.put(record, "1-aaa");
    assertThat(map).containsEntry(record, "1-aaa");
  }

  /**
   * 测试场景：null keys。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testNullKeys() {
    Map<StructLike, String> map = StructLikeMap.create(STRUCT_TYPE);
    assertThat(map).doesNotContainKey(null);

    map.put(null, "aaa");
    assertThat(map).containsEntry(null, "aaa");

    String replacedValue = map.put(null, "bbb");
    assertThat(replacedValue).isEqualTo("aaa");

    String removedValue = map.remove(null);
    assertThat(removedValue).isEqualTo("bbb");
  }

  /**
   * 测试场景：keys with nulls。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testKeysWithNulls() {
    Record recordTemplate = GenericRecord.create(STRUCT_TYPE);
    Record record1 = recordTemplate.copy("id", 1, "data", null);
    Record record2 = recordTemplate.copy("id", 2, "data", null);

    Map<StructLike, String> map = StructLikeMap.create(STRUCT_TYPE);
    map.put(record1, "aaa");
    map.put(record2, "bbb");

    assertThat(map).containsEntry(record1, "aaa").containsEntry(record2, "bbb");

    Record record3 = record1.copy();
    assertThat(map).containsEntry(record3, "aaa");

    assertThat(map.remove(record3)).isEqualTo("aaa");
  }
}
