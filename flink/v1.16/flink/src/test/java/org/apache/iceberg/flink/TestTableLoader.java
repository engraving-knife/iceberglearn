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

import java.io.File;
import org.apache.iceberg.Table;
import org.apache.iceberg.TestTables;

/**
 * 文件级说明：测试 TestTableLoader 的功能。
 *
 * <p>所属模块：iceberg-flink（flink v1.16）。职责：验证 TestTableLoader 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 Flink TableEnvironment + JUnit，通过构造测试数据、执行 SQL/Table API 操作、 断言结果来覆盖正常路径与边界情况。
 */
public class TestTableLoader implements TableLoader {
  private File dir;

  /** 辅助方法：of，of。 */
  public static TableLoader of(String dir) {
    return new TestTableLoader(dir);
  }

  /** 辅助方法：TestTableLoader，Table Loader。 */
  public TestTableLoader(String dir) {
    this.dir = new File(dir);
  }

  /** 辅助方法：open，open。 */
  @Override
  public void open() {}

  /** 辅助方法：isOpen，is Open。 */
  @Override
  public boolean isOpen() {
    return true;
  }

  /** 辅助方法：loadTable，load Table。 */
  @Override
  public Table loadTable() {
    return TestTables.load(dir, "test");
  }

  /** 辅助方法：clone，clone。 */
  @Override
  @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
  public TableLoader clone() {
    return new TestTableLoader(dir.getAbsolutePath());
  }

  /** 辅助方法：close，close。 */
  @Override
  public void close() {}
}
