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
package org.apache.iceberg.rest.responses;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestListNamespacesResponse，用于验证 List Namespaces Response 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 List Namespaces Response 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestListNamespacesResponse extends RequestResponseTestBase<ListNamespacesResponse> {

  private static final List<Namespace> NAMESPACES =
      ImmutableList.of(Namespace.of("accounting"), Namespace.of("tax"));

  /**
   * 测试场景：round trip ser de。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRoundTripSerDe() throws JsonProcessingException {
    String fullJson = "{\"namespaces\":[[\"accounting\"],[\"tax\"]]}";
    ListNamespacesResponse fullValue = ListNamespacesResponse.builder().addAll(NAMESPACES).build();
    assertRoundTripSerializesEquallyFrom(fullJson, fullValue);

    String emptyNamespaces = "{\"namespaces\":[]}";
    assertRoundTripSerializesEquallyFrom(emptyNamespaces, ListNamespacesResponse.builder().build());
  }

  /**
   * 测试场景：deserialize invalid response throws。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeserializeInvalidResponseThrows() {
    String jsonNamespacesHasWrongType = "{\"namespaces\":\"accounting\"}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonNamespacesHasWrongType))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining(
            "Cannot deserialize value of type `java.util.ArrayList<org.apache.iceberg.catalog.Namespace>`");

    String emptyJson = "{}";
    Assertions.assertThatThrownBy(() -> deserialize(emptyJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid namespace: null");

    String jsonWithKeysSpelledIncorrectly = "{\"namepsacezz\":[\"accounting\",\"tax\"]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonWithKeysSpelledIncorrectly))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid namespace: null");

    String nullJson = null;
    Assertions.assertThatThrownBy(() -> deserialize(nullJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("argument \"content\" is null");
  }

  /**
   * 测试场景：builder does not create invalid objects。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBuilderDoesNotCreateInvalidObjects() {
    Assertions.assertThatThrownBy(() -> ListNamespacesResponse.builder().add(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid namespace: null");

    Assertions.assertThatThrownBy(() -> ListNamespacesResponse.builder().addAll(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid namespace list: null");

    List<Namespace> listWithNullElement = Lists.newArrayList(Namespace.of("a"), null);
    Assertions.assertThatThrownBy(
            () -> ListNamespacesResponse.builder().addAll(listWithNullElement).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid namespace: null");
  }

  /** 辅助方法：all fields from spec。 */
  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"namespaces"};
  }

  /** 辅助方法：create example instance。 */
  @Override
  public ListNamespacesResponse createExampleInstance() {
    return ListNamespacesResponse.builder().addAll(NAMESPACES).build();
  }

  /** 辅助方法：assert equals。 */
  @Override
  public void assertEquals(ListNamespacesResponse actual, ListNamespacesResponse expected) {
    Assertions.assertThat(actual.namespaces())
        .as("Namespaces list should be equal")
        .hasSize(expected.namespaces().size())
        .containsExactlyInAnyOrderElementsOf(expected.namespaces());
  }

  /** 辅助方法：deserialize。 */
  @Override
  public ListNamespacesResponse deserialize(String json) throws JsonProcessingException {
    ListNamespacesResponse resp = mapper().readValue(json, ListNamespacesResponse.class);
    resp.validate();
    return resp;
  }
}
