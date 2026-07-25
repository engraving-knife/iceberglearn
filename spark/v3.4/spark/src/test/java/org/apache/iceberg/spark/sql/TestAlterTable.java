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
package org.apache.iceberg.spark.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.hadoop.HadoopCatalog;
import org.apache.iceberg.spark.SparkCatalogTestBase;
import org.apache.iceberg.types.Types;
import org.apache.iceberg.types.Types.NestedField;
import org.apache.spark.SparkException;
import org.apache.spark.sql.AnalysisException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * 文件级说明：测试 TestAlterTable 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.4）。职责：验证 Iceberg 表在 Spark 引擎下 修改表 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestAlterTable extends SparkCatalogTestBase {
  private final TableIdentifier renamedIdent =
      TableIdentifier.of(Namespace.of("default"), "table2");

  /** 测试修改表。 */
  public TestAlterTable(String catalogName, String implementation, Map<String, String> config) {
    super(catalogName, implementation, config);
  }

  /** 创建表。 */
  @Before
  public void createTable() {
    sql("CREATE TABLE %s (id bigint NOT NULL, data string) USING iceberg", tableName);
  }

  /** 移除表。 */
  @After
  public void removeTable() {
    sql("DROP TABLE IF EXISTS %s", tableName);
    sql("DROP TABLE IF EXISTS %s2", tableName);
  }

  /** 测试添加列非空值场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAddColumnNotNull() {
    assertThatThrownBy(() -> sql("ALTER TABLE %s ADD COLUMN c3 INT NOT NULL", tableName))
        .isInstanceOf(SparkException.class)
        .hasMessage(
            "Unsupported table change: Incompatible change: cannot add required column: c3");
  }

  /** 测试添加列场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAddColumn() {
    sql(
        "ALTER TABLE %s ADD COLUMN point struct<x: double NOT NULL, y: double NOT NULL> AFTER id",
        tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(
                3,
                "point",
                Types.StructType.of(
                    NestedField.required(4, "x", Types.DoubleType.get()),
                    NestedField.required(5, "y", Types.DoubleType.get()))),
            NestedField.optional(2, "data", Types.StringType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());

    sql("ALTER TABLE %s ADD COLUMN point.z double COMMENT 'May be null' FIRST", tableName);

    Types.StructType expectedSchema2 =
        Types.StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(
                3,
                "point",
                Types.StructType.of(
                    NestedField.optional(6, "z", Types.DoubleType.get(), "May be null"),
                    NestedField.required(4, "x", Types.DoubleType.get()),
                    NestedField.required(5, "y", Types.DoubleType.get()))),
            NestedField.optional(2, "data", Types.StringType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema2,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试添加列带数组场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAddColumnWithArray() {
    sql("ALTER TABLE %s ADD COLUMN data2 array<struct<a:INT,b:INT,c:int>>", tableName);
    // use the implicit column name 'element' to access member of array and add column d to struct.
    sql("ALTER TABLE %s ADD COLUMN data2.element.d int", tableName);
    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()),
            NestedField.optional(
                3,
                "data2",
                Types.ListType.ofOptional(
                    4,
                    Types.StructType.of(
                        NestedField.optional(5, "a", Types.IntegerType.get()),
                        NestedField.optional(6, "b", Types.IntegerType.get()),
                        NestedField.optional(7, "c", Types.IntegerType.get()),
                        NestedField.optional(8, "d", Types.IntegerType.get())))));
    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试添加列带映射场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAddColumnWithMap() {
    sql("ALTER TABLE %s ADD COLUMN data2 map<struct<x:INT>, struct<a:INT,b:INT>>", tableName);
    // use the implicit column name 'key' and 'value' to access member of map.
    // add column to value struct column
    sql("ALTER TABLE %s ADD COLUMN data2.value.c int", tableName);
    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()),
            NestedField.optional(
                3,
                "data2",
                Types.MapType.ofOptional(
                    4,
                    5,
                    Types.StructType.of(NestedField.optional(6, "x", Types.IntegerType.get())),
                    Types.StructType.of(
                        NestedField.optional(7, "a", Types.IntegerType.get()),
                        NestedField.optional(8, "b", Types.IntegerType.get()),
                        NestedField.optional(9, "c", Types.IntegerType.get())))));
    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());

    // should not allow changing map key column
    assertThatThrownBy(() -> sql("ALTER TABLE %s ADD COLUMN data2.key.y int", tableName))
        .isInstanceOf(SparkException.class)
        .hasMessageStartingWith("Unsupported table change: Cannot add fields to map keys:");
  }

  /** 测试删除列场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testDropColumn() {
    sql("ALTER TABLE %s DROP COLUMN data", tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(NestedField.required(1, "id", Types.LongType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试重命名列场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testRenameColumn() {
    sql("ALTER TABLE %s RENAME COLUMN id TO row_id", tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.required(1, "row_id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试修改列comment场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAlterColumnComment() {
    sql("ALTER TABLE %s ALTER COLUMN id COMMENT 'Record id'", tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.required(1, "id", Types.LongType.get(), "Record id"),
            NestedField.optional(2, "data", Types.StringType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试修改列类型场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAlterColumnType() {
    sql("ALTER TABLE %s ADD COLUMN count int", tableName);
    sql("ALTER TABLE %s ALTER COLUMN count TYPE bigint", tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()),
            NestedField.optional(3, "count", Types.LongType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试修改列删除非空值场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAlterColumnDropNotNull() {
    sql("ALTER TABLE %s ALTER COLUMN id DROP NOT NULL", tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.optional(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试修改列集合非空值场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAlterColumnSetNotNull() {
    // no-op changes are allowed
    sql("ALTER TABLE %s ALTER COLUMN id SET NOT NULL", tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());

    assertThatThrownBy(() -> sql("ALTER TABLE %s ALTER COLUMN data SET NOT NULL", tableName))
        .isInstanceOf(AnalysisException.class)
        .hasMessageStartingWith("Cannot change nullable column to non-nullable: data");
  }

  /** 测试修改列位置后场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAlterColumnPositionAfter() {
    sql("ALTER TABLE %s ADD COLUMN count int", tableName);
    sql("ALTER TABLE %s ALTER COLUMN count AFTER id", tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(3, "count", Types.IntegerType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试修改列位置第一个场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testAlterColumnPositionFirst() {
    sql("ALTER TABLE %s ADD COLUMN count int", tableName);
    sql("ALTER TABLE %s ALTER COLUMN count FIRST", tableName);

    Types.StructType expectedSchema =
        Types.StructType.of(
            NestedField.optional(3, "count", Types.IntegerType.get()),
            NestedField.required(1, "id", Types.LongType.get()),
            NestedField.optional(2, "data", Types.StringType.get()));

    Assert.assertEquals(
        "Schema should match expected",
        expectedSchema,
        validationCatalog.loadTable(tableIdent).schema().asStruct());
  }

  /** 测试表重命名场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testTableRename() {
    Assume.assumeFalse(
        "Hadoop catalog does not support rename", validationCatalog instanceof HadoopCatalog);

    Assert.assertTrue("Initial name should exist", validationCatalog.tableExists(tableIdent));
    Assert.assertFalse("New name should not exist", validationCatalog.tableExists(renamedIdent));

    sql("ALTER TABLE %s RENAME TO %s2", tableName, tableName);

    Assert.assertFalse("Initial name should not exist", validationCatalog.tableExists(tableIdent));
    Assert.assertTrue("New name should exist", validationCatalog.tableExists(renamedIdent));
  }

  /** 测试集合表属性场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testSetTableProperties() {
    sql("ALTER TABLE %s SET TBLPROPERTIES ('prop'='value')", tableName);

    assertThat(validationCatalog.loadTable(tableIdent).properties().get("prop"))
        .as("Should have the new table property")
        .isEqualTo("value");

    sql("ALTER TABLE %s UNSET TBLPROPERTIES ('prop')", tableName);

    assertThat(validationCatalog.loadTable(tableIdent).properties().get("prop"))
        .as("Should not have the removed table property")
        .isNull();

    String[] reservedProperties = new String[] {"sort-order", "identifier-fields"};
    for (String reservedProp : reservedProperties) {
      assertThatThrownBy(
              () -> sql("ALTER TABLE %s SET TBLPROPERTIES ('%s'='value')", tableName, reservedProp))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageStartingWith(
              "Cannot specify the '%s' because it's a reserved table property", reservedProp);
    }
  }
}
