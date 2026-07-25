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

import com.aliyun.oss.OSS;
import java.util.Map;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;
import org.junit.Assert;
import org.junit.Test;

/**
 * 文件级说明：测试 TestAliyunClientFactories 的功能。
 *
 * <p>所属模块：iceberg-aliyun。职责：验证 TestAliyunClientFactories 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class TestAliyunClientFactories {

  /**
   * 测试场景：Load Default。
   *
   * <p>验证该方法在 Load Default 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadDefault() {
    Assert.assertEquals(
        "Default client should be singleton",
        AliyunClientFactories.defaultFactory(),
        AliyunClientFactories.defaultFactory());

    AliyunClientFactory defaultFactory = AliyunClientFactories.from(Maps.newHashMap());
    Assert.assertTrue(
        "Should load default when factory impl not configured",
        defaultFactory instanceof AliyunClientFactories.DefaultAliyunClientFactory);
    Assert.assertNull(
        "Should have no Aliyun properties set", defaultFactory.aliyunProperties().accessKeyId());

    AliyunClientFactory defaultFactoryWithConfig =
        AliyunClientFactories.from(ImmutableMap.of(AliyunProperties.CLIENT_ACCESS_KEY_ID, "key"));
    Assert.assertTrue(
        "Should load default when factory impl not configured",
        defaultFactoryWithConfig instanceof AliyunClientFactories.DefaultAliyunClientFactory);
    Assert.assertEquals(
        "Should have access key set",
        "key",
        defaultFactoryWithConfig.aliyunProperties().accessKeyId());
  }

  /**
   * 测试场景：Load Custom。
   *
   * <p>验证该方法在 Load Custom 条件下的行为是否符合预期。
   */
  @Test
  public void testLoadCustom() {
    Map<String, String> properties = Maps.newHashMap();
    properties.put(AliyunProperties.CLIENT_FACTORY, CustomFactory.class.getName());
    Assert.assertTrue(
        "Should load custom class",
        AliyunClientFactories.from(properties) instanceof CustomFactory);
  }

  public static class CustomFactory implements AliyunClientFactory {

    AliyunProperties aliyunProperties;

    /** 辅助方法：CustomFactory。 */
    public CustomFactory() {}

    /** 辅助方法：newOSSClient。 */
    @Override
    public OSS newOSSClient() {
      return null;
    }

    /** 辅助方法：initialize。 */
    @Override
    public void initialize(Map<String, String> properties) {
      this.aliyunProperties = new AliyunProperties(properties);
    }

    /** 辅助方法：aliyunProperties。 */
    @Override
    public AliyunProperties aliyunProperties() {
      return aliyunProperties;
    }
  }
}
