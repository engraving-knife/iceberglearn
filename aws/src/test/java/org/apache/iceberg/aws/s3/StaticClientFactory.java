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
package org.apache.iceberg.aws.s3;

import java.util.Map;
import org.apache.iceberg.aws.AwsClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.glue.GlueClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * 文件级说明：测试 StaticClientFactory 的功能。
 *
 * <p>所属模块：iceberg-aws。职责：验证 StaticClientFactory 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
class StaticClientFactory implements AwsClientFactory {
  static S3Client client;

  /** 辅助方法：s3。 */
  @Override
  public S3Client s3() {
    return client;
  }

  /** 辅助方法：glue。 */
  @Override
  public GlueClient glue() {
    return null;
  }

  /** 辅助方法：kms。 */
  @Override
  public KmsClient kms() {
    return null;
  }

  /** 辅助方法：dynamo。 */
  @Override
  public DynamoDbClient dynamo() {
    return null;
  }

  /** 辅助方法：initialize。 */
  @Override
  public void initialize(Map<String, String> properties) {}
}
