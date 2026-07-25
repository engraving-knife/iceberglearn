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
package org.apache.iceberg.mr.hive.vector;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.common.type.HiveIntervalDayTime;
import org.apache.hadoop.hive.common.type.HiveIntervalYearMonth;
import org.apache.hadoop.hive.ql.exec.Utilities;
import org.apache.hadoop.hive.ql.exec.tez.DagUtils;
import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.ColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DecimalColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.DoubleColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.IntervalDayTimeColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.TimestampColumnVector;
import org.apache.hadoop.hive.ql.plan.BaseWork;
import org.apache.hadoop.hive.ql.plan.MapWork;
import org.apache.hadoop.hive.serde2.io.DateWritable;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.mapred.JobConf;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：从较新 Hive 源码移植的兼容性工具方法集合。
 *
 * <p>所属模块：iceberg-hive3（Iceberg 与 Hive3 集成模块）。
 *
 * <p>职责：在 Hive3 向量化执行路径中，提供 Hive 较新版本才具备的工具方法，
 * 以弥补 Hive3 自身 API 的缺失，保证 Iceberg 向量化读取器能够正确获取
 * MapWork 实例并填充分区列到向量批。
 *
 * <p>设计意图：临时性兼容层。当 Hive 4 发布且 Iceberg 切换至该版本依赖后，
 * 此类应当被移除，直接调用 Hive 原生 API。
 *
 * <p>上下游关系：上游为 Hive3 任务执行环境（{@link JobConf}），下游为向量化记录读取器
 * {@code HiveIcebergVectorizedRecordReader}。
 */
public class CompatibilityHiveVectorUtils {

  private static final Logger LOG = LoggerFactory.getLogger(CompatibilityHiveVectorUtils.class);

  /** 私有构造，工具类禁止实例化。 */
  private CompatibilityHiveVectorUtils() {}

  /**
   * 从 JobConf 中解析出序列化的 MapWork 实例（移植自 Hive 源码 LlapHiveUtils#findMapWork）。
   *
   * <p>逻辑：
   * <ol>
   *   <li>优先按 SMB 合并工作前缀查找合并工作；若 SMB 暂不可用则直接返回 null。</li>
   *   <li>若存在 inputName，则按 inputName 从合并工作缓存中获取；否则回退至普通 MapWork。</li>
   * </ol>
   *
   * @param job JobConf 实例
   * @return 基于给定 JobConf 解析出的 {@link MapWork}
   */
  public static MapWork findMapWork(JobConf job) {
    String inputName = job.get(Utilities.INPUT_NAME, null);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Initializing for input {}", inputName);
    }
    String prefixes = job.get(DagUtils.TEZ_MERGE_WORK_FILE_PREFIXES);
    if (prefixes != null && !prefixes.trim().isEmpty()) {
      // Currently SMB is broken, so we cannot check if it's  compatible with IO elevator.
      // So, we don't use the below code that would get the correct MapWork. See HIVE-16985.
      return null;
    }

    BaseWork work = null;
    // HIVE-16985: try to find the fake merge work for SMB join, that is really another MapWork.
    if (inputName != null) {
      if (prefixes == null || !Lists.newArrayList(prefixes.split(",")).contains(inputName)) {
        inputName = null;
      }
    }
    if (inputName != null) {
      work = Utilities.getMergeWork(job, inputName);
    }

