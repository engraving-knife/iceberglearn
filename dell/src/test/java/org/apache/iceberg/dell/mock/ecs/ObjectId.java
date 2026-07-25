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
package org.apache.iceberg.dell.mock.ecs;

import java.util.Comparator;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Objects;

/**
 * 文件级说明：测试 ObjectId 的功能。
 *
 * <p>所属模块：iceberg-dell。职责：验证 ObjectId 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class ObjectId implements Comparable<ObjectId> {
  public static final Comparator<ObjectId> COMPARATOR =
      Comparator.<ObjectId, String>comparing(id -> id.bucket).thenComparing(id -> id.name);

  public final String bucket;
  public final String name;

  /** 辅助方法：ObjectId。 */
  public ObjectId(String bucket, String name) {
    this.bucket = bucket;
    this.name = name;
  }

  /** 辅助方法：equals。 */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ObjectId objectId = (ObjectId) o;
    return Objects.equal(bucket, objectId.bucket) && Objects.equal(name, objectId.name);
  }

  /** 辅助方法：hashCode。 */
  @Override
  public int hashCode() {
    return Objects.hashCode(bucket, name);
  }

  /** 辅助方法：toString。 */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("bucket", bucket).add("name", name).toString();
  }

  /** 辅助方法：compareTo。 */
  @Override
  public int compareTo(ObjectId o) {
    return COMPARATOR.compare(this, o);
  }
}
