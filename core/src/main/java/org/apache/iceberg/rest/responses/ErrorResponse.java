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
package org.apache.iceberg.rest.responses;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.rest.RESTResponse;

/**
 * 文件级说明：REST API 错误响应的标准模型。
 *
 * <p>所属模块：iceberg-core（REST Catalog 响应模型层）。
 *
 * <p>职责：封装错误响应的结构化信息，包括 HTTP 状态码（code）、错误类型（type）、 消息（message）和可选的堆栈跟踪（stack）。
 *
 * <p>设计意图：遵循 Iceberg REST Catalog 规范的错误响应格式，使客户端能根据 type 字段 精确映射到对应的 Iceberg 异常。使用 Builder
 * 模式保证不可变性。
 *
 * <p>上下游关系：由 {@link org.apache.iceberg.rest.ErrorHandler} 解析， 由 {@link ErrorResponseParser}
 * 序列化/反序列化。
 */
public class ErrorResponse implements RESTResponse {

  private String message;
  private String type;
  private int code;
  private List<String> stack;

  private ErrorResponse(String message, String type, int code, List<String> stack) {
    this.message = message;
    this.type = type;
    this.code = code;
    this.stack = stack;
    validate();
  }

  @Override
  public void validate() {
    // Because we use the `ErrorResponseParser`, validation is done there.
  }

  public String message() {
    return message;
  }

  public String type() {
    return type;
  }

  public Integer code() {
    return code;
  }

  public List<String> stack() {
    return stack;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ErrorResponse(")
        .append("code=")
        .append(code)
        .append(", type=")
        .append(type)
        .append(", message=")
        .append(message)
        .append(")");

    if (stack != null && !stack.isEmpty()) {
      for (String line : stack) {
        sb.append("\n").append(line);
      }
    }

    return sb.toString();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String message;
    private String type;
    private Integer code;
    private List<String> stack;

    private Builder() {}

    public Builder withMessage(String errorMessage) {
      this.message = errorMessage;
      return this;
    }

    public Builder withType(String errorType) {
      this.type = errorType;
      return this;
    }

    public Builder withStackTrace(Throwable throwable) {
      StringWriter sw = new StringWriter();
      try (PrintWriter pw = new PrintWriter(sw)) {
        throwable.printStackTrace(pw);
      }

      this.stack = Arrays.asList(sw.toString().split("\n"));

      return this;
    }

    public Builder withStackTrace(List<String> trace) {
      this.stack = trace;
      return this;
    }

    public Builder responseCode(Integer responseCode) {
      this.code = responseCode;
      return this;
    }

    public ErrorResponse build() {
      Preconditions.checkArgument(code != null, "Invalid response, missing field: code");
      return new ErrorResponse(message, type, code, stack);
    }
  }
}
