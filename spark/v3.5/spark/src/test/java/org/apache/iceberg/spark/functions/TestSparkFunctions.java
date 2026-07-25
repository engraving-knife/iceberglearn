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
package org.apache.iceberg.spark.functions;

import org.apache.spark.sql.connector.catalog.functions.ScalarFunction;
import org.apache.spark.sql.connector.catalog.functions.UnboundFunction;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DecimalType;
import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * 文件级说明：测试 TestSparkFunctions 相关功能。
 *
 * <p>所属模块：iceberg-spark（spark v3.5）。职责：验证 Iceberg 表在 Spark 引擎下 Spark函数 相关行为，覆盖正常路径与边界场景。
 *
 * <p>测试策略：基于 SparkSession + JUnit，通过构造测试数据、执行 SQL/DataFrame 操作并断言结果， 覆盖正常路径与边界情况。
 */
public class TestSparkFunctions {

  /** 测试构建years函数从类场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBuildYearsFunctionFromClass() {
    UnboundFunction expected = new YearsFunction();

    YearsFunction.DateToYearsFunction dateToYearsFunc = new YearsFunction.DateToYearsFunction();
    checkBuildFunc(dateToYearsFunc, expected);

    YearsFunction.TimestampToYearsFunction tsToYearsFunc =
        new YearsFunction.TimestampToYearsFunction();
    checkBuildFunc(tsToYearsFunc, expected);

    YearsFunction.TimestampNtzToYearsFunction tsNtzToYearsFunc =
        new YearsFunction.TimestampNtzToYearsFunction();
    checkBuildFunc(tsNtzToYearsFunc, expected);
  }

  /** 测试构建months函数从类场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBuildMonthsFunctionFromClass() {
    UnboundFunction expected = new MonthsFunction();

    MonthsFunction.DateToMonthsFunction dateToMonthsFunc =
        new MonthsFunction.DateToMonthsFunction();
    checkBuildFunc(dateToMonthsFunc, expected);

    MonthsFunction.TimestampToMonthsFunction tsToMonthsFunc =
        new MonthsFunction.TimestampToMonthsFunction();
    checkBuildFunc(tsToMonthsFunc, expected);

    MonthsFunction.TimestampNtzToMonthsFunction tsNtzToMonthsFunc =
        new MonthsFunction.TimestampNtzToMonthsFunction();
    checkBuildFunc(tsNtzToMonthsFunc, expected);
  }

  /** 测试构建days函数从类场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBuildDaysFunctionFromClass() {
    UnboundFunction expected = new DaysFunction();

    DaysFunction.DateToDaysFunction dateToDaysFunc = new DaysFunction.DateToDaysFunction();
    checkBuildFunc(dateToDaysFunc, expected);

    DaysFunction.TimestampToDaysFunction tsToDaysFunc = new DaysFunction.TimestampToDaysFunction();
    checkBuildFunc(tsToDaysFunc, expected);

    DaysFunction.TimestampNtzToDaysFunction tsNtzToDaysFunc =
        new DaysFunction.TimestampNtzToDaysFunction();
    checkBuildFunc(tsNtzToDaysFunc, expected);
  }

  /** 测试构建hours函数从类场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBuildHoursFunctionFromClass() {
    UnboundFunction expected = new HoursFunction();

    HoursFunction.TimestampToHoursFunction tsToHoursFunc =
        new HoursFunction.TimestampToHoursFunction();
    checkBuildFunc(tsToHoursFunc, expected);

    HoursFunction.TimestampNtzToHoursFunction tsNtzToHoursFunc =
        new HoursFunction.TimestampNtzToHoursFunction();
    checkBuildFunc(tsNtzToHoursFunc, expected);
  }

  /** 测试构建桶函数从类场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBuildBucketFunctionFromClass() {
    UnboundFunction expected = new BucketFunction();

    BucketFunction.BucketInt bucketDateFunc = new BucketFunction.BucketInt(DataTypes.DateType);
    checkBuildFunc(bucketDateFunc, expected);

    BucketFunction.BucketInt bucketIntFunc = new BucketFunction.BucketInt(DataTypes.IntegerType);
    checkBuildFunc(bucketIntFunc, expected);

    BucketFunction.BucketLong bucketLongFunc = new BucketFunction.BucketLong(DataTypes.LongType);
    checkBuildFunc(bucketLongFunc, expected);

    BucketFunction.BucketLong bucketTsFunc = new BucketFunction.BucketLong(DataTypes.TimestampType);
    checkBuildFunc(bucketTsFunc, expected);

    BucketFunction.BucketLong bucketTsNtzFunc =
        new BucketFunction.BucketLong(DataTypes.TimestampNTZType);
    checkBuildFunc(bucketTsNtzFunc, expected);

    BucketFunction.BucketDecimal bucketDecimalFunc =
        new BucketFunction.BucketDecimal(new DecimalType());
    checkBuildFunc(bucketDecimalFunc, expected);

    BucketFunction.BucketString bucketStringFunc = new BucketFunction.BucketString();
    checkBuildFunc(bucketStringFunc, expected);

    BucketFunction.BucketBinary bucketBinary = new BucketFunction.BucketBinary();
    checkBuildFunc(bucketBinary, expected);
  }

  /** 测试构建截断函数从类场景：验证该方法在对应输入下的行为与断言结果。 */
  @Test
  public void testBuildTruncateFunctionFromClass() {
    UnboundFunction expected = new TruncateFunction();

    TruncateFunction.TruncateTinyInt truncateTinyIntFunc = new TruncateFunction.TruncateTinyInt();
    checkBuildFunc(truncateTinyIntFunc, expected);

    TruncateFunction.TruncateSmallInt truncateSmallIntFunc =
        new TruncateFunction.TruncateSmallInt();
    checkBuildFunc(truncateSmallIntFunc, expected);

    TruncateFunction.TruncateInt truncateIntFunc = new TruncateFunction.TruncateInt();
    checkBuildFunc(truncateIntFunc, expected);

    TruncateFunction.TruncateBigInt truncateBigIntFunc = new TruncateFunction.TruncateBigInt();
    checkBuildFunc(truncateBigIntFunc, expected);

    TruncateFunction.TruncateDecimal truncateDecimalFunc =
        new TruncateFunction.TruncateDecimal(10, 9);
    checkBuildFunc(truncateDecimalFunc, expected);

    TruncateFunction.TruncateString truncateStringFunc = new TruncateFunction.TruncateString();
    checkBuildFunc(truncateStringFunc, expected);

    TruncateFunction.TruncateBinary truncateBinaryFunc = new TruncateFunction.TruncateBinary();
    checkBuildFunc(truncateBinaryFunc, expected);
  }

  /** 检查构建func。 */
  private void checkBuildFunc(ScalarFunction<?> function, UnboundFunction expected) {
    UnboundFunction actual = SparkFunctions.loadFunctionByClass(function.getClass());

    Assertions.assertThat(actual).isNotNull();
    Assertions.assertThat(actual.name()).isEqualTo(expected.name());
    Assertions.assertThat(actual.description()).isEqualTo(expected.description());
  }
}
