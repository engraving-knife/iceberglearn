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
package org.apache.iceberg.spark;

import java.util.function.Function;
import org.apache.iceberg.transforms.Transforms;
import org.apache.iceberg.types.Type;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

/**
 * Iceberg Spark 工具类入口：注册 Iceberg 变换为 Spark UDF。
 *
 * <p>所属模块：iceberg-spark（Spark v3.5 集成模块），spark 顶级包。
 *
 * <p>职责：把 Iceberg 的 bucket、truncate 变换注册为 Spark SQL 用户自定义函数， 便于在 Spark SQL 中直接调用以计算分区值。
 *
 * <p>设计意图：先用 {@link SparkTypeToType} 把 Spark 类型转为 Iceberg 类型， 再绑定 Iceberg Transform 得到
 * Function，最后包装为 Spark UDF；输入值通过 {@link SparkValueConverter} 转为 Iceberg 内部表示后调用变换。
 *
 * <p>上下游关系：被用户代码或测试调用注册 UDF；依赖 iceberg-core 的 Transforms。
 */
public class IcebergSpark {
  private IcebergSpark() {}

  /**
   * 注册 bucket 变换为 Spark UDF。
   *
   * <p>逻辑：把 sourceType 转为 Iceberg 类型，绑定 bucket(numBuckets) 变换， 注册为返回 IntegerType 的 UDF；调用时把 Spark
   * 值转为 Iceberg 值再应用变换。
   *
   * @param session SparkSession
   * @param funcName UDF 名称
   * @param sourceType 源列 Spark 类型
   * @param numBuckets 桶数
   */
  public static void registerBucketUDF(
      SparkSession session, String funcName, DataType sourceType, int numBuckets) {
    SparkTypeToType typeConverter = new SparkTypeToType();
    Type sourceIcebergType = typeConverter.atomic(sourceType);
    Function<Object, Integer> bucket = Transforms.bucket(numBuckets).bind(sourceIcebergType);
    session
        .udf()
        .register(
            funcName,
            value -> bucket.apply(SparkValueConverter.convert(sourceIcebergType, value)),
            DataTypes.IntegerType);
  }

  /**
   * 注册 truncate 变换为 Spark UDF。
   *
   * <p>逻辑：把 sourceType 转为 Iceberg 类型，绑定 truncate(width) 变换， 注册为返回源类型的 UDF；调用时把 Spark 值转为 Iceberg
   * 值再应用变换。
   *
   * @param session SparkSession
   * @param funcName UDF 名称
   * @param sourceType 源列 Spark 类型
   * @param width 截断宽度
   */
  public static void registerTruncateUDF(
      SparkSession session, String funcName, DataType sourceType, int width) {
    SparkTypeToType typeConverter = new SparkTypeToType();
    Type sourceIcebergType = typeConverter.atomic(sourceType);
    Function<Object, Object> truncate = Transforms.truncate(width).bind(sourceIcebergType);
    session
        .udf()
        .register(
            funcName,
            value -> truncate.apply(SparkValueConverter.convert(sourceIcebergType, value)),
            sourceType);
  }
}
