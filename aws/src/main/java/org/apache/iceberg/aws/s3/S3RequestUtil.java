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

import java.util.Locale;
import java.util.function.Function;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Request;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

/**
 * 文件级说明：S3 请求构建工具类，集中处理 SSE 加密与 ACL 权限注入。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>为不同 S3 请求类型（PutObject、CreateMultipartUpload、UploadPart、GetObject、HeadObject）
 *       统一注入服务端加密（SSE-KMS / SSE-S3 / SSE-C）相关字段。
 *   <li>为写请求统一注入对象 ACL（Canned ACL）权限。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>不同 S3 请求 builder 暴露的 SSE setter 不同：PutObject/CreateMultipartUpload 同时支持 SSE-S3/KMS/Custom，而
 *       UploadPart/GetObject/HeadObject 仅支持 SSE-C。本类通过 {@code Function} 回调抽象 setter，对不支持的字段传入 NULL
 *       setter 实现统一分支。
 *   <li>工具类模式，私有构造器 + 静态方法，无状态。
 * </ul>
 *
 * <p>上下游关系：被 {@link S3OutputStream}、{@link S3InputStream} 在构建具体 S3 请求时调用； 读取 {@link
 * S3FileIOProperties} 中的 SSE 类型、KMS key、ACL 等配置。
 */
@SuppressWarnings("UnnecessaryLambda")
public class S3RequestUtil {

  /** 始终返回 null 的 SSE setter 占位，用于不支持 SSE-S3/KMS 的请求类型。 */
  private static final Function<ServerSideEncryption, S3Request.Builder> NULL_SSE_SETTER =
      sse -> null;
  /** 始终返回 null 的字符串 setter 占位，用于不支持 KMS key 的请求类型。 */
  private static final Function<String, S3Request.Builder> NULL_STRING_SETTER = s -> null;

  private S3RequestUtil() {}

  /** 为 PutObject 请求 builder 注入 SSE 加密配置。 */
  static void configureEncryption(
      S3FileIOProperties s3FileIOProperties, PutObjectRequest.Builder requestBuilder) {
    configureEncryption(
        s3FileIOProperties,
        requestBuilder::serverSideEncryption,
        requestBuilder::ssekmsKeyId,
        requestBuilder::sseCustomerAlgorithm,
        requestBuilder::sseCustomerKey,
        requestBuilder::sseCustomerKeyMD5);
  }

  /** 为 CreateMultipartUpload 请求 builder 注入 SSE 加密配置。 */
  static void configureEncryption(
      S3FileIOProperties s3FileIOProperties, CreateMultipartUploadRequest.Builder requestBuilder) {
    configureEncryption(
        s3FileIOProperties,
        requestBuilder::serverSideEncryption,
        requestBuilder::ssekmsKeyId,
        requestBuilder::sseCustomerAlgorithm,
        requestBuilder::sseCustomerKey,
        requestBuilder::sseCustomerKeyMD5);
  }

  /** 为 UploadPart 请求 builder 注入 SSE-C 加密配置（仅支持 SSE-C）。 */
  static void configureEncryption(
      S3FileIOProperties s3FileIOProperties, UploadPartRequest.Builder requestBuilder) {
    configureEncryption(
        s3FileIOProperties,
        NULL_SSE_SETTER,
        NULL_STRING_SETTER,
        requestBuilder::sseCustomerAlgorithm,
        requestBuilder::sseCustomerKey,
        requestBuilder::sseCustomerKeyMD5);
  }

  /** 为 GetObject 请求 builder 注入 SSE-C 加密配置（仅支持 SSE-C 解密）。 */
  static void configureEncryption(
      S3FileIOProperties s3FileIOProperties, GetObjectRequest.Builder requestBuilder) {
    configureEncryption(
        s3FileIOProperties,
        NULL_SSE_SETTER,
        NULL_STRING_SETTER,
        requestBuilder::sseCustomerAlgorithm,
        requestBuilder::sseCustomerKey,
        requestBuilder::sseCustomerKeyMD5);
  }

