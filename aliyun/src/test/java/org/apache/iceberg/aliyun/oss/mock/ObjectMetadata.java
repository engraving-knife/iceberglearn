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
package org.apache.iceberg.aliyun.oss.mock;

import java.util.Map;

/**
 * 文件级说明：测试 ObjectMetadata 的功能。
 *
 * <p>所属模块：iceberg-aliyun。职责：验证 ObjectMetadata 在各类场景下的行为是否符合预期， 包括正常路径与边界条件。
 *
 * <p>测试策略：使用 JUnit 框架，通过构造输入、调用方法、断言结果来覆盖功能点。
 */
public class ObjectMetadata {

  private long contentLength;

  // In millis
  private long lastModificationDate;

  private String contentMD5;

  private String contentType;

  private String contentEncoding;

  private Map<String, String> userMetaData;

  private String dataFile;

  private String metaFile;

  // The following getters and setters are required for Jackson ObjectMapper serialization and
  // deserialization.

  public long getContentLength() {
    return contentLength;
  }

  /** 辅助方法：setContentLength。 */
  public void setContentLength(long contentLength) {
    this.contentLength = contentLength;
  }

  /** 辅助方法：getLastModificationDate。 */
  public long getLastModificationDate() {
    return lastModificationDate;
  }

  /** 辅助方法：setLastModificationDate。 */
  public void setLastModificationDate(long lastModificationDate) {
    this.lastModificationDate = lastModificationDate;
  }

  /** 辅助方法：getContentMD5。 */
  public String getContentMD5() {
    return contentMD5;
  }

  /** 辅助方法：setContentMD5。 */
  public void setContentMD5(String contentMD5) {
    this.contentMD5 = contentMD5;
  }

  /** 辅助方法：getContentType。 */
  public String getContentType() {
    return contentType;
  }

  /** 辅助方法：setContentType。 */
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  /** 辅助方法：getContentEncoding。 */
  public String getContentEncoding() {
    return contentEncoding;
  }

  /** 辅助方法：setContentEncoding。 */
  public void setContentEncoding(String contentEncoding) {
    this.contentEncoding = contentEncoding;
  }

  /** 辅助方法：getUserMetaData。 */
  public Map<String, String> getUserMetaData() {
    return userMetaData;
  }

  /** 辅助方法：setUserMetaData。 */
  public void setUserMetaData(Map<String, String> userMetaData) {
    this.userMetaData = userMetaData;
  }

  /** 辅助方法：getDataFile。 */
  public String getDataFile() {
    return dataFile;
  }

  /** 辅助方法：setDataFile。 */
  public void setDataFile(String dataFile) {
    this.dataFile = dataFile;
  }

  /** 辅助方法：getMetaFile。 */
  public String getMetaFile() {
    return metaFile;
  }

  /** 辅助方法：setMetaFile。 */
  public void setMetaFile(String metaFile) {
    this.metaFile = metaFile;
  }
}