    if (!(work instanceof MapWork)) {
      work = Utilities.getMapWork(job);
    }
    return (MapWork) work;
  }

  /**
   * 将分区列值写入向量化批的对应列向量（移植自 Hive 源码
   * VectorizedRowBatchCtx#addPartitionColsToBatch）。
   *
   * <p>逻辑：根据列类型枚举分支处理。null 值会标记列向量为无值且重复；
   * 非空值按类型填入对应的 ColumnVector（Long/Double/Bytes/Decimal/Timestamp 等），
   * 并设置 isRepeating=true，因为分区列对整批数据值相同。
   *
   * @param col 待写入的 ColumnVector
   * @param value 分区值
   * @param partitionColumnName 分区列名（仅用于异常信息）
   * @param rowColumnTypeInfo 列类型描述
   */
  //  @SuppressWarnings({"AvoidNestedBlocks", "FallThrough", "MethodLength", "CyclomaticComplexity",
  // "Indentation"})
  public static void addPartitionColsToBatch(
      ColumnVector col, Object value, String partitionColumnName, TypeInfo rowColumnTypeInfo) {
    PrimitiveTypeInfo primitiveTypeInfo = (PrimitiveTypeInfo) rowColumnTypeInfo;

    if (value == null) {
      col.noNulls = false;
      col.isNull[0] = true;
      col.isRepeating = true;
      return;
    }

    switch (primitiveTypeInfo.getPrimitiveCategory()) {
      case BOOLEAN:
        LongColumnVector booleanColumnVector = (LongColumnVector) col;
        booleanColumnVector.fill((Boolean) value ? 1 : 0);
        booleanColumnVector.isNull[0] = false;
        break;

      case BYTE:
        LongColumnVector byteColumnVector = (LongColumnVector) col;
        byteColumnVector.fill((Byte) value);
        byteColumnVector.isNull[0] = false;
        break;

      case SHORT:
        LongColumnVector shortColumnVector = (LongColumnVector) col;
        shortColumnVector.fill((Short) value);
        shortColumnVector.isNull[0] = false;
        break;

      case INT:
        LongColumnVector intColumnVector = (LongColumnVector) col;
        intColumnVector.fill((Integer) value);
        intColumnVector.isNull[0] = false;
        break;

      case LONG:
        LongColumnVector longColumnVector = (LongColumnVector) col;
        longColumnVector.fill((Long) value);
        longColumnVector.isNull[0] = false;
        break;

      case DATE:
        LongColumnVector dateColumnVector = (LongColumnVector) col;
        dateColumnVector.fill(DateWritable.dateToDays((Date) value));
        dateColumnVector.isNull[0] = false;
        break;

      case TIMESTAMP:
        TimestampColumnVector timeStampColumnVector = (TimestampColumnVector) col;
        timeStampColumnVector.fill((Timestamp) value);
        timeStampColumnVector.isNull[0] = false;
        break;

      case INTERVAL_YEAR_MONTH:
        LongColumnVector intervalYearMonthColumnVector = (LongColumnVector) col;
        intervalYearMonthColumnVector.fill(((HiveIntervalYearMonth) value).getTotalMonths());
        intervalYearMonthColumnVector.isNull[0] = false;
        break;

      case INTERVAL_DAY_TIME:
        IntervalDayTimeColumnVector intervalDayTimeColumnVector = (IntervalDayTimeColumnVector) col;
        intervalDayTimeColumnVector.fill((HiveIntervalDayTime) value);
        intervalDayTimeColumnVector.isNull[0] = false;
        break;

      case FLOAT:
        DoubleColumnVector floatColumnVector = (DoubleColumnVector) col;
        floatColumnVector.fill((Float) value);
        floatColumnVector.isNull[0] = false;
        break;

      case DOUBLE:
        DoubleColumnVector doubleColumnVector = (DoubleColumnVector) col;
        doubleColumnVector.fill((Double) value);
        doubleColumnVector.isNull[0] = false;
        break;

      case DECIMAL:
        DecimalColumnVector decimalColumnVector = (DecimalColumnVector) col;
        HiveDecimal hd = (HiveDecimal) value;
        decimalColumnVector.set(0, hd);
        decimalColumnVector.isRepeating = true;
        decimalColumnVector.isNull[0] = false;
        break;

      case BINARY:
        BytesColumnVector binaryColumnVector = (BytesColumnVector) col;
        byte[] bytes = (byte[]) value;
        binaryColumnVector.fill(bytes);
        binaryColumnVector.isNull[0] = false;
        break;

      case STRING:
      case CHAR:
      case VARCHAR:
        BytesColumnVector bytesColumnVector = (BytesColumnVector) col;
        String sVal = value.toString();
        if (sVal == null) {
          bytesColumnVector.noNulls = false;
          bytesColumnVector.isNull[0] = true;
          bytesColumnVector.isRepeating = true;
        } else {
          bytesColumnVector.setVal(0, sVal.getBytes(StandardCharsets.UTF_8));
          bytesColumnVector.isRepeating = true;
        }
        break;

      default:
        throw new RuntimeException(
            "Unable to recognize the partition type "
                + primitiveTypeInfo.getPrimitiveCategory()
                + " for column "
                + partitionColumnName);
    }
  }
}
