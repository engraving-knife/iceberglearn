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
package org.apache.iceberg.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Set;
import org.apache.iceberg.relocated.com.google.common.collect.Sets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：RequestResponseTestBase，用于验证 Request Response 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Request Response 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public abstract class RequestResponseTestBase<T extends RESTMessage> {

  private static final ObjectMapper MAPPER = RESTObjectMapper.mapper();

  /** 辅助方法：mapper。 */
  public static ObjectMapper mapper() {
    return MAPPER;
  }

  /** 辅助方法：all fields from spec。 */
  public abstract String[] allFieldsFromSpec();

  /** 辅助方法：create example instance。 */
  public abstract T createExampleInstance();

  /** 辅助方法：assert equals。 */
  public abstract void assertEquals(T actual, T expected);

  /** 辅助方法：deserialize。 */
  public abstract T deserialize(String json) throws JsonProcessingException;

  /** 辅助方法：serialize。 */
  public String serialize(T object) throws JsonProcessingException {
    return MAPPER.writeValueAsString(object);
  }

  /**
   * 测试场景：has only known fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testHasOnlyKnownFields() {
    Set<String> fieldsFromSpec = Sets.newHashSet();
    Collections.addAll(fieldsFromSpec, allFieldsFromSpec());
    try {
      JsonNode node = mapper().readValue(serialize(createExampleInstance()), JsonNode.class);
      for (String field : fieldsFromSpec) {
        Assertions.assertThat(node.has(field)).as("Should have field: %s", field).isTrue();
      }

      for (String field : ((Iterable<? extends String>) node::fieldNames)) {
        Assertions.assertThat(fieldsFromSpec)
            .as("Should not have field: %s", field)
            .contains(field);
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /** 辅助方法：assert round trip serializes equally from。 */
  protected void assertRoundTripSerializesEquallyFrom(String json, T expected)
      throws JsonProcessingException {
    // Check that the JSON deserializes into the expected value;
    T actual = deserialize(json);
    assertEquals(actual, expected);

    // Check that the deserialized value serializes back into the original JSON
    Assertions.assertThat(serialize(expected)).isEqualTo(json);
  }
}
