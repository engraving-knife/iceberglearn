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
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableList;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestUpdateNamespacePropertiesResponse，用于验证 Update Namespace Properties Response 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Update Namespace Properties Response
 * 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入， 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestUpdateNamespacePropertiesResponse
    extends RequestResponseTestBase<UpdateNamespacePropertiesResponse> {

  /* Values used to fill in response fields */
  private static final List<String> UPDATED = ImmutableList.of("owner");
  private static final List<String> REMOVED = ImmutableList.of("foo");
  private static final List<String> MISSING = ImmutableList.of("bar");
  private static final List<String> EMPTY_LIST = ImmutableList.of();

  /**
   * 测试场景：round trip ser de。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRoundTripSerDe() throws JsonProcessingException {
    // Full request
    String fullJson = "{\"removed\":[\"foo\"],\"updated\":[\"owner\"],\"missing\":[\"bar\"]}";
    assertRoundTripSerializesEquallyFrom(
        fullJson,
        UpdateNamespacePropertiesResponse.builder()
            .addUpdated(UPDATED)
            .addRemoved(REMOVED)
            .addMissing(MISSING)
            .build());

    // Only updated
    String jsonOnlyUpdated = "{\"removed\":[],\"updated\":[\"owner\"],\"missing\":[]}";
    assertRoundTripSerializesEquallyFrom(
        jsonOnlyUpdated, UpdateNamespacePropertiesResponse.builder().addUpdated(UPDATED).build());
    assertRoundTripSerializesEquallyFrom(
        jsonOnlyUpdated, UpdateNamespacePropertiesResponse.builder().addUpdated("owner").build());

    assertRoundTripSerializesEquallyFrom(
        jsonOnlyUpdated,
        UpdateNamespacePropertiesResponse.builder()
            .addUpdated(UPDATED)
            .addMissing(EMPTY_LIST)
            .addRemoved(EMPTY_LIST)
            .build());

    // Only removed
    String jsonOnlyRemoved = "{\"removed\":[\"foo\"],\"updated\":[],\"missing\":[]}";
    assertRoundTripSerializesEquallyFrom(
        jsonOnlyRemoved, UpdateNamespacePropertiesResponse.builder().addRemoved(REMOVED).build());
    assertRoundTripSerializesEquallyFrom(
        jsonOnlyRemoved, UpdateNamespacePropertiesResponse.builder().addRemoved("foo").build());

    assertRoundTripSerializesEquallyFrom(
        jsonOnlyRemoved,
        UpdateNamespacePropertiesResponse.builder()
            .addRemoved(REMOVED)
            .addUpdated(EMPTY_LIST)
            .addMissing(EMPTY_LIST)
            .build());

    // Only missing
    String jsonOnlyMissing = "{\"removed\":[],\"updated\":[],\"missing\":[\"bar\"]}";
    assertRoundTripSerializesEquallyFrom(
        jsonOnlyMissing, UpdateNamespacePropertiesResponse.builder().addMissing(MISSING).build());

    assertRoundTripSerializesEquallyFrom(
        jsonOnlyMissing, UpdateNamespacePropertiesResponse.builder().addMissing("bar").build());

    assertRoundTripSerializesEquallyFrom(
        jsonOnlyMissing,
        UpdateNamespacePropertiesResponse.builder()
            .addMissing(MISSING)
            .addUpdated(EMPTY_LIST)
            .addRemoved(EMPTY_LIST)
            .build());

    // All fields are empty
    String jsonWithAllFieldsAsEmptyList = "{\"removed\":[],\"updated\":[],\"missing\":[]}";
    assertRoundTripSerializesEquallyFrom(
        jsonWithAllFieldsAsEmptyList, UpdateNamespacePropertiesResponse.builder().build());
  }

  @Test
  // Test cases that can't be constructed with our Builder class e2e but that will parse correctly
  public void testCanDeserializeWithoutDefaultValues() throws JsonProcessingException {
    // only updated
    UpdateNamespacePropertiesResponse onlyUpdated =
        UpdateNamespacePropertiesResponse.builder().addUpdated(UPDATED).build();
    String jsonOnlyUpdatedOthersNull =
        "{\"removed\":null,\"updated\":[\"owner\"],\"missing\":null}";
    assertEquals(deserialize(jsonOnlyUpdatedOthersNull), onlyUpdated);

    String jsonOnlyUpdatedOthersMissing = "{\"updated\":[\"owner\"]}";
    assertEquals(deserialize(jsonOnlyUpdatedOthersMissing), onlyUpdated);

    // Only removed
    UpdateNamespacePropertiesResponse onlyRemoved =
        UpdateNamespacePropertiesResponse.builder().addRemoved(REMOVED).build();
    String jsonOnlyRemovedOthersNull = "{\"removed\":[\"foo\"],\"updated\":null,\"missing\":null}";
    assertEquals(deserialize(jsonOnlyRemovedOthersNull), onlyRemoved);

    String jsonOnlyRemovedOthersMissing = "{\"removed\":[\"foo\"]}";
    assertEquals(deserialize(jsonOnlyRemovedOthersMissing), onlyRemoved);

    // Only missing
    UpdateNamespacePropertiesResponse onlyMissing =
        UpdateNamespacePropertiesResponse.builder().addMissing(MISSING).build();
    String jsonOnlyMissingFieldOthersNull =
        "{\"removed\":null,\"updated\":null,\"missing\":[\"bar\"]}";
    assertEquals(deserialize(jsonOnlyMissingFieldOthersNull), onlyMissing);

    String jsonOnlyMissingFieldIsPresent = "{\"missing\":[\"bar\"]}";
    assertEquals(deserialize(jsonOnlyMissingFieldIsPresent), onlyMissing);

    // all fields are missing
    UpdateNamespacePropertiesResponse noValues =
        UpdateNamespacePropertiesResponse.builder().build();
    String emptyJson = "{}";
    assertEquals(deserialize(emptyJson), noValues);
  }

  /**
   * 测试场景：deserialize invalid response。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeserializeInvalidResponse() {
    // Invalid top-level types
    String jsonInvalidTypeOnRemovedField =
        "{\"removed\":{\"foo\":true},\"updated\":[\"owner\"],\"missing\":[\"bar\"]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonInvalidTypeOnRemovedField))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining(
            "Cannot deserialize value of type `java.util.ArrayList<java.lang.String>`");

    String jsonInvalidTypeOnUpdatedField = "{\"updated\":\"owner\",\"missing\":[\"bar\"]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonInvalidTypeOnUpdatedField))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("Cannot construct instance of `java.util.ArrayList`");

    // Valid top-level (array) types, but at least one entry in the list is not the expected type
    String jsonInvalidValueOfTypeIntNestedInRemovedList =
        "{\"removed\":[\"foo\", \"bar\", 123456], ,\"updated\":[\"owner\"],\"missing\":[\"bar\"]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonInvalidValueOfTypeIntNestedInRemovedList))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageContaining("Unexpected character (',' (code 44))");

    // Exception comes from Jackson
    Assertions.assertThatThrownBy(() -> deserialize(null))
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
    List<String> listContainingNull = Lists.newArrayList("a", null, null);

    // updated
    Assertions.assertThatThrownBy(
            () -> UpdateNamespacePropertiesResponse.builder().addUpdated((String) null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid updated property: null");

    Assertions.assertThatThrownBy(
            () ->
                UpdateNamespacePropertiesResponse.builder().addUpdated((List<String>) null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid updated property list: null");

    Assertions.assertThatThrownBy(
            () ->
                UpdateNamespacePropertiesResponse.builder().addUpdated(listContainingNull).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid updated property: null");

    // removed
    Assertions.assertThatThrownBy(
            () -> UpdateNamespacePropertiesResponse.builder().addRemoved((String) null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid removed property: null");

    Assertions.assertThatThrownBy(
            () ->
                UpdateNamespacePropertiesResponse.builder().addRemoved((List<String>) null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid removed property list: null");

    Assertions.assertThatThrownBy(
            () ->
                UpdateNamespacePropertiesResponse.builder().addRemoved(listContainingNull).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid removed property: null");

    // missing
    Assertions.assertThatThrownBy(
            () -> UpdateNamespacePropertiesResponse.builder().addMissing((String) null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid missing property: null");

    Assertions.assertThatThrownBy(
            () ->
                UpdateNamespacePropertiesResponse.builder().addMissing((List<String>) null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid missing property list: null");

    Assertions.assertThatThrownBy(
            () ->
                UpdateNamespacePropertiesResponse.builder().addMissing(listContainingNull).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid missing property: null");
  }

  /** 辅助方法：all fields from spec。 */
  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"updated", "removed", "missing"};
  }

  /** 辅助方法：create example instance。 */
  @Override
  public UpdateNamespacePropertiesResponse createExampleInstance() {
    return UpdateNamespacePropertiesResponse.builder()
        .addUpdated(UPDATED)
        .addMissing(MISSING)
        .addRemoved(REMOVED)
        .build();
  }

  /** 辅助方法：assert equals。 */
  @Override
  public void assertEquals(
      UpdateNamespacePropertiesResponse actual, UpdateNamespacePropertiesResponse expected) {
    Assertions.assertThat(actual.updated())
        .as("Properties updated should be equal")
        .containsExactlyInAnyOrderElementsOf(expected.updated());
    Assertions.assertThat(actual.removed())
        .as("Properties removed should be equal")
        .containsExactlyInAnyOrderElementsOf(expected.removed());
    Assertions.assertThat(actual.missing())
        .as("Properties missing should be equal")
        .containsExactlyInAnyOrderElementsOf(expected.missing());
  }

  /** 辅助方法：deserialize。 */
  @Override
  public UpdateNamespacePropertiesResponse deserialize(String json) throws JsonProcessingException {
    UpdateNamespacePropertiesResponse resp =
        mapper().readValue(json, UpdateNamespacePropertiesResponse.class);
    resp.validate();
    return resp;
  }
}
