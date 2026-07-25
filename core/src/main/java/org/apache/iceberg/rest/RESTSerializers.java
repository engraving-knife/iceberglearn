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
package org.apache.iceberg.rest;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import org.apache.iceberg.MetadataUpdate;
import org.apache.iceberg.MetadataUpdateParser;
import org.apache.iceberg.PartitionSpecParser;
import org.apache.iceberg.Schema;
import org.apache.iceberg.SchemaParser;
import org.apache.iceberg.SortOrderParser;
import org.apache.iceberg.TableMetadata;
import org.apache.iceberg.TableMetadataParser;
import org.apache.iceberg.UnboundPartitionSpec;
import org.apache.iceberg.UnboundSortOrder;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.catalog.TableIdentifierParser;
import org.apache.iceberg.rest.auth.OAuth2Util;
import org.apache.iceberg.rest.requests.CommitTransactionRequest;
import org.apache.iceberg.rest.requests.CommitTransactionRequestParser;
import org.apache.iceberg.rest.requests.ImmutableRegisterTableRequest;
import org.apache.iceberg.rest.requests.ImmutableReportMetricsRequest;
import org.apache.iceberg.rest.requests.RegisterTableRequest;
import org.apache.iceberg.rest.requests.RegisterTableRequestParser;
import org.apache.iceberg.rest.requests.ReportMetricsRequest;
import org.apache.iceberg.rest.requests.ReportMetricsRequestParser;
import org.apache.iceberg.rest.requests.UpdateRequirementParser;
import org.apache.iceberg.rest.requests.UpdateTableRequest;
import org.apache.iceberg.rest.requests.UpdateTableRequest.UpdateRequirement;
import org.apache.iceberg.rest.requests.UpdateTableRequestParser;
import org.apache.iceberg.rest.responses.ErrorResponse;
import org.apache.iceberg.rest.responses.ErrorResponseParser;
import org.apache.iceberg.rest.responses.OAuthTokenResponse;
import org.apache.iceberg.util.JsonUtil;

/**
 * 文件级说明：REST Catalog 自定义 Jackson 序列化器/反序列化器集合。
 *
 * <p>所属模块：iceberg-core（REST Catalog 序列化基础设施）。
 *
 * <p>职责：为 Iceberg 核心类型（{@link TableMetadata}、{@link MetadataUpdate}、 {@link
 * UpdateRequirement}、{@link ErrorResponse}、{@link Namespace}、{@link TableIdentifier}） 提供 JSON
 * 序列化与反序列化支持，并通过 {@link #registerAll} 统一注册到 ObjectMapper。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>这些核心类型没有直接的 Jackson 注解，需要自定义序列化器来控制 JSON 格式。
 *   <li>每个类型提供 Serializer 和 Deserializer 一对，分别继承 JsonSerializer/JsonDeserializer。
 *   <li>UpdateRequirement 的旧版（已废弃）和新版（{@link org.apache.iceberg.UpdateRequirement}） 分别注册，用于向后兼容。
 * </ul>
 *
 * <p>上下游关系：由 {@link RESTObjectMapper#mapper()} 调用 {@link #registerAll} 注册。
 */
public class RESTSerializers {

  /** 私有构造函数，禁止实例化工具类。 */
  private RESTSerializers() {}

