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

import org.apache.iceberg.metrics.MetricsContext;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

abstract class BaseS3File {
  private final S3Client client;
  private final S3URI uri;
  private final S3FileIOProperties s3FileIOProperties;
  private HeadObjectResponse metadata;
  private final MetricsContext metrics;

  BaseS3File(
      S3Client client, S3URI uri, S3FileIOProperties s3FileIOProperties, MetricsContext metrics) {
    this.client = client;
    this.uri = uri;
    this.s3FileIOProperties = s3FileIOProperties;
    this.metrics = metrics;
  }

  public String location() {
    return uri.location();
  }

  S3Client client() {
    return client;
  }

  S3URI uri() {
    return uri;
  }

  public S3FileIOProperties s3FileIOProperties() {
    return s3FileIOProperties;
  }

  protected MetricsContext metrics() {
    return metrics;
  }

  /**
   * 检查 S3 对象是否存在。
   *
   * <p>注意：由于元数据会被缓存，若文件在缓存后被删除，此方法可能返回过时的结果。
   *
   * @return true 表示对象存在
   */
  public boolean exists() {
    try {
      return getObjectMetadata() != null;
    } catch (S3Exception e) {
      if (e.statusCode() == HttpStatusCode.NOT_FOUND) {
        return false;
      } else {
        throw e; // return null if 404 Not Found, otherwise rethrow
      }
    }
  }

  /**
   * 获取 S3 对象元数据（HeadObject），惰性缓存。
   *
   * <p>逻辑：首次调用时通过 HeadObject 请求获取元数据并缓存，后续直接返回缓存值。 请求时会通过 S3RequestUtil 配置服务端加密参数。
   *
   * @return S3 对象元数据
   * @throws S3Exception S3 请求失败
   */
  protected HeadObjectResponse getObjectMetadata() throws S3Exception {
    if (metadata == null) {
      HeadObjectRequest.Builder requestBuilder =
          HeadObjectRequest.builder().bucket(uri().bucket()).key(uri().key());
      S3RequestUtil.configureEncryption(s3FileIOProperties, requestBuilder);
      metadata = client().headObject(requestBuilder.build());
    }

    return metadata;
  }

  @Override
  public String toString() {
    return uri.toString();
  }
}
