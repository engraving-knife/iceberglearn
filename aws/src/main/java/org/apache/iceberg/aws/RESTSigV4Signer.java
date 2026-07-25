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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.iceberg.exceptions.RESTException;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.internal.SignerConstant;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.auth.signer.params.SignerChecksumParams;
import software.amazon.awssdk.core.checksums.Algorithm;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

/**
 * 文件级说明：用于 Apache HttpClient 的 SigV4 请求签名拦截器。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>实现 {@link HttpRequestInterceptor}，在 HTTP 请求发出前为其计算 AWS SigV4 签名， 并注入
 *       Authorization、x-amz-date、x-amz-content-sha256 等必要头。
 *   <li>用于 REST Catalog 客户端在通过 SigV4 网关（如 API Gateway / Lambda 代理）访问 Iceberg REST 服务时，使请求被 AWS IAM
 *       接受。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>复用 AWS SDK 的 {@link Aws4Signer} 真正计算签名，本类仅负责将 Apache HttpRequest 适配为 SdkHttpFullRequest
 *       并回写签名后的头，避免重复实现 SigV4 易出错的部分。
 *   <li>对空请求体的特殊处理：直接固定 x-amz-content-sha256 为空串的 SHA256， 规避签名器对空 body 计算出无效 checksum 的实现问题。
 *   <li>对原始 Authorization 头与签名后冲突的头进行“重命名保留”（前缀 Original-）， 便于排查或后续网关转发，避免直接覆盖丢失上下文。
 * </ul>
 *
 * <p>上下游关系：由 REST Catalog HTTP 客户端构建时挂载到 Apache HttpClient 拦截器链； 依赖 {@link AwsProperties}
 * 提供签名区域、签名服务名、凭证提供者；产出的签名请求发送给 部署在 AWS 上的 REST Catalog 服务端。
 *
 * <p>参见 <a
 * href="https://docs.aws.amazon.com/general/latest/gr/signing-aws-api-requests.html">Signing AWS
 * API requests</a> 了解 SigV4 协议细节。
 */
public class RESTSigV4Signer implements HttpRequestInterceptor {
  /** 空请求体的 SHA256 哈希值（十六进制），用于固定 x-amz-content-sha256 头。 */
  static final String EMPTY_BODY_SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  /** 被“重命名保留”的原始 HTTP 头前缀，用于在签名覆盖前保留原有头。 */
  static final String RELOCATED_HEADER_PREFIX = "Original-";

  private final Aws4Signer signer = Aws4Signer.create();
  private AwsCredentialsProvider credentialsProvider;

  private String signingName;
  private Region signingRegion;

  /**
   * 从 catalog 配置初始化签名所需的区域、服务名与凭证提供者。
   *
   * @param properties catalog 配置键值
   */
  public void initialize(Map<String, String> properties) {
    AwsProperties awsProperties = new AwsProperties(properties);

    this.signingRegion = awsProperties.restSigningRegion();
    this.signingName = awsProperties.restSigningName();
    this.credentialsProvider = awsProperties.restCredentialsProvider();
  }

