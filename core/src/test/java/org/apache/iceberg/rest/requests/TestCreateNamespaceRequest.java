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
package org.apache.iceberg.rest.requests;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestCreateNamespaceRequest，用于验证 Create Namespace Request 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Create Namespace Request 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestCreateNamespaceRequest extends RequestResponseTestBase<CreateNamespaceRequest> {

  /* Values used to fill in request fields */
  private static final Namespace NAMESPACE = Namespace.of("accounting", "tax");
  private static final Map<String, String> PROPERTIES = ImmutableMap.of("owner", "Hank");
  private static final Map<String, String> EMPTY_PROPERTIES = ImmutableMap.of();

  @Test
  // Test cases that are JSON that can be created via the Builder
  public void testRoundTripSerDe() throws JsonProcessingException {
    String fullJson =
        "{\"namespace\":[\"accounting\",\"tax\"],\"properties\":{\"owner\":\"Hank\"}}";
    CreateNamespaceRequest req =
        CreateNamespaceRequest.builder().withNamespace(NAMESPACE).setProperties(PROPERTIES).build();
    assertRoundTripSerializesEquallyFrom(fullJson, req);

    String jsonEmptyProperties = "{\"namespace\":[\"accounting\",\"tax\"],\"properties\":{}}";
    CreateNamespaceRequest reqWithExplicitEmptyProperties =
        CreateNamespaceRequest.builder()
            .withNamespace(NAMESPACE)
            .setProperties(EMPTY_PROPERTIES)
            .build();
    assertRoundTripSerializesEquallyFrom(jsonEmptyProperties, reqWithExplicitEmptyProperties);

    CreateNamespaceRequest reqWithImplicitEmptyProperties =
        CreateNamespaceRequest.builder().withNamespace(NAMESPACE).build();
    assertRoundTripSerializesEquallyFrom(jsonEmptyProperties, reqWithImplicitEmptyProperties);

    String jsonWithEmptyNamespace = "{\"namespace\":[],\"properties\":{}}";
    CreateNamespaceRequest reqUsingEmptyNamespace =
        CreateNamespaceRequest.builder().withNamespace(Namespace.empty()).build();
    assertRoundTripSerializesEquallyFrom(jsonWithEmptyNamespace, reqUsingEmptyNamespace);
  }

  @Test
  // Test cases that can't be constructed with our Builder class but that will parse correctly
  public void testCanDeserializeWithoutDefaultValues() throws JsonProcessingException {
    CreateNamespaceRequest req = CreateNamespaceRequest.builder().withNamespace(NAMESPACE).build();
    String jsonWithNullProperties = "{\"namespace\":[\"accounting\",\"tax\"],\"properties\":null}";
    assertEquals(deserialize(jsonWithNullProperties), req);

    String jsonWithMissingProperties = "{\"namespace\":[\"accounting\",\"tax\"]}";
    assertEquals(deserialize(jsonWithMissingProperties), req);
  }

  /**
   * 测试场景：deserialize invalid request。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeserializeInvalidRequest() {
    String jsonIncorrectTypeForNamespace =
        "{\"namespace\":\"accounting%1Ftax\",\"properties\":null}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonIncorrectTypeForNamespace))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageStartingWith("Cannot parse string array from non-array");

    String jsonIncorrectTypeForProperties =
        "{\"namespace\":[\"accounting\",\"tax\"],\"properties\":[]}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonIncorrectTypeForProperties))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageStartingWith("Cannot deserialize value of type");

    String jsonMisspelledKeys =
        "{\"namepsace\":[\"accounting\",\"tax\"],\"propertiezzzz\":{\"owner\":\"Hank\"}}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonMisspelledKeys))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid namespace: null");

    String emptyJson = "{}";
    Assertions.assertThatThrownBy(() -> deserialize(emptyJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid namespace: null");

    Assertions.assertThatThrownBy(() -> deserialize(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("argument \"content\" is null");
  }

  /**
   * 测试场景：builder does not build invalid requests。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testBuilderDoesNotBuildInvalidRequests() {
    Assertions.assertThatThrownBy(
            () -> CreateNamespaceRequest.builder().withNamespace(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid namespace: null");

    Assertions.assertThatThrownBy(
            () -> CreateNamespaceRequest.builder().setProperties(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid collection of properties: null");

    Map<String, String> mapWithNullKey = Maps.newHashMap();
    mapWithNullKey.put(null, "hello");

    Assertions.assertThatThrownBy(
            () -> CreateNamespaceRequest.builder().setProperties(mapWithNullKey).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid property: null");

    Map<String, String> mapWithMultipleNullValues = Maps.newHashMap();
    mapWithMultipleNullValues.put("a", null);
    mapWithMultipleNullValues.put("b", "b");

    Assertions.assertThatThrownBy(
            () -> CreateNamespaceRequest.builder().setProperties(mapWithMultipleNullValues).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid value for properties [a]: null");
  }

  /** 辅助方法：all fields from spec。 */
  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"namespace", "properties"};
  }

  /** 辅助方法：create example instance。 */
  @Override
  public CreateNamespaceRequest createExampleInstance() {
    return CreateNamespaceRequest.builder()
        .withNamespace(NAMESPACE)
        .setProperties(PROPERTIES)
        .build();
  }

  /** 辅助方法：assert equals。 */
  @Override
  public void assertEquals(CreateNamespaceRequest actual, CreateNamespaceRequest expected) {
    Assertions.assertThat(actual.namespace()).isEqualTo(expected.namespace());
    Assertions.assertThat(actual.properties()).isEqualTo(expected.properties());
  }

  /** 辅助方法：deserialize。 */
  @Override
  public CreateNamespaceRequest deserialize(String json) throws JsonProcessingException {
    CreateNamespaceRequest request = mapper().readValue(json, CreateNamespaceRequest.class);
    request.validate();
    return request;
  }
}
