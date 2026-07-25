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
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestListTablesResponse，用于验证 List Tables Response 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 List Tables Response 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestListTablesResponse extends RequestResponseTestBase<ListTablesResponse> {

  private static final List<TableIdentifier> IDENTIFIERS =
      ImmutableList.of(TableIdentifier.of(Namespace.of("accounting", "tax"), "paid"));

  /**
   * 测试场景：round trip ser de。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRoundTripSerDe() throws JsonProcessingException {
    String fullJson =
        "{\"identifiers\":[{\"namespace\":[\"accounting\",\"tax\"],\"name\":\"paid\"}]}";
    assertRoundTripSerializesEquallyFrom(
        fullJson, ListTablesResponse.builder().addAll(IDENTIFIERS).build());

    String emptyIdentifiers = "{\"identifiers\":[]}";
    assertRoundTripSerializesEquallyFrom(emptyIdentifiers, ListTablesResponse.builder().build());
  }

  /**
   * 测试场景：deserialize invalid responses throws。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeserializeInvalidResponsesThrows() {
    String identifiersHasWrongType = "{\"identifiers\":\"accounting%1Ftax\"}";
    Assertions.assertThatThrownBy(() -> deserialize(identifiersHasWrongType))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining(
            "Cannot deserialize value of type `java.util.ArrayList<org.apache.iceberg.catalog.TableIdentifier>`");

    String emptyJson = "{}";
    Assertions.assertThatThrownBy(() -> deserialize(emptyJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid identifier list: null");

    String jsonWithKeysSpelledIncorrectly =
        "{\"identifyrezzzz\":[{\"namespace\":[\"accounting\",\"tax\"],\"name\":\"paid\"}]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonWithKeysSpelledIncorrectly))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid identifier list: null");

    String jsonWithInvalidIdentifiersInList =
        "{\"identifiers\":[{\"namespace\":\"accounting.tax\",\"name\":\"paid\"}]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonWithInvalidIdentifiersInList))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining(
            "Cannot parse JSON array from non-array value: namespace: \"accounting.tax\"");

    String jsonWithInvalidIdentifiersInList2 =
        "{\"identifiers\":[{\"namespace\":[\"accounting\",\"tax\"],\"name\":\"paid\"},\"accounting.tax.paid\"]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonWithInvalidIdentifiersInList2))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("Cannot parse missing or non-object table identifier");

    String jsonWithInvalidTypeForNamePartOfIdentifier =
        "{\"identifiers\":[{\"namespace\":[\"accounting\",\"tax\"],\"name\":true}]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonWithInvalidTypeForNamePartOfIdentifier))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("Cannot parse to a string value");

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
    Assertions.assertThatThrownBy(() -> ListTablesResponse.builder().add(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid table identifier: null");

    Assertions.assertThatThrownBy(() -> ListTablesResponse.builder().addAll(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid table identifier list: null");

    List<TableIdentifier> listWithNullElement =
        Lists.newArrayList(TableIdentifier.of(Namespace.of("foo"), "bar"), null);
    Assertions.assertThatThrownBy(() -> ListTablesResponse.builder().addAll(listWithNullElement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid table identifier: null");
  }

  /** 辅助方法：all fields from spec。 */
  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"identifiers"};
  }

  /** 辅助方法：create example instance。 */
  @Override
  public ListTablesResponse createExampleInstance() {
    return ListTablesResponse.builder().addAll(IDENTIFIERS).build();
  }

  /** 辅助方法：assert equals。 */
  @Override
  public void assertEquals(ListTablesResponse actual, ListTablesResponse expected) {
    Assertions.assertThat(actual.identifiers())
        .as("Identifiers should be equal")
        .hasSameSizeAs(expected.identifiers())
        .containsExactlyInAnyOrderElementsOf(expected.identifiers());
  }

  /** 辅助方法：deserialize。 */
  @Override
  public ListTablesResponse deserialize(String json) throws JsonProcessingException {
    ListTablesResponse resp = mapper().readValue(json, ListTablesResponse.class);
    resp.validate();
    return resp;
  }
}
