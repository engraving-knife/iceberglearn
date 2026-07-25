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
 * Flink 逻辑类型访问者基类，对 Iceberg 不支持的类型统一抛出异常。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：实现 {@link LogicalTypeVisitor}，为 Iceberg 不支持的 类型（如
 * ZonedTimestampType、DistinctType、StructuredType、RawType 等）提供默认抛 UnsupportedOperationException
 * 的实现，子类只需覆盖感兴趣的类型。
 *
 * <p>设计意图：模板方法模式，集中处理不支持的类型，简化子类。
 */
public abstract class FlinkTypeVisitor<T> implements LogicalTypeVisitor<T> {

  // ------------------------- Unsupported types ------------------------------

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