  /**
   * 拦截 HTTP 请求，计算 SigV4 签名并把签名结果回写到请求头。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>解析请求 URI，构造 {@link Aws4SignerParams}（区域、服务名、凭证、SHA256 checksum 参数）。
   *   <li>把 Apache HttpRequest 转换为 {@link SdkHttpFullRequest}，按实体类型分流：
   *       <ul>
   *         <li>空 body：直接设置固定的 x-amz-content-sha256，绕过签名器对空 body 的实现问题。
   *         <li>StringEntity：以内容流形式交给签名器计算。
   *         <li>其他类型：抛 UnsupportedOperationException。
   *       </ul>
   *   <li>调用 {@link Aws4Signer#sign} 完成签名，将签名后的头回写到原始 HttpRequest。
   * </ol>
   *
   * @param request 待签名的 HTTP 请求
   * @param entity 请求实体（可为 null）
   * @param context HTTP 上下文（本实现未使用）
   * @throws RESTException 当请求 URI 非法时
   * @throws UnsupportedOperationException 当实体类型非 StringEntity 且非空时
   */
  @Override
  public void process(HttpRequest request, EntityDetails entity, HttpContext context) {
    URI requestUri;

    try {
      requestUri = request.getUri();
    } catch (URISyntaxException e) {
      throw new RESTException(e, "Invalid uri for request: %s", request);
    }

    Aws4SignerParams params =
        Aws4SignerParams.builder()
            .signingName(signingName)
            .signingRegion(signingRegion)
            .awsCredentials(credentialsProvider.resolveCredentials())
            .checksumParams(
                SignerChecksumParams.builder()
                    .algorithm(Algorithm.SHA256)
                    .isStreamingRequest(false)
                    .checksumHeaderName(SignerConstant.X_AMZ_CONTENT_SHA256)
                    .build())
            .build();

    SdkHttpFullRequest.Builder sdkRequestBuilder = SdkHttpFullRequest.builder();

    sdkRequestBuilder
        .method(SdkHttpMethod.fromValue(request.getMethod()))
        .protocol(request.getScheme())
        .uri(requestUri)
        .headers(convertHeaders(request.getHeaders()));

    if (entity == null) {
      // This is a workaround for the signer implementation incorrectly producing
      // an invalid content checksum for empty body requests.
      sdkRequestBuilder.putHeader(SignerConstant.X_AMZ_CONTENT_SHA256, EMPTY_BODY_SHA256);
    } else if (entity instanceof StringEntity) {
      sdkRequestBuilder.contentStreamProvider(
          () -> {
            try {
              return ((StringEntity) entity).getContent();
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          });
    } else {
      throw new UnsupportedOperationException("Unsupported entity type: " + entity.getClass());
    }

    SdkHttpFullRequest signedSdkRequest = signer.sign(sdkRequestBuilder.build(), params);
    updateRequestHeaders(request, signedSdkRequest.headers());
  }

  /**
   * 将 Apache Header 数组转换为 SDK 需要的多值头 Map。
   *
   * <p>关键点：原有 Authorization 头会被改名为 Original-Authorization，让 SigV4 签名头接管， 避免双重鉴权头冲突。
   *
   * @param headers Apache Header 数组
   * @return 头名到值列表的映射
   */
  private Map<String, List<String>> convertHeaders(Header[] headers) {
    return Arrays.stream(headers)
        .collect(
            Collectors.groupingBy(
                // Relocate Authorization header as SigV4 takes precedence
                header ->
                    HttpHeaders.AUTHORIZATION.equals(header.getName())
                        ? RELOCATED_HEADER_PREFIX + header.getName()
                        : header.getName(),
                Collectors.mapping(Header::getValue, Collectors.toList())));
  }

  /**
   * 用签名后的头更新原始 HttpRequest，处理与签名头冲突的原有头。
   *
   * <p>逻辑：遍历每个签名头，若原始请求已存在同名头，先把不与签名值重复的原值改名为 Original-&lt;name&gt; 保留，再用签名值 setHeader 覆盖。
   *
   * @param request 原始 HTTP 请求
   * @param headers 签名后的头映射
   */
  private void updateRequestHeaders(HttpRequest request, Map<String, List<String>> headers) {
    headers.forEach(
        (name, values) -> {
          if (request.containsHeader(name)) {
            Header[] original = request.getHeaders(name);
            request.removeHeaders(name);
            Arrays.asList(original)
                .forEach(
                    header -> {
                      // Relocate headers if there is a conflict with signed headers
                      if (!values.contains(header.getValue())) {
                        request.addHeader(RELOCATED_HEADER_PREFIX + name, header.getValue());
                      }
                    });
          }

          values.forEach(value -> request.setHeader(name, value));
        });
  }
}
