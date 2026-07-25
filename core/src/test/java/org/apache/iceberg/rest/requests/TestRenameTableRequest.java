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
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.catalog.TableIdentifierParser;
import org.apache.iceberg.rest.RequestResponseTestBase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestRenameTableRequest，用于验证 Rename Table Request 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Rename Table Request 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestRenameTableRequest extends RequestResponseTestBase<RenameTableRequest> {

  /* Values used to fill in request fields */
  private static final Namespace NAMESPACE = Namespace.of("accounting", "tax");
  private static final TableIdentifier TAX_PAID = TableIdentifier.of(NAMESPACE, "paid");
  private static final TableIdentifier TAX_PAID_RENAMED =
      TableIdentifier.of(NAMESPACE, "paid_2022");

  /**
   * 测试场景：round trip ser de。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testRoundTripSerDe() throws JsonProcessingException {
    String sourceJson = TableIdentifierParser.toJson(TAX_PAID);
    String destinationJson = TableIdentifierParser.toJson(TAX_PAID_RENAMED);
    String fullJson =
        String.format("{\"source\":%s,\"destination\":%s}", sourceJson, destinationJson);
    RenameTableRequest req =
        RenameTableRequest.builder().withSource(TAX_PAID).withDestination(TAX_PAID_RENAMED).build();

    assertRoundTripSerializesEquallyFrom(fullJson, req);
  }

  /**
   * 测试场景：deserialize invalid request throws。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testDeserializeInvalidRequestThrows() {
    String jsonSourceNullName =
        "{\"source\":{\"namespace\":[\"accounting\",\"tax\"],\"name\":null},"
            + "\"destination\":{\"namespace\":[\"accounting\",\"tax\"],\"name\":\"paid_2022\"}}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonSourceNullName))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageStartingWith("Cannot parse to a string value: name: null");

    String jsonDestinationNullName =
        "{\"source\":{\"namespace\":[\"accounting\",\"tax\"],\"name\":\"paid\"},"
            + "\"destination\":{\"namespace\":[\"accounting\",\"tax\"],\"name\":null}}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonDestinationNullName))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageStartingWith("Cannot parse to a string value: name: null");

    String jsonSourceMissingName =
        "{\"source\":{\"namespace\":[\"accounting\",\"tax\"]},"
            + "\"destination\":{\"name\":\"paid_2022\"}}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonSourceMissingName))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageStartingWith("Cannot parse missing string: name");

    String jsonDestinationMissingName =
        "{\"source\":{\"namespace\":[\"accounting\",\"tax\"],\"name\":\"paid\"},"
            + "\"destination\":{\"namespace\":[\"accounting\",\"tax\"]}}";
    Assertions.assertThatThrownBy(() -> deserialize(jsonDestinationMissingName))
        .isInstanceOf(JsonProcessingException.class)
        .hasMessageStartingWith("Cannot parse missing string: name");

    String emptyJson = "{}";
    Assertions.assertThatThrownBy(() -> deserialize(emptyJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid source table: null");

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
            () ->
                RenameTableRequest.builder()
                    .withSource(null)
                    .withDestination(TAX_PAID_RENAMED)
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid source table identifier: null");

    Assertions.assertThatThrownBy(
            () -> RenameTableRequest.builder().withSource(TAX_PAID).withDestination(null).build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("Invalid destination table identifier: null");
  }

  /** 辅助方法：all fields from spec。 */
  @Override
  public String[] allFieldsFromSpec() {
    return new String[] {"source", "destination"};
  }

  /** 辅助方法：create example instance。 */
  @Override
  public RenameTableRequest createExampleInstance() {
    return RenameTableRequest.builder()
        .withSource(TAX_PAID)
        .withDestination(TAX_PAID_RENAMED)
        .build();
  }

  /** 辅助方法：assert equals。 */
  @Override
  public void assertEquals(RenameTableRequest actual, RenameTableRequest expected) {
    Assertions.assertThat(actual.source())
        .as("Source table identifier should be equal")
        .isEqualTo(expected.source());
    Assertions.assertThat(actual.destination())
        .as("Destination table identifier should be equal")
        .isEqualTo(expected.destination());
  }

  /** 辅助方法：deserialize。 */
  @Override
  public RenameTableRequest deserialize(String json) throws JsonProcessingException {
    RenameTableRequest request = mapper().readValue(json, RenameTableRequest.class);
    request.validate();
    return request;
  }
}