  /**
   * 将所有自定义序列化器/反序列化器注册到指定的 ObjectMapper。
   *
   * @param mapper 待注册的 ObjectMapper
   */
  public static void registerAll(ObjectMapper mapper) {
    SimpleModule module = new SimpleModule();
    module
        .addSerializer(ErrorResponse.class, new ErrorResponseSerializer())
        .addDeserializer(ErrorResponse.class, new ErrorResponseDeserializer())
        .addSerializer(TableIdentifier.class, new TableIdentifierSerializer())
        .addDeserializer(TableIdentifier.class, new TableIdentifierDeserializer())
        .addSerializer(Namespace.class, new NamespaceSerializer())
        .addDeserializer(Namespace.class, new NamespaceDeserializer())
        .addSerializer(Schema.class, new SchemaSerializer())
        .addDeserializer(Schema.class, new SchemaDeserializer())
        .addSerializer(UnboundPartitionSpec.class, new UnboundPartitionSpecSerializer())
        .addDeserializer(UnboundPartitionSpec.class, new UnboundPartitionSpecDeserializer())
        .addSerializer(UnboundSortOrder.class, new UnboundSortOrderSerializer())
        .addDeserializer(UnboundSortOrder.class, new UnboundSortOrderDeserializer())
        .addSerializer(MetadataUpdate.class, new MetadataUpdateSerializer())
        .addDeserializer(MetadataUpdate.class, new MetadataUpdateDeserializer())
        .addSerializer(TableMetadata.class, new TableMetadataSerializer())
        .addDeserializer(TableMetadata.class, new TableMetadataDeserializer())
        .addSerializer(UpdateRequirement.class, new UpdateRequirementSerializer())
        .addDeserializer(UpdateRequirement.class, new UpdateRequirementDeserializer())
        .addSerializer(org.apache.iceberg.UpdateRequirement.class, new UpdateReqSerializer())
        .addDeserializer(org.apache.iceberg.UpdateRequirement.class, new UpdateReqDeserializer())
        .addSerializer(OAuthTokenResponse.class, new OAuthTokenResponseSerializer())
        .addDeserializer(OAuthTokenResponse.class, new OAuthTokenResponseDeserializer())
        .addSerializer(ReportMetricsRequest.class, new ReportMetricsRequestSerializer<>())
        .addDeserializer(ReportMetricsRequest.class, new ReportMetricsRequestDeserializer<>())
        .addSerializer(ImmutableReportMetricsRequest.class, new ReportMetricsRequestSerializer<>())
        .addDeserializer(
            ImmutableReportMetricsRequest.class, new ReportMetricsRequestDeserializer<>())
        .addSerializer(CommitTransactionRequest.class, new CommitTransactionRequestSerializer())
        .addDeserializer(CommitTransactionRequest.class, new CommitTransactionRequestDeserializer())
        .addSerializer(UpdateTableRequest.class, new UpdateTableRequestSerializer())
        .addDeserializer(UpdateTableRequest.class, new UpdateTableRequestDeserializer())
        .addSerializer(RegisterTableRequest.class, new RegisterTableRequestSerializer<>())
        .addDeserializer(RegisterTableRequest.class, new RegisterTableRequestDeserializer<>())
        .addSerializer(ImmutableRegisterTableRequest.class, new RegisterTableRequestSerializer<>())
        .addDeserializer(
            ImmutableRegisterTableRequest.class, new RegisterTableRequestDeserializer<>());
    mapper.registerModule(module);
  }

