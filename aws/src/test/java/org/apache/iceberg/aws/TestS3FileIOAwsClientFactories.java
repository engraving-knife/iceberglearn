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
package org.apache.iceberg.aws;

import java.util.Map;
import org.apache.iceberg.aws.s3.S3FileIOAwsClientFactory;
import org.apache.iceberg.aws.s3.S3FileIOProperties;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 文件级说明：测试 TestS3FileIOAwsClientFactories 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 TestS3FileIOAwsClientFactories 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestS3FileIOAwsClientFactories {

  /**
   * 测试场景：3 File IO Impl Catalog Property Defined。
   *
   * <p>验证该方法在 3 File IO Impl Catalog Property Defined 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIOImplCatalogPropertyDefined() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(
        S3FileIOProperties.CLIENT_FACTORY,
        "org.apache.iceberg.aws.s3.DefaultS3FileIOAwsClientFactory");
    Object factoryImpl = S3FileIOAwsClientFactories.initialize(properties);
    Assertions.assertThat(factoryImpl)
        .withFailMessage(
            "should instantiate an object of type S3FileIOAwsClientFactory when s3.client-factory-impl is set")
        .isInstanceOf(S3FileIOAwsClientFactory.class);
  }

  /**
   * 测试场景：3 File IO Impl Catalog Property Not Defined。
   *
   * <p>验证该方法在 3 File IO Impl Catalog Property Not Defined 条件下的行为是否符合预期。
   */
  @Test
  public void testS3FileIOImplCatalogPropertyNotDefined() {
    // don't set anything
    Map<String, String> properties = Maps.newHashMap();
    Object factoryImpl = S3FileIOAwsClientFactories.initialize(properties);
    Assertions.assertThat(factoryImpl)
        .withFailMessage(
            "should instantiate an object of type AwsClientFactory when s3.client-factory-impl is not set")
        .isInstanceOf(AwsClientFactory.class);
  }
}
