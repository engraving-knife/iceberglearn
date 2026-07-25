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
package org.apache.iceberg.aws.s3.signer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import org.apache.iceberg.rest.RESTSerializers.ErrorResponseDeserializer;
import org.apache.iceberg.rest.RESTSerializers.ErrorResponseSerializer;
import org.apache.iceberg.rest.RESTSerializers.OAuthTokenResponseDeserializer;
import org.apache.iceberg.rest.RESTSerializers.OAuthTokenResponseSerializer;
import org.apache.iceberg.rest.responses.ErrorResponse;
import org.apache.iceberg.rest.responses.OAuthTokenResponse;

/**
 * 文件级说明：S3 签名场景专用的 Jackson ObjectMapper 持有者。
 *
 * <p>所属模块：iceberg-aws（Iceberg 与 AWS 服务集成的入口模块，位于 api/core 之上）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>提供懒加载、线程安全的单例 {@link ObjectMapper}，供 S3 签名请求/响应 JSON 序列化使用。
 *   <li>注册 S3SignRequest/S3SignResponse 以及 Iceberg REST 通用 ErrorResponse/OAuthTokenResponse 的自定义
 *       Jackson 序列化器与反序列化器。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>双重检查锁懒加载：避免类加载期就构建 ObjectMapper，减少冷启动开销； volatile isInitialized 保证多线程可见性。
 *   <li>独立 ObjectMapper：与 Iceberg REST 主 ObjectMapper 隔离，避免 S3 签名专用配置 （字段可见性、kebab-case
 *       命名、忽略未知字段）影响其他序列化场景。
 *   <li>Jackson 版本兼容：使用已 deprecated 的 PropertyNamingStrategy.KebabCaseStrategy， 而非 2.14 引入的
 *       PropertyNamingStrategies.KebabCaseStrategy.INSTANCE， 因为 Spark 仍依赖 Jackson 2.13.x。
 * </ul>
 *
 * <p>上下游关系：被 {@link S3SignRequestParser}、{@link S3SignResponseParser} 间接使用； 注册的序列化器/反序列化器分别代理到对应
 * Parser 的 toJson/fromJson。
 */
public class S3ObjectMapper {

  private static final JsonFactory FACTORY = new JsonFactory();
  private static final ObjectMapper MAPPER = new ObjectMapper(FACTORY);
  private static volatile boolean isInitialized = false;

  private S3ObjectMapper() {}

  /**
   * 返回已初始化的 ObjectMapper 单例，首次调用时双重检查锁完成配置。
   *
   * <p>初始化逻辑：设置字段可见性为 ANY、忽略未知字段、命名策略为 kebab-case， 并注册自定义 SimpleModule。
   *
   * @return 已配置的 ObjectMapper
   */
  static ObjectMapper mapper() {
    if (!isInitialized) {
      synchronized (S3ObjectMapper.class) {
        if (!isInitialized) {
          MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
          MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
          // even though using new PropertyNamingStrategy.KebabCaseStrategy() is deprecated
          // and PropertyNamingStrategies.KebabCaseStrategy.INSTANCE (introduced in jackson 2.14) is
          // recommended, we can't use it because Spark still relies on jackson 2.13.x stuff
          MAPPER.setPropertyNamingStrategy(new PropertyNamingStrategy.KebabCaseStrategy());
          MAPPER.registerModule(initModule());
          isInitialized = true;
        }
      }
    }

    return MAPPER;
  }

  /**
   * 构造 SimpleModule，注册 ErrorResponse、OAuthTokenResponse、S3SignRequest、S3SignResponse
   * 的自定义序列化器与反序列化器。
   *
   * @return 已注册序列化器的 SimpleModule
   */
  public static SimpleModule initModule() {
    return new SimpleModule()
        .addSerializer(ErrorResponse.class, new ErrorResponseSerializer())
        .addDeserializer(ErrorResponse.class, new ErrorResponseDeserializer())
        .addSerializer(OAuthTokenResponse.class, new OAuthTokenResponseSerializer())
        .addDeserializer(OAuthTokenResponse.class, new OAuthTokenResponseDeserializer())
        .addSerializer(S3SignRequest.class, new S3SignRequestSerializer<>())
        .addSerializer(ImmutableS3SignRequest.class, new S3SignRequestSerializer<>())
        .addDeserializer(S3SignRequest.class, new S3SignRequestDeserializer<>())
        .addDeserializer(ImmutableS3SignRequest.class, new S3SignRequestDeserializer<>())
        .addSerializer(S3SignResponse.class, new S3SignResponseSerializer<>())
        .addSerializer(ImmutableS3SignResponse.class, new S3SignResponseSerializer<>())
        .addDeserializer(S3SignResponse.class, new S3SignResponseDeserializer<>())
        .addDeserializer(ImmutableS3SignResponse.class, new S3SignResponseDeserializer<>());
  }

  /** S3SignRequest 的 Jackson 序列化器，委托 {@link S3SignRequestParser#toJson}。 */
  public static class S3SignRequestSerializer<T extends S3SignRequest> extends JsonSerializer<T> {
    @Override
    public void serialize(T request, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      S3SignRequestParser.toJson(request, gen);
    }
  }

  /** S3SignRequest 的 Jackson 反序列化器，委托 {@link S3SignRequestParser#fromJson}。 */
  public static class S3SignRequestDeserializer<T extends S3SignRequest>
      extends JsonDeserializer<T> {
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return (T) S3SignRequestParser.fromJson(jsonNode);
    }
  }

  /** S3SignResponse 的 Jackson 序列化器，委托 {@link S3SignResponseParser#toJson}。 */
  public static class S3SignResponseSerializer<T extends S3SignResponse> extends JsonSerializer<T> {
    @Override
    public void serialize(T request, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      S3SignResponseParser.toJson(request, gen);
    }
  }

  /** S3SignResponse 的 Jackson 反序列化器，委托 {@link S3SignResponseParser#fromJson}。 */
  public static class S3SignResponseDeserializer<T extends S3SignResponse>
      extends JsonDeserializer<T> {
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return (T) S3SignResponseParser.fromJson(jsonNode);
    }
  }
}
