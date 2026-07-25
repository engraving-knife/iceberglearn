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
package org.apache.iceberg.nessie;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.TestCatalogUtil;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.projectnessie.client.NessieClientBuilder.AbstractNessieClientBuilder;
import org.projectnessie.client.NessieConfigConstants;
import org.projectnessie.client.api.NessieApi;
import org.projectnessie.client.http.HttpClientBuilder;

/**
 * 文件级说明：测试 TestCustomNessieClient 的功能。
 *
 * <p>所属模块：iceberg-nessie。职责：验证 TestCustomNessieClient 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestCustomNessieClient extends BaseTestIceberg {

  /** 辅助方法：TestCustomNessieClient。 */
  public TestCustomNessieClient() {
    super("main");
  }

  /**
   * 测试场景：No Custom Client。
   *
   * <p>验证该方法在 No Custom Client 条件下的行为是否符合预期。
   */
  @Test
  public void testNoCustomClient() {
    NessieCatalog catalog = new NessieCatalog();
    catalog.initialize(
        "nessie",
        ImmutableMap.of(
            CatalogProperties.WAREHOUSE_LOCATION,
            temp.toUri().toString(),
            CatalogProperties.URI,
            uri,
            "client-api-version",
            apiVersion));
  }

  /**
   * 测试场景：Unnecessary Default Custom Client。
   *
   * <p>验证该方法在 Unnecessary Default Custom Client 条件下的行为是否符合预期。
   */
  @Test
  public void testUnnecessaryDefaultCustomClient() {
    NessieCatalog catalog = new NessieCatalog();
    catalog.initialize(
        "nessie",
        ImmutableMap.of(
            CatalogProperties.WAREHOUSE_LOCATION,
            temp.toUri().toString(),
            CatalogProperties.URI,
            uri,
            NessieConfigConstants.CONF_NESSIE_CLIENT_BUILDER_IMPL,
            HttpClientBuilder.class.getName(),
            "client-api-version",
            apiVersion));
  }

  /**
   * 测试场景：Non Existent Custom Client。
   *
   * <p>验证该方法在 Non Existent Custom Client 条件下的行为是否符合预期。
   */
  @Test
  public void testNonExistentCustomClient() {
    assertThatThrownBy(
            () -> {
              NessieCatalog catalog = new NessieCatalog();
              catalog.initialize(
                  "nessie",
                  ImmutableMap.of(
                      CatalogProperties.WAREHOUSE_LOCATION,
                      temp.toUri().toString(),
                      CatalogProperties.URI,
                      uri,
                      NessieConfigConstants.CONF_NESSIE_CLIENT_BUILDER_IMPL,
                      "non.existent.ClientBuilderImpl"));
            })
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Cannot load Nessie client builder implementation class");
  }

  /**
   * 测试场景：Custom Client By Impl。
   *
   * <p>验证该方法在 Custom Client By Impl 条件下的行为是否符合预期。
   */
  @Test
  public void testCustomClientByImpl() {
    assertThatThrownBy(
            () -> {
              NessieCatalog catalog = new NessieCatalog();
              catalog.initialize(
                  "nessie",
                  ImmutableMap.of(
                      CatalogProperties.WAREHOUSE_LOCATION,
                      temp.toUri().toString(),
                      CatalogProperties.URI,
                      uri,
                      NessieConfigConstants.CONF_NESSIE_CLIENT_BUILDER_IMPL,
                      DummyClientBuilderImpl.class.getName()));
            })
        .isInstanceOf(RuntimeException.class)
        .hasMessage("BUILD CALLED");
  }

  /**
   * 测试场景：Custom Client By Name。
   *
   * <p>验证该方法在 Custom Client By Name 条件下的行为是否符合预期。
   */
  @Test
  public void testCustomClientByName() {
    assertThatThrownBy(
            () -> {
              NessieCatalog catalog = new NessieCatalog();
              catalog.initialize(
                  "nessie",
                  ImmutableMap.of(
                      CatalogProperties.WAREHOUSE_LOCATION,
                      temp.toUri().toString(),
                      CatalogProperties.URI,
                      uri,
                      NessieConfigConstants.CONF_NESSIE_CLIENT_NAME,
                      "Dummy"));
            })
        .isInstanceOf(RuntimeException.class)
        .hasMessage("BUILD CALLED");
  }

  /**
   * 测试场景：Alternative Initialize With Nulls。
   *
   * <p>验证该方法在 Alternative Initialize With Nulls 条件下的行为是否符合预期。
   */
  @Test
  public void testAlternativeInitializeWithNulls() {
    NessieCatalog catalog = new NessieCatalog();
    NessieIcebergClient client = new NessieIcebergClient(null, null, null, null);
    FileIO fileIO = new TestCatalogUtil.TestFileIONoArg();

    assertThatThrownBy(() -> catalog.initialize("nessie", null, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("client must be non-null");

    assertThatThrownBy(() -> catalog.initialize("nessie", client, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("fileIO must be non-null");

    assertThatThrownBy(() -> catalog.initialize("nessie", client, fileIO, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("catalogOptions must be non-null");
  }

  @SuppressWarnings("rawtypes")
  public static final class DummyClientBuilderImpl extends AbstractNessieClientBuilder {

    /** 辅助方法：builder。 */
    @SuppressWarnings("unused")
    public static DummyClientBuilderImpl builder() {
      return new DummyClientBuilderImpl();
    }

    /** 辅助方法：build。 */
    @Override
    public <A extends NessieApi> A build(Class<A> apiContract) {
      throw new RuntimeException("BUILD CALLED");
    }

    /** 辅助方法：name。 */
    @Override
    public String name() {
      return "Dummy";
    }

    /** 辅助方法：priority。 */
    @Override
    public int priority() {
      return 42;
    }
  }
}
