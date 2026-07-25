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
package org.apache.iceberg.io;

/**
 * 文件级说明：批量删除失败异常。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：当 {@link SupportsBulkOperations#deleteFiles(Iterable)} 至少有一个文件删除失败时 抛出，携带失败对象数量。
 *
 * <p>设计意图：以非受检异常形式表示批量删除失败，并暴露失败数量便于上层做日志统计与重试 决策。
 *
 * <p>上下游关系：由批量删除实现抛出；被调用批量删除的上层流程捕获处理。
 */
public class BulkDeletionFailureException extends RuntimeException {
  private final int numberFailedObjects;

  /**
   * 构造异常，指定失败删除的对象数量。
   *
   * @param numberFailedObjects 删除失败的对象数量
   */
  public BulkDeletionFailureException(int numberFailedObjects) {
    super(String.format("Failed to delete %d files", numberFailedObjects));
    this.numberFailedObjects = numberFailedObjects;
  }

  /**
   * 返回本次批量删除中失败的对象数量。
   *
   * @return 失败对象数量
   */
  public int numberFailedObjects() {
    return numberFailedObjects;
  }
}
