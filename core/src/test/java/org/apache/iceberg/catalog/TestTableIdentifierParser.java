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
package org.apache.iceberg.catalog;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 测试类：TestTableIdentifierParser，用于验证 Table Identifier Parser 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 Table Identifier Parser 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestTableIdentifierParser {

  /**
   * 测试场景：table identifier to json。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTableIdentifierToJson() {
    String json = "{\"namespace\":[\"accounting\",\"tax\"],\"name\":\"paid\"}";
    TableIdentifier identifier = TableIdentifier.of(Namespace.of("accounting", "tax"), "paid");
    Assertions.assertThat(TableIdentifierParser.toJson(identifier))
        .as("Should be able to serialize a table identifier with both namespace and name")
        .isEqualTo(json);

    TableIdentifier identifierWithEmptyNamespace = TableIdentifier.of(Namespace.empty(), "paid");
    String jsonWithEmptyNamespace = "{\"namespace\":[],\"name\":\"paid\"}";
    Assertions.assertThat(TableIdentifierParser.toJson(identifierWithEmptyNamespace))
        .as("Should be able to serialize a table identifier that uses the empty namespace")
        .isEqualTo(jsonWithEmptyNamespace);
  }

  /**
   * 测试场景：table identifier from json。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testTableIdentifierFromJson() {
    String json = "{\"namespace\":[\"accounting\",\"tax\"],\"name\":\"paid\"}";
    TableIdentifier identifier = TableIdentifier.of(Namespace.of("accounting", "tax"), "paid");
    Assertions.assertThat(TableIdentifierParser.fromJson(json))
        .as("Should be able to deserialize a valid table identifier")
        .isEqualTo(identifier);

    TableIdentifier identifierWithEmptyNamespace = TableIdentifier.of(Namespace.empty(), "paid");
    String jsonWithEmptyNamespace = "{\"namespace\":[],\"name\":\"paid\"}";
    Assertions.assertThat(TableIdentifierParser.fromJson(jsonWithEmptyNamespace))
        .as("Should be able to deserialize a valid multi-level table identifier")
        .isEqualTo(identifierWithEmptyNamespace);

    String identifierMissingNamespace = "{\"name\":\"paid\"}";
    Assertions.assertThat(TableIdentifierParser.fromJson(identifierMissingNamespace))
        .as(
            "Should implicitly convert a missing namespace into the the empty namespace when parsing")
        .isEqualTo(identifierWithEmptyNamespace);
  }

  /**
   * 测试场景：fail parsing when null or empty json。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFailParsingWhenNullOrEmptyJson() {
    String nullJson = null;
    Assertions.assertThatThrownBy(() -> TableIdentifierParser.fromJson(nullJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse table identifier from invalid JSON: null");

    String emptyString = "";
    Assertions.assertThatThrownBy(() -> TableIdentifierParser.fromJson(emptyString))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse table identifier from invalid JSON: ''");

    String emptyJson = "{}";
    Assertions.assertThatThrownBy(() -> TableIdentifierParser.fromJson(emptyJson))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: name");

    String emptyJsonArray = "[]";
    Assertions.assertThatThrownBy(() -> TableIdentifierParser.fromJson(emptyJsonArray))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing or non-object table identifier: []");
  }

  /**
   * 测试场景：fail parsing when missing required fields。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFailParsingWhenMissingRequiredFields() {
    String identifierMissingName = "{\"namespace\":[\"accounting\",\"tax\"]}";
    Assertions.assertThatThrownBy(() -> TableIdentifierParser.fromJson(identifierMissingName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: name");
  }

  /**
   * 测试场景：fail when fields have invalid values。
   *
   * <p>验证逻辑：针对该场景调用被测方法，断言返回结果或表/快照状态符合预期。
   */
  @Test
  public void testFailWhenFieldsHaveInvalidValues() {
    String invalidNamespace = "{\"namespace\":\"accounting.tax\",\"name\":\"paid\"}";
    Assertions.assertThatThrownBy(() -> TableIdentifierParser.fromJson(invalidNamespace))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse JSON array from non-array value: namespace: \"accounting.tax\"");

    String invalidName = "{\"namespace\":[\"accounting\",\"tax\"],\"name\":1234}";
    Assertions.assertThatThrownBy(() -> TableIdentifierParser.fromJson(invalidName))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse to a string value: name: 1234");
  }
}
