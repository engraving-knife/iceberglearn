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
package org.apache.iceberg.mr.hive;

import static org.apache.iceberg.types.Types.NestedField.optional;

import java.util.List;
import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.apache.hadoop.hive.serde.serdeConstants;
import org.apache.iceberg.Schema;
import org.apache.iceberg.hive.TestHiveSchemaUtil;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.apache.iceberg.types.Types;

/**
 * 文件级说明：测试 Hive3 特有的 Schema 转换（Iceberg Schema ↔ Hive FieldSchema）。
 *
 * <p>所属模块：iceberg-hive3。职责：继承父类 {@link TestHiveSchemaUtil} 的通用测试用例，
 * 并扩展 Hive3 独有的 {@code timestamp with local time zone} 类型，验证该类型在
 * Iceberg 与 Hive3 之间的双向转换正确性。
 *
 * <p>测试策略：重写父类的 {@code getSupportedFieldSchemas} 和 {@code getSchemaWithSupportedTypes}，
 * 在父类已支持的类型列表基础上追加 TIMESTAMPLOCALTZ 类型，使继承的所有测试方法自动覆盖该类型。
 */
public class TestHiveSchemaUtilHive3 extends TestHiveSchemaUtil {

  /**
   * 返回 Hive3 支持的字段 schema 列表（在父类基础上追加 timestamp local tz）。
   *
   * <p>逻辑：拷贝父类列表 → 追加 {@code c_timestamptz} 字段（Hive3 独有类型）→ 返回。
   */
  @Override
  protected List<FieldSchema> getSupportedFieldSchemas() {
    List<FieldSchema> fields = Lists.newArrayList(super.getSupportedFieldSchemas());
    // timestamp local tz only present in Hive3
    fields.add(new FieldSchema("c_timestamptz", serdeConstants.TIMESTAMPLOCALTZ_TYPE_NAME, null));
    return fields;
  }

  /**
   * 返回包含 Hive3 全部支持类型的 Iceberg Schema（在父类基础上追加带时区的 Timestamp）。
   *
   * <p>逻辑：拷贝父类 schema 的列 → 追加 {@code c_timestamptz}（{@link Types.TimestampType#withZone()}）
   * → 构造新 Schema 返回。
   */
  @Override
  protected Schema getSchemaWithSupportedTypes() {
    Schema schema = super.getSchemaWithSupportedTypes();
    List<Types.NestedField> columns = Lists.newArrayList(schema.columns());
    // timestamp local tz only present in Hive3
    columns.add(optional(columns.size(), "c_timestamptz", Types.TimestampType.withZone()));
    return new Schema(columns);
  }
}
