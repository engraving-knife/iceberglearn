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

/**
 * 文件级说明：S3TestUtil 集成测试。
 *
 * <p>所属模块：iceberg-aws。职责：验证 s3测试 相关功能，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 JUnit 框架，在真实集成环境（如云存储、元数据服务、计算引擎集群）下验证端到端行为。 运行前需配置相应的环境变量、凭证与测试资源。
 */
public class S3TestUtil {

  /** 构造方法：S3TestUtil。 */
  private S3TestUtil() {}

  /** 辅助方法：获取桶从URI。 */
  public static String getBucketFromUri(String s3Uri) {
    return new S3URI(s3Uri).bucket();
  }

  /** 辅助方法：获取key从URI。 */
  public static String getKeyFromUri(String s3Uri) {
    return new S3URI(s3Uri).key();
  }
}
