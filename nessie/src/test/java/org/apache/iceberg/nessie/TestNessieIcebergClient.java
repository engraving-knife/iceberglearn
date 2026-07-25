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

import java.io.IOException;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.Reference;

/**
 * 文件级说明：测试 TestNessieIcebergClient 的功能。
 *
 * <p>所属模块：iceberg-nessie。职责：验证 TestNessieIcebergClient 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestNessieIcebergClient extends BaseTestIceberg {

  private static final String BRANCH = "test-nessie-client";

  /** 辅助方法：TestNessieIcebergClient。 */
  public TestNessieIcebergClient() {
    super(BRANCH);
  }

  /**
   * 测试场景：With Null Ref Loads Main。
   *
   * <p>验证该方法在 With Null Ref Loads Main 条件下的行为是否符合预期。
   */
  @Test
  public void testWithNullRefLoadsMain() throws NessieNotFoundException {
    NessieIcebergClient client = new NessieIcebergClient(api, null, null, ImmutableMap.of());
    Assertions.assertThat(client.getRef().getReference())
        .isEqualTo(api.getReference().refName("main").get());
  }

  /**
   * 测试场景：With Null Hash。
   *
   * <p>验证该方法在 With Null Hash 条件下的行为是否符合预期。
   */
  @Test
  public void testWithNullHash() throws NessieNotFoundException {
    NessieIcebergClient client = new NessieIcebergClient(api, BRANCH, null, ImmutableMap.of());
    Assertions.assertThat(client.getRef().getReference())
        .isEqualTo(api.getReference().refName(BRANCH).get());
  }

  /**
   * 测试场景：With Reference。
   *
   * <p>验证该方法在 With Reference 条件下的行为是否符合预期。
   */
  @Test
  public void testWithReference() throws NessieNotFoundException {
    NessieIcebergClient client = new NessieIcebergClient(api, "main", null, ImmutableMap.of());

    Assertions.assertThat(client.withReference(null, null)).isEqualTo(client);
    Assertions.assertThat(client.withReference("main", null)).isNotEqualTo(client);
    Assertions.assertThat(
            client.withReference("main", api.getReference().refName("main").get().getHash()))
        .isEqualTo(client);

    Assertions.assertThat(client.withReference(BRANCH, null)).isNotEqualTo(client);
    Assertions.assertThat(
            client.withReference(BRANCH, api.getReference().refName(BRANCH).get().getHash()))
        .isNotEqualTo(client);
  }

  /**
   * 测试场景：With Reference After Recreating Branch。
   *
   * <p>验证该方法在 With Reference After Recreating Branch 条件下的行为是否符合预期。
   */
  @Test
  public void testWithReferenceAfterRecreatingBranch()
      throws NessieConflictException, NessieNotFoundException {
    String branch = "branchToBeDropped";
    createBranch(branch);
    NessieIcebergClient client = new NessieIcebergClient(api, branch, null, ImmutableMap.of());

    // just create a new commit on the branch and then delete & re-create it
    Namespace namespace = Namespace.of("a");
    client.createNamespace(namespace, ImmutableMap.of());
    Assertions.assertThat(client.listNamespaces(namespace)).isNotNull();
    client
        .getApi()
        .deleteBranch()
        .branch((Branch) client.getApi().getReference().refName(branch).get())
        .delete();
    createBranch(branch);

    // make sure the client uses the re-created branch
    Reference ref = client.getApi().getReference().refName(branch).get();
    Assertions.assertThat(client.withReference(branch, null).getRef().getReference())
        .isEqualTo(ref);
    Assertions.assertThat(client.withReference(branch, null)).isNotEqualTo(client);
  }

  /**
   * 测试场景：Invalid Client Api Version。
   *
   * <p>验证该方法在 Invalid Client Api Version 条件下的行为是否符合预期。
   */
  @Test
  public void testInvalidClientApiVersion() throws IOException {
    try (NessieCatalog newCatalog = new NessieCatalog()) {
      newCatalog.setConf(hadoopConfig);
      ImmutableMap.Builder<String, String> options =
          ImmutableMap.<String, String>builder().put("client-api-version", "3");
      Assertions.assertThatThrownBy(() -> newCatalog.initialize("nessie", options.buildOrThrow()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Unsupported client-api-version: 3. Can only be 1 or 2");
    }
  }
}
