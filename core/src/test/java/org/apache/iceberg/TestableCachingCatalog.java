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
package org.apache.iceberg;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Optional;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.TableIdentifier;

/**
 * 测试类：TestableCachingCatalog，用于验证 able Caching Catalog 相关功能。
 *
 * <p>所属模块：iceberg-core（测试目录 src/test）。 职责：针对 able Caching Catalog 的核心行为构造多种场景，覆盖正常路径、边界条件与异常输入，
 * 确保实现与预期语义一致。
 *
 * <p>测试策略：基于 JUnit（必要时配合参数化执行器）搭建表/目录等测试基座， 通过构造输入、执行被测方法并断言结果或状态来验证功能点。
 */
public class TestableCachingCatalog extends CachingCatalog {

  /** 辅助方法：wrap。 */
  public static TestableCachingCatalog wrap(
      Catalog catalog, Duration expirationInterval, Ticker ticker) {
    return new TestableCachingCatalog(
        catalog, true /* caseSensitive */, expirationInterval, ticker);
  }

  private final Duration cacheExpirationInterval;

  TestableCachingCatalog(
      Catalog catalog, boolean caseSensitive, Duration expirationInterval, Ticker ticker) {
    super(catalog, caseSensitive, expirationInterval.toMillis(), ticker);
    this.cacheExpirationInterval = expirationInterval;
  }

  /** 辅助方法：cache。 */
  public Cache<TableIdentifier, Table> cache() {
    // cleanUp must be called as tests apply assertions directly on the underlying map, but metadata
    // table
    // map entries are cleaned up asynchronously.
    tableCache.cleanUp();
    return tableCache;
  }

  /** 辅助方法：is cache expiration enabled。 */
  public boolean isCacheExpirationEnabled() {
    return tableCache.policy().expireAfterAccess().isPresent()
        || tableCache.policy().expireAfterWrite().isPresent();
  }

  // Throws a NoSuchElementException if this entry is not in the cache (has already been TTL'd).
  public Optional<Duration> ageOf(TableIdentifier identifier) {
    return tableCache.policy().expireAfterAccess().get().ageOf(identifier);
  }

  // Throws a NoSuchElementException if the entry is not in the cache (has already been TTL'd).
  public Optional<Duration> remainingAgeFor(TableIdentifier identifier) {
    return ageOf(identifier).map(cacheExpirationInterval::minus);
  }
}
