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
package org.apache.iceberg.flink;

import org.apache.flink.table.types.logical.DayTimeIntervalType;
import org.apache.flink.table.types.logical.DistinctType;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeVisitor;
import org.apache.flink.table.types.logical.NullType;
import org.apache.flink.table.types.logical.RawType;
import org.apache.flink.table.types.logical.StructuredType;
import org.apache.flink.table.types.logical.SymbolType;
import org.apache.flink.table.types.logical.YearMonthIntervalType;
import org.apache.flink.table.types.logical.ZonedTimestampType;

/**
 * 文件级说明：Flink {@link LogicalType} 访问器基类，对所有不支持的类型统一抛出异常。
 *
 * <p>所属模块：iceberg-flink（Flink 集成模块），实现 Flink 的 {@link LogicalTypeVisitor} 接口。
 *
 * <p>职责：为 Iceberg 不支持的 Flink 类型提供默认实现（抛出 UnsupportedOperationException）， 使子类只需重写支持的类型方法。
 *
 * <p>设计意图：模板方法模式。Iceberg 不支持 Flink 的 ZonedTimestampType、YearMonthIntervalType、
 * DayTimeIntervalType、DistinctType、StructuredType、NullType、RawType、SymbolType 等类型， 本类将这些类型的 visit
 * 方法统一实现为抛异常，避免子类重复编写。
 *
 * <p>上下游关系：被各 schema 访问器（如 {@link FlinkSchemaVisitor}）继承使用。
 *
 * @param <T> 访问返回类型
 */
public abstract class FlinkTypeVisitor<T> implements LogicalTypeVisitor<T> {

  // ------------------------- 不支持的类型：以下方法统一抛出 UnsupportedOperationException
  // ------------------------------

  @Override
  public T visit(ZonedTimestampType zonedTimestampType) {
    throw new UnsupportedOperationException("Unsupported ZonedTimestampType.");
  }

  @Override
  public T visit(YearMonthIntervalType yearMonthIntervalType) {
    throw new UnsupportedOperationException("Unsupported YearMonthIntervalType.");
  }

  @Override
  public T visit(DayTimeIntervalType dayTimeIntervalType) {
    throw new UnsupportedOperationException("Unsupported DayTimeIntervalType.");
  }

  @Override
  public T visit(DistinctType distinctType) {
    throw new UnsupportedOperationException("Unsupported DistinctType.");
  }

  @Override
  public T visit(StructuredType structuredType) {
    throw new UnsupportedOperationException("Unsupported StructuredType.");
  }

  @Override
  public T visit(NullType nullType) {
    throw new UnsupportedOperationException("Unsupported NullType.");
  }

  @Override
  public T visit(RawType<?> rawType) {
    throw new UnsupportedOperationException("Unsupported RawType.");
  }

  @Override
  public T visit(SymbolType<?> symbolType) {
    throw new UnsupportedOperationException("Unsupported SymbolType.");
  }

  @Override
  public T visit(LogicalType other) {
    throw new UnsupportedOperationException("Unsupported type: " + other);
  }
}
