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
package org.apache.iceberg.aliyun.oss;

import com.aliyun.oss.OSS;
import org.apache.iceberg.aliyun.TestUtility;
import org.apache.iceberg.util.SerializableSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

/**
 * 文件级说明：测试 AliyunOSSTestBase 的功能。
 *
 * <p>所属模块：iceberg-aliyun。职责：验证 AliyunOSSTestBase 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public abstract class AliyunOSSTestBase {
  @ClassRule public static final AliyunOSSTestRule OSS_TEST_RULE = TestUtility.initialize();

  private final SerializableSupplier<OSS> ossClient = OSS_TEST_RULE::createOSSClient;
  private final String bucketName = OSS_TEST_RULE.testBucketName();
  private final String keyPrefix = OSS_TEST_RULE.keyPrefix();

  /** 辅助方法：before。 */
  @Before
  public void before() {
    OSS_TEST_RULE.setUpBucket(bucketName);
  }

  /** 辅助方法：after。 */
  @After
  public void after() {
    OSS_TEST_RULE.tearDownBucket(bucketName);
  }

  /** 辅助方法：location。 */
  protected String location(String key) {
    return String.format("oss://%s/%s%s", bucketName, keyPrefix, key);
  }

  /** 辅助方法：ossClient。 */
  protected SerializableSupplier<OSS> ossClient() {
    return ossClient;
  }
}
