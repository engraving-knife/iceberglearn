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
package org.apache.iceberg.aliyun.oss.mock;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import org.apache.iceberg.aliyun.oss.AliyunOSSTestRule;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.base.Strings;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：测试 AliyunOSSMockRule 的功能。
 *
 * <p>所属模块：iceberg-aliyun。职责：验证 AliyunOSSMockRule 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class AliyunOSSMockRule implements AliyunOSSTestRule {

  private final Map<String, Object> properties;

  private AliyunOSSMockApp ossMockApp;

  /** 辅助方法：AliyunOSSMockRule。 */
  private AliyunOSSMockRule(Map<String, Object> properties) {
    this.properties = properties;
  }

  /** 辅助方法：builder。 */
  public static Builder builder() {
    return new Builder();
  }

  /** 辅助方法：keyPrefix。 */
  @Override
  public String keyPrefix() {
    return "mock-objects/";
  }

  /** 辅助方法：start。 */
  @Override
  public void start() {
    ossMockApp = AliyunOSSMockApp.start(properties);
  }

  /** 辅助方法：stop。 */
  @Override
  public void stop() {
    ossMockApp.stop();
  }

  /** 辅助方法：createOSSClient。 */
  @Override
  public OSS createOSSClient() {
    String endpoint =
        String.format(
            "http://localhost:%s",
            properties.getOrDefault(
                AliyunOSSMockApp.PROP_HTTP_PORT, AliyunOSSMockApp.PORT_HTTP_PORT_DEFAULT));
    return new OSSClientBuilder().build(endpoint, "foo", "bar");
  }

  /** 辅助方法：rootDir。 */
  private File rootDir() {
    Object rootDir = properties.get(AliyunOSSMockApp.PROP_ROOT_DIR);
    Preconditions.checkNotNull(rootDir, "Root directory cannot be null");
    return new File(rootDir.toString());
  }

  /** 辅助方法：setUpBucket。 */
  @Override
  public void setUpBucket(String bucket) {
    createOSSClient().createBucket(bucket);
  }

  /** 辅助方法：tearDownBucket。 */
  @Override
  public void tearDownBucket(String bucket) {
    try {
      Files.walk(rootDir().toPath())
          .filter(p -> p.toFile().isFile())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                  // delete this file quietly.
                }
              });

      createOSSClient().deleteBucket(bucket);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static class Builder {
    private Map<String, Object> props = Maps.newHashMap();

    /** 辅助方法：silent。 */
    public Builder silent() {
      props.put(AliyunOSSMockApp.PROP_SILENT, true);
      return this;
    }

    /** 辅助方法：build。 */
    public AliyunOSSTestRule build() {
      String rootDir = (String) props.get(AliyunOSSMockApp.PROP_ROOT_DIR);
      if (Strings.isNullOrEmpty(rootDir)) {
        File dir =
            new File(
                System.getProperty("java.io.tmpdir"),
                "oss-mock-file-store-" + System.currentTimeMillis());
        rootDir = dir.getAbsolutePath();
        props.put(AliyunOSSMockApp.PROP_ROOT_DIR, rootDir);
      }
      File root = new File(rootDir);
      root.deleteOnExit();
      root.mkdir();
      return new AliyunOSSMockRule(props);
    }
  }
}
