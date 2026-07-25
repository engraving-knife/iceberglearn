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

import com.google.errorprone.annotations.FormatMethod;
import java.io.IOException;
import org.apache.iceberg.exceptions.NoSuchNamespaceException;
import org.apache.iceberg.exceptions.NoSuchTableException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.spark.sql.AnalysisException;

/**
 * 所属模块：iceberg-spark v3.5
 *
 * <p>职责：Spark 异常工具，将 Iceberg 异常转换为对应的 Spark 分析异常或运行时异常。
 *
 * <p>设计意图：集中处理异常映射，避免在各处重复 try-catch。
 *
 * <p>上下游关系：被 SparkCatalog、SparkTable、Procedures 等广泛调用。
 */
public class SparkExceptionUtil {

  private SparkExceptionUtil() {}

  /**
   * Converts checked exceptions to unchecked exceptions.
   *
   * @param cause a checked exception object which is to be converted to its unchecked equivalent.
   * @param message exception message as a format string
   * @param args format specifiers
   * @return unchecked exception.
   */
  @FormatMethod
  public static RuntimeException toUncheckedException(
      final Throwable cause, final String message, final Object... args) {
    // Parameters are required to be final to help @FormatMethod do static analysis
    if (cause instanceof RuntimeException) {
      return (RuntimeException) cause;

    } else if (cause instanceof org.apache.spark.sql.catalyst.analysis.NoSuchDatabaseException) {
      return new NoSuchNamespaceException(cause, message, args);

    } else if (cause instanceof org.apache.spark.sql.catalyst.analysis.NoSuchTableException) {
      return new NoSuchTableException(cause, message, args);

    } else if (cause instanceof AnalysisException) {
      return new ValidationException(cause, message, args);

    } else if (cause instanceof IOException) {
      return new RuntimeIOException((IOException) cause, message, args);

    } else {
      return new RuntimeException(String.format(message, args), cause);
    }
  }
}
