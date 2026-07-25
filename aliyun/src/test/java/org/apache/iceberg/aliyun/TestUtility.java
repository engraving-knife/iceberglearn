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
package org.apache.iceberg.aliyun;

import org.apache.iceberg.aliyun.oss.AliyunOSSTestRule;
import org.apache.iceberg.aliyun.oss.OSSURI;
import org.apache.iceberg.aliyun.oss.mock.AliyunOSSMockRule;
import org.apache.iceberg.common.DynConstructors;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：测试 TestUtility 的功能。
 *
 * <p>所属模块：iceberg-aliyun。职责：验证 TestUtility 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestUtility {
  private static final Logger LOG = LoggerFactory.getLogger(TestUtility.class);

  // System environment variables for Aliyun Access Key Pair.
  private static final String ALIYUN_TEST_ACCESS_KEY_ID = "ALIYUN_TEST_ACCESS_KEY_ID";
  private static final String ALIYUN_TEST_ACCESS_KEY_SECRET = "ALIYUN_TEST_ACCESS_KEY_SECRET";

  // System environment variables for Aliyun OSS
  private static final String ALIYUN_TEST_OSS_RULE_CLASS = "ALIYUN_TEST_OSS_TEST_RULE_CLASS";
  private static final String ALIYUN_TEST_OSS_ENDPOINT = "ALIYUN_TEST_OSS_ENDPOINT";
  private static final String ALIYUN_TEST_OSS_WAREHOUSE = "ALIYUN_TEST_OSS_WAREHOUSE";

  /** 辅助方法：TestUtility。 */
  private TestUtility() {}

  /** 辅助方法：initialize。 */
  public static AliyunOSSTestRule initialize() {
    AliyunOSSTestRule testRule;

    String implClass = System.getenv(ALIYUN_TEST_OSS_RULE_CLASS);
    if (!Strings.isNullOrEmpty(implClass)) {
      LOG.info("The initializing AliyunOSSTestRule implementation is: {}", implClass);
      try {
        DynConstructors.Ctor<AliyunOSSTestRule> ctor =
            DynConstructors.builder(AliyunOSSTestRule.class).impl(implClass).buildChecked();
        testRule = ctor.newInstance();
      } catch (NoSuchMethodException e) {
        throw new IllegalArgumentException(
            String.format(
                "Cannot initialize AliyunOSSTestRule, missing no-arg constructor: %s", implClass),
            e);
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(
            String.format(
                "Cannot initialize AliyunOSSTestRule, %s does not implement it.", implClass),
            e);
      }
    } else {
      LOG.info("Initializing AliyunOSSTestRule implementation with default AliyunOSSMockRule");
      testRule = AliyunOSSMockRule.builder().silent().build();
    }

    return testRule;
  }

  /** 辅助方法：accessKeyId。 */
  public static String accessKeyId() {
    return System.getenv(ALIYUN_TEST_ACCESS_KEY_ID);
  }

  /** 辅助方法：accessKeySecret。 */
  public static String accessKeySecret() {
    return System.getenv(ALIYUN_TEST_ACCESS_KEY_SECRET);
  }

  /** 辅助方法：ossEndpoint。 */
  public static String ossEndpoint() {
    return System.getenv(ALIYUN_TEST_OSS_ENDPOINT);
  }

  /** 辅助方法：ossWarehouse。 */
  public static String ossWarehouse() {
    return System.getenv(ALIYUN_TEST_OSS_WAREHOUSE);
  }

  /** 辅助方法：ossBucket。 */
  public static String ossBucket() {
    return ossWarehouseURI().bucket();
  }

  /** 辅助方法：ossKey。 */
  public static String ossKey() {
    return ossWarehouseURI().key();
  }

  /** 辅助方法：ossWarehouseURI。 */
  private static OSSURI ossWarehouseURI() {
    String ossWarehouse = ossWarehouse();
    Preconditions.checkNotNull(
        ossWarehouse,
        "Please set a correct Aliyun OSS path for environment variable '%s'",
        ALIYUN_TEST_OSS_WAREHOUSE);

    return new OSSURI(ossWarehouse);
  }
}