  /** 为 HeadObject 请求 builder 注入 SSE-C 加密配置（仅支持 SSE-C）。 */
  static void configureEncryption(
      S3FileIOProperties s3FileIOProperties, HeadObjectRequest.Builder requestBuilder) {
    configureEncryption(
        s3FileIOProperties,
        NULL_SSE_SETTER,
        NULL_STRING_SETTER,
        requestBuilder::sseCustomerAlgorithm,
        requestBuilder::sseCustomerKey,
        requestBuilder::sseCustomerKeyMD5);
  }

  /**
   * 通用 SSE 注入实现，按 SSE 类型分发：NONE 不处理；KMS 设置 AWS_KMS 与 KMS key； S3 设置 AES256；CUSTOM 设置 SSE-C 算法、客户密钥与
   * MD5。
   *
   * @param s3FileIOProperties S3 FileIO 配置
   * @param encryptionSetter SSE 类型 setter，不支持时传 NULL_SSE_SETTER
   * @param kmsKeySetter KMS key setter，不支持时传 NULL_STRING_SETTER
   * @param customAlgorithmSetter SSE-C 算法 setter
   * @param customKeySetter SSE-C 客户密钥 setter
   * @param customMd5Setter SSE-C MD5 setter
   */
  @SuppressWarnings("ReturnValueIgnored")
  static void configureEncryption(
      S3FileIOProperties s3FileIOProperties,
      Function<ServerSideEncryption, S3Request.Builder> encryptionSetter,
      Function<String, S3Request.Builder> kmsKeySetter,
      Function<String, S3Request.Builder> customAlgorithmSetter,
      Function<String, S3Request.Builder> customKeySetter,
      Function<String, S3Request.Builder> customMd5Setter) {

    switch (s3FileIOProperties.sseType().toLowerCase(Locale.ENGLISH)) {
      case S3FileIOProperties.SSE_TYPE_NONE:
        break;

      case S3FileIOProperties.SSE_TYPE_KMS:
        encryptionSetter.apply(ServerSideEncryption.AWS_KMS);
        kmsKeySetter.apply(s3FileIOProperties.sseKey());
        break;

      case S3FileIOProperties.SSE_TYPE_S3:
        encryptionSetter.apply(ServerSideEncryption.AES256);
        break;

      case S3FileIOProperties.SSE_TYPE_CUSTOM:
        // setters for SSE-C exist for all request builders, no need to check null
        customAlgorithmSetter.apply(ServerSideEncryption.AES256.name());
        customKeySetter.apply(s3FileIOProperties.sseKey());
        customMd5Setter.apply(s3FileIOProperties.sseMd5());
        break;

      default:
        throw new IllegalArgumentException(
            "Cannot support given S3 encryption type: " + s3FileIOProperties.sseType());
    }
  }

  /** 为 PutObject 请求 builder 注入对象 ACL 权限。 */
  static void configurePermission(
      S3FileIOProperties s3FileIOProperties, PutObjectRequest.Builder requestBuilder) {
    configurePermission(s3FileIOProperties, requestBuilder::acl);
  }

  /** 为 CreateMultipartUpload 请求 builder 注入对象 ACL 权限。 */
  static void configurePermission(
      S3FileIOProperties s3FileIOProperties, CreateMultipartUploadRequest.Builder requestBuilder) {
    configurePermission(s3FileIOProperties, requestBuilder::acl);
  }

  /**
   * 通用 ACL 注入实现，调用 setter 设置配置的 Canned ACL。
   *
   * @param s3FileIOProperties S3 FileIO 配置
   * @param aclSetter ACL setter 回调
   */
  @SuppressWarnings("ReturnValueIgnored")
  static void configurePermission(
      S3FileIOProperties s3FileIOProperties,
      Function<ObjectCannedACL, S3Request.Builder> aclSetter) {
    aclSetter.apply(s3FileIOProperties.acl());
  }
}
