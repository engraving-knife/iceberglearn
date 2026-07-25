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

package org.apache.spark.sql.catalyst

import org.apache.spark.sql.catalyst.util.ArrayData
import org.apache.spark.sql.catalyst.util.MapData
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.types.Decimal
import org.apache.spark.sql.types.StructType
import org.apache.spark.unsafe.types.CalendarInterval
import org.apache.spark.unsafe.types.UTF8String

/**
 * Iceberg Spark 集成相关组件。
 *
 * <p>所属模块：iceberg-spark-extensions v3.3。
 * 类型：样例类 ProjectingInternalRow。
 */
case class ProjectingInternalRow(schema: StructType, colOrdinals: Seq[Int]) extends InternalRow {
  assert(schema.size == colOrdinals.size)

  private var row: InternalRow = _

  /**
   * 执行该方法的具体逻辑。
   * @return 结果对象
   */
  override def numFields: Int = colOrdinals.size

  /** 执行该方法的具体逻辑。 */
  def project(row: InternalRow): Unit = {
    this.row = row
  }

  /** 设置nullat。 */
  override def setNullAt(i: Int): Unit = {
    throw new UnsupportedOperationException("Cannot modify InternalRowProjection")
  }

  /** 更新数据或状态。 */
  override def update(i: Int, value: Any): Unit = {
    throw new UnsupportedOperationException("Cannot modify InternalRowProjection")
  }

  /**
   * 返回当前对象的副本。
   * @return 结果对象
   */
  override def copy(): InternalRow = {
    val newRow = if (row != null) row.copy() else null
    val newProjection = ProjectingInternalRow(schema, colOrdinals)
    newProjection.project(newRow)
    newProjection
  }

  /** 判断是否nullat。 */
  override def isNullAt(ordinal: Int): Boolean = {
    row.isNullAt(colOrdinals(ordinal))
  }

  /** 返回boolean。 */
  override def getBoolean(ordinal: Int): Boolean = {
    row.getBoolean(colOrdinals(ordinal))
  }

  /** 返回byte。 */
  override def getByte(ordinal: Int): Byte = {
    row.getByte(colOrdinals(ordinal))
  }

  /** 返回short。 */
  override def getShort(ordinal: Int): Short = {
    row.getShort(colOrdinals(ordinal))
  }

  /** 返回int。 */
  override def getInt(ordinal: Int): Int = {
    row.getInt(colOrdinals(ordinal))
  }

  /** 返回long。 */
  override def getLong(ordinal: Int): Long = {
    row.getLong(colOrdinals(ordinal))
  }

  /** 返回float。 */
  override def getFloat(ordinal: Int): Float = {
    row.getFloat(colOrdinals(ordinal))
  }

  /** 返回double。 */
  override def getDouble(ordinal: Int): Double = {
    row.getDouble(colOrdinals(ordinal))
  }

  /** 返回decimal。 */
  override def getDecimal(ordinal: Int, precision: Int, scale: Int): Decimal = {
    row.getDecimal(colOrdinals(ordinal), precision, scale)
  }

  /** 返回utf8string。 */
  override def getUTF8String(ordinal: Int): UTF8String = {
    row.getUTF8String(colOrdinals(ordinal))
  }

  /** 返回binary。 */
  override def getBinary(ordinal: Int): Array[Byte] = {
    row.getBinary(colOrdinals(ordinal))
  }

  /** 返回interval。 */
  override def getInterval(ordinal: Int): CalendarInterval = {
    row.getInterval(colOrdinals(ordinal))
  }

  /** 返回struct。 */
  override def getStruct(ordinal: Int, numFields: Int): InternalRow = {
    row.getStruct(colOrdinals(ordinal), numFields)
  }

  /** 返回array。 */
  override def getArray(ordinal: Int): ArrayData = {
    row.getArray(colOrdinals(ordinal))
  }

  /** 返回map。 */
  override def getMap(ordinal: Int): MapData = {
    row.getMap(colOrdinals(ordinal))
  }

  /**
   * 执行该方法的具体逻辑。
   * @return 对应结果
   */
  override def get(ordinal: Int, dataType: DataType): AnyRef = {
    row.get(colOrdinals(ordinal), dataType)
  }
}
