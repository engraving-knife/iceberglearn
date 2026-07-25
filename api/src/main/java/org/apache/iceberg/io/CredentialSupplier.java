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
 * 文件级说明：凭据暴露接口，用于把 FileIO 实例所持有的凭据以字符串形式对外暴露。
 *
 * <p>所属模块：iceberg-api（核心对外 API 模块）。
 *
 * <p>职责：提供 {@link #getCredential()} 方法返回 FileIO 已配置的凭据字符串。
 *
 * <p>设计意图：表会向使用方提供一个配置好访问凭据的 FileIO 实例。对于不直接使用 FileIO 而需要通过其他 IO 库访问文件的外部系统，可以通过本接口拿到已配置的凭据字符串，从而
 * 复用同一份凭证配置，避免重复维护鉴权信息。
 *
 * <p>上下游关系：由具备凭据的 FileIO 实现额外实现；被需要复用 Iceberg 凭证的外部 IO 路径调用。
 */
public interface CredentialSupplier {
  /**
   * 返回凭据字符串。
   *
   * @return 凭据字符串
   */
  String getCredential();
}