  /**
   * 旧版 {@link UpdateRequirement} 的反序列化器（已废弃）。
   *
   * <p>通过 {@link UpdateRequirementParser#fromJson} 解析 JSON 节点。
   *
   * @deprecated 将在 1.5.0 移除，请使用 {@link UpdateReqDeserializer}。
   */
  @Deprecated
  public static class UpdateRequirementDeserializer extends JsonDeserializer<UpdateRequirement> {
    @Override
    public UpdateRequirement deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = p.getCodec().readTree(p);
      return UpdateRequirementParser.fromJson(node);
    }
  }

  /**
   * 旧版 {@link UpdateRequirement} 的序列化器（已废弃）。
   *
   * <p>通过 {@link UpdateRequirementParser#toJson} 写出 JSON。
   *
   * @deprecated 将在 1.5.0 移除，请使用 {@link UpdateReqSerializer}。
   */
  @Deprecated
  public static class UpdateRequirementSerializer extends JsonSerializer<UpdateRequirement> {
    @Override
    public void serialize(
        UpdateRequirement value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      UpdateRequirementParser.toJson(value, gen);
    }
  }

  /**
   * 新版 {@link org.apache.iceberg.UpdateRequirement} 的反序列化器。
   *
   * <p>读取 JSON 树并委托 {@link org.apache.iceberg.UpdateRequirementParser#fromJson} 解析。
   */
  static class UpdateReqDeserializer
      extends JsonDeserializer<org.apache.iceberg.UpdateRequirement> {
    @Override
    public org.apache.iceberg.UpdateRequirement deserialize(
        JsonParser p, DeserializationContext ctxt) throws IOException {
      JsonNode node = p.getCodec().readTree(p);
      return org.apache.iceberg.UpdateRequirementParser.fromJson(node);
    }
  }

  /**
   * 新版 {@link org.apache.iceberg.UpdateRequirement} 的序列化器。
   *
   * <p>委托 {@link org.apache.iceberg.UpdateRequirementParser#toJson} 写出 JSON。
   */
  static class UpdateReqSerializer extends JsonSerializer<org.apache.iceberg.UpdateRequirement> {
    @Override
    public void serialize(
        org.apache.iceberg.UpdateRequirement value,
        JsonGenerator gen,
        SerializerProvider serializers)
        throws IOException {
      org.apache.iceberg.UpdateRequirementParser.toJson(value, gen);
    }
  }

  /** {@link TableMetadata} 的反序列化器，委托 {@link TableMetadataParser#fromJson} 解析 JSON 节点。 */
  public static class TableMetadataDeserializer extends JsonDeserializer<TableMetadata> {
    @Override
    public TableMetadata deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      JsonNode node = p.getCodec().readTree(p);
      return TableMetadataParser.fromJson(node);
    }
  }

  /** {@link TableMetadata} 的序列化器，委托 {@link TableMetadataParser#toJson} 写出 JSON。 */
  public static class TableMetadataSerializer extends JsonSerializer<TableMetadata> {
    @Override
    public void serialize(TableMetadata metadata, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      TableMetadataParser.toJson(metadata, gen);
    }
  }

  /** {@link MetadataUpdate} 的反序列化器，委托 {@link MetadataUpdateParser#fromJson} 解析 JSON 节点。 */
  public static class MetadataUpdateDeserializer extends JsonDeserializer<MetadataUpdate> {
    @Override
    public MetadataUpdate deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      JsonNode node = p.getCodec().readTree(p);
      return MetadataUpdateParser.fromJson(node);
    }
  }

  /** {@link MetadataUpdate} 的序列化器，委托 {@link MetadataUpdateParser#toJson} 写出 JSON。 */
  public static class MetadataUpdateSerializer extends JsonSerializer<MetadataUpdate> {
    @Override
    public void serialize(MetadataUpdate value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      MetadataUpdateParser.toJson(value, gen);
    }
  }

  /** {@link ErrorResponse} 的反序列化器，委托 {@link ErrorResponseParser#fromJson} 解析 JSON 节点。 */
  public static class ErrorResponseDeserializer extends JsonDeserializer<ErrorResponse> {
    @Override
    public ErrorResponse deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      JsonNode node = p.getCodec().readTree(p);
      return ErrorResponseParser.fromJson(node);
    }
  }

  /** {@link ErrorResponse} 的序列化器，委托 {@link ErrorResponseParser#toJson} 写出 JSON。 */
  public static class ErrorResponseSerializer extends JsonSerializer<ErrorResponse> {
    @Override
    public void serialize(
        ErrorResponse errorResponse, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      ErrorResponseParser.toJson(errorResponse, gen);
    }
  }

  /** {@link Namespace} 的反序列化器，将 JSON 数组读取为层级字符串数组并构建命名空间。 */
  public static class NamespaceDeserializer extends JsonDeserializer<Namespace> {
    @Override
    public Namespace deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      String[] levels = JsonUtil.getStringArray(p.getCodec().readTree(p));
      return Namespace.of(levels);
    }
  }

  /** {@link Namespace} 的序列化器，将命名空间层级写为 JSON 字符串数组。 */
  public static class NamespaceSerializer extends JsonSerializer<Namespace> {
    @Override
    public void serialize(Namespace namespace, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      String[] parts = namespace.levels();
      gen.writeArray(parts, 0, parts.length);
    }
  }

  /** {@link TableIdentifier} 的反序列化器，委托 {@link TableIdentifierParser#fromJson} 解析。 */
  public static class TableIdentifierDeserializer extends JsonDeserializer<TableIdentifier> {
    @Override
    public TableIdentifier deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return TableIdentifierParser.fromJson(jsonNode);
    }
  }

  /** {@link TableIdentifier} 的序列化器，委托 {@link TableIdentifierParser#toJson} 写出 JSON。 */
  public static class TableIdentifierSerializer extends JsonSerializer<TableIdentifier> {
    @Override
    public void serialize(
        TableIdentifier identifier, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      TableIdentifierParser.toJson(identifier, gen);
    }
  }

  /** {@link Schema} 的反序列化器，委托 {@link SchemaParser#fromJson} 解析 JSON 节点。 */
  public static class SchemaDeserializer extends JsonDeserializer<Schema> {
    @Override
    public Schema deserialize(JsonParser p, DeserializationContext context) throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return SchemaParser.fromJson(jsonNode);
    }
  }

  /** {@link Schema} 的序列化器，委托 {@link SchemaParser#toJson} 写出 JSON。 */
  public static class SchemaSerializer extends JsonSerializer<Schema> {
    @Override
    public void serialize(Schema schema, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      SchemaParser.toJson(schema, gen);
    }
  }

  /** {@link UnboundPartitionSpec} 的序列化器，委托 {@link PartitionSpecParser#toJson} 写出 JSON。 */
  public static class UnboundPartitionSpecSerializer extends JsonSerializer<UnboundPartitionSpec> {
    @Override
    public void serialize(
        UnboundPartitionSpec spec, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      PartitionSpecParser.toJson(spec, gen);
    }
  }

  /** {@link UnboundPartitionSpec} 的反序列化器，委托 {@link PartitionSpecParser#fromJson} 解析。 */
  public static class UnboundPartitionSpecDeserializer
      extends JsonDeserializer<UnboundPartitionSpec> {
    @Override
    public UnboundPartitionSpec deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return PartitionSpecParser.fromJson(jsonNode);
    }
  }

  /** {@link UnboundSortOrder} 的序列化器，委托 {@link SortOrderParser#toJson} 写出 JSON。 */
  public static class UnboundSortOrderSerializer extends JsonSerializer<UnboundSortOrder> {
    @Override
    public void serialize(
        UnboundSortOrder sortOrder, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      SortOrderParser.toJson(sortOrder, gen);
    }
  }

  /** {@link UnboundSortOrder} 的反序列化器，委托 {@link SortOrderParser#fromJson} 解析。 */
  public static class UnboundSortOrderDeserializer extends JsonDeserializer<UnboundSortOrder> {
    @Override
    public UnboundSortOrder deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return SortOrderParser.fromJson(jsonNode);
    }
  }

  /** {@link OAuthTokenResponse} 的序列化器，委托 {@link OAuth2Util#tokenResponseToJson} 写出 JSON。 */
  public static class OAuthTokenResponseSerializer extends JsonSerializer<OAuthTokenResponse> {
    @Override
    public void serialize(
        OAuthTokenResponse tokenResponse, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      OAuth2Util.tokenResponseToJson(tokenResponse, gen);
    }
  }

  /** {@link OAuthTokenResponse} 的反序列化器，委托 {@link OAuth2Util#tokenResponseFromJson} 解析。 */
  public static class OAuthTokenResponseDeserializer extends JsonDeserializer<OAuthTokenResponse> {
    @Override
    public OAuthTokenResponse deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return OAuth2Util.tokenResponseFromJson(jsonNode);
    }
  }

  /** {@link ReportMetricsRequest} 的泛型序列化器，委托 {@link ReportMetricsRequestParser#toJson} 写出 JSON。 */
  public static class ReportMetricsRequestSerializer<T extends ReportMetricsRequest>
      extends JsonSerializer<T> {
    @Override
    public void serialize(T request, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      ReportMetricsRequestParser.toJson(request, gen);
    }
  }

  /** {@link ReportMetricsRequest} 的泛型反序列化器，委托 {@link ReportMetricsRequestParser#fromJson} 解析。 */
  public static class ReportMetricsRequestDeserializer<T extends ReportMetricsRequest>
      extends JsonDeserializer<T> {
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return (T) ReportMetricsRequestParser.fromJson(jsonNode);
    }
  }

  /**
   * {@link CommitTransactionRequest} 的序列化器，委托 {@link CommitTransactionRequestParser#toJson} 写出
   * JSON。
   */
  public static class CommitTransactionRequestSerializer
      extends JsonSerializer<CommitTransactionRequest> {
    @Override
    public void serialize(
        CommitTransactionRequest request, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      CommitTransactionRequestParser.toJson(request, gen);
    }
  }

  /**
   * {@link CommitTransactionRequest} 的反序列化器，委托 {@link CommitTransactionRequestParser#fromJson} 解析。
   */
  public static class CommitTransactionRequestDeserializer
      extends JsonDeserializer<CommitTransactionRequest> {
    @Override
    public CommitTransactionRequest deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return CommitTransactionRequestParser.fromJson(jsonNode);
    }
  }

  /** {@link UpdateTableRequest} 的序列化器，委托 {@link UpdateTableRequestParser#toJson} 写出 JSON。 */
  public static class UpdateTableRequestSerializer extends JsonSerializer<UpdateTableRequest> {
    @Override
    public void serialize(
        UpdateTableRequest request, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      UpdateTableRequestParser.toJson(request, gen);
    }
  }

  /** {@link UpdateTableRequest} 的反序列化器，委托 {@link UpdateTableRequestParser#fromJson} 解析。 */
  public static class UpdateTableRequestDeserializer extends JsonDeserializer<UpdateTableRequest> {
    @Override
    public UpdateTableRequest deserialize(JsonParser p, DeserializationContext context)
        throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return UpdateTableRequestParser.fromJson(jsonNode);
    }
  }

  /** {@link RegisterTableRequest} 的泛型序列化器，委托 {@link RegisterTableRequestParser#toJson} 写出 JSON。 */
  public static class RegisterTableRequestSerializer<T extends RegisterTableRequest>
      extends JsonSerializer<T> {
    @Override
    public void serialize(T request, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      RegisterTableRequestParser.toJson(request, gen);
    }
  }

  /** {@link RegisterTableRequest} 的泛型反序列化器，委托 {@link RegisterTableRequestParser#fromJson} 解析。 */
  public static class RegisterTableRequestDeserializer<T extends RegisterTableRequest>
      extends JsonDeserializer<T> {
    @Override
    public T deserialize(JsonParser p, DeserializationContext context) throws IOException {
      JsonNode jsonNode = p.getCodec().readTree(p);
      return (T) RegisterTableRequestParser.fromJson(jsonNode);
    }
  }
}
