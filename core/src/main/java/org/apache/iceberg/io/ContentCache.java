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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.ValidationException;
import org.apache.iceberg.relocated.com.google.common.base.MoreObjects;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件级说明：读取阶段的文件内容缓存。
 *
 * <p>所属模块：iceberg-core。
 *
 * <p>职责：在读取小文件（如 manifest、metadata 文件）时，将其内容缓存到内存中，避免 反复从底层存储读取，提升读取性能。
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>基于 Caffeine 缓存：支持按访问后过期、按总字节数权重淘汰、softValues 让 GC 在内存 紧张时可回收。
 *   <li>仅缓存长度不超过 maxContentLength 的文件，避免大文件占用过多缓存。
 *   <li>读时回填：通过 {@link CachingInputFile} 包装，首次访问时整文件读入缓存，后续访问 直接从 ByteBuffer 列表构造
 *       ByteBufferInputStream，避免再次 IO。
 *   <li>失败回退：缓存加载失败时退回原始 InputFile，保证可用性。
 * </ul>
 *
 * <p>上下游关系：由表配置（如 ManifestList 缓存）使用；底层依赖 FileIO 读取真实文件， 上层调用方通过 {@link #tryCache(FileIO, String,
 * long)} 获取可缓存的 InputFile。
 */
public class ContentCache {
  private static final Logger LOG = LoggerFactory.getLogger(ContentCache.class);
  private static final int BUFFER_CHUNK_SIZE = 4 * 1024 * 1024; // 4MB

  private final long expireAfterAccessMs;
  private final long maxTotalBytes;
  private final long maxContentLength;
  private final Cache<String, CacheEntry> cache;

  /**
   * 构造 ContentCache。
   *
   * <p>逻辑：校验参数合法性后，基于 Caffeine 构建缓存——设置访问后过期时间、最大权重（字节数）、 软引用值、移除监听器和统计。
   *
   * @param expireAfterAccessMs 访问后过期时间（毫秒），>=0；设为 0 表示仅在内存压力下驱逐
   * @param maxTotalBytes 缓存最大总字节数，必须 >0
   * @param maxContentLength 允许缓存的最大文件长度，必须 >0
   */
  public ContentCache(long expireAfterAccessMs, long maxTotalBytes, long maxContentLength) {
    ValidationException.check(expireAfterAccessMs >= 0, "expireAfterAccessMs is less than 0");
    ValidationException.check(maxTotalBytes > 0, "maxTotalBytes is equal or less than 0");
    ValidationException.check(maxContentLength > 0, "maxContentLength is equal or less than 0");
    this.expireAfterAccessMs = expireAfterAccessMs;
    this.maxTotalBytes = maxTotalBytes;
    this.maxContentLength = maxContentLength;

    Caffeine<Object, Object> builder = Caffeine.newBuilder();
    if (expireAfterAccessMs > 0) {
      builder = builder.expireAfterAccess(Duration.ofMillis(expireAfterAccessMs));
    }

    this.cache =
        builder
            .maximumWeight(maxTotalBytes)
            .weigher(
                (Weigher<String, CacheEntry>)
                    (key, value) -> (int) Math.min(value.length, Integer.MAX_VALUE))
            .softValues()
            .removalListener(
                (location, cacheEntry, cause) ->
                    LOG.debug("Evicted {} from ContentCache ({})", location, cause))
            .recordStats()
            .build();
  }

  /** 返回访问后过期时间（毫秒）。 */
  public long expireAfterAccess() {
    return expireAfterAccessMs;
  }

  /** 返回允许缓存的最大文件长度。 */
  public long maxContentLength() {
    return maxContentLength;
  }

  /** 返回缓存的最大总字节数。 */
  public long maxTotalBytes() {
    return maxTotalBytes;
  }

  /** 返回缓存的统计信息。 */
  public CacheStats stats() {
    return cache.stats();
  }

  /** 按 key 获取缓存项，不存在时用 mappingFunction 加载。 */
  public CacheEntry get(String key, Function<String, CacheEntry> mappingFunction) {
    return cache.get(key, mappingFunction);
  }

  /** 按 location 获取已缓存的项，不存在返回 null。 */
  public CacheEntry getIfPresent(String location) {
    return cache.getIfPresent(location);
  }

  /**
   * 尝试缓存指定位置的文件内容，在读取流时生效。
   *
   * <p>逻辑：若文件长度不超过 maxContentLength，则返回由 ContentCache 支撑的 {@link CachingInputFile}；否则返回普通 {@link
   * InputFile}（不缓存）。
   *
   * @param io 与 location 关联的 FileIO
   * @param location 文件的 URL/路径
   * @param length 文件的已知长度
   * @return 长度在允许范围内返回 {@link CachingInputFile}，否则返回普通 {@link InputFile}
   */
  public InputFile tryCache(FileIO io, String location, long length) {
    if (length <= maxContentLength) {
      return new CachingInputFile(this, io, location, length);
    }
    return io.newInputFile(location, length);
  }

  /** 使指定 key 的缓存项失效。 */
  public void invalidate(String key) {
    cache.invalidate(key);
  }

  /** 使所有缓存项失效。 */
  public void invalidateAll() {
    cache.invalidateAll();
  }

  /** 触发缓存清理。 */
  public void cleanUp() {
    cache.cleanUp();
  }

  /** 返回缓存的估算大小。 */
  public long estimatedCacheSize() {
    return cache.estimatedSize();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("expireAfterAccessMs", expireAfterAccessMs)
        .add("maxContentLength", maxContentLength)
        .add("maxTotalBytes", maxTotalBytes)
        .add("cacheStats", cache.stats())
        .toString();
  }

  private static class CacheEntry {
    private final long length;
    private final List<ByteBuffer> buffers;

    private CacheEntry(long length, List<ByteBuffer> buffers) {
      this.length = length;
      this.buffers = buffers;
    }
  }

  /**
   * 由 {@link ContentCache} 支撑的 {@link InputFile} 实现。
   *
   * <p>设计意图：调用 {@link #newStream()} 时优先从缓存返回 ByteBufferInputStream；若文件内容 尚未缓存，则按 4MB
   * 分块读入缓存后返回；缓存加载失败时退回原始 InputFile 的流。
   */
  private static class CachingInputFile implements InputFile {
    private final ContentCache contentCache;
    private final FileIO io;
    private final String location;
    private final long length;
    private InputFile fallbackInputFile = null;

    private CachingInputFile(ContentCache cache, FileIO io, String location, long length) {
      this.contentCache = cache;
      this.io = io;
      this.location = location;
      this.length = length;
    }

    /** 懒加载底层真实 InputFile，作为缓存失效时的回退。 */
    private InputFile wrappedInputFile() {
      if (fallbackInputFile == null) {
        fallbackInputFile = io.newInputFile(location, length);
      }
      return fallbackInputFile;
    }

    /**
     * 返回文件长度。
     *
     * <p>逻辑：优先取缓存项中的长度；其次取已懒加载的 fallbackInputFile 长度； 否则返回构造时传入的 length。
     */
    @Override
    public long getLength() {
      CacheEntry buf = contentCache.getIfPresent(location);
      if (buf != null) {
        return buf.length;
      } else if (fallbackInputFile != null) {
        return fallbackInputFile.getLength();
      } else {
        return length;
      }
    }

    /**
     * 打开新的 {@link SeekableInputStream}。
     *
     * <p>逻辑：若文件长度不超过 maxContentLength 则走 {@link #cachedStream()}（命中缓存或读入 缓存）；否则直接打开底层 InputFile
     * 的流。FileNotFoundException 包装为 NotFoundException， 其他 IOException 包装为 UncheckedIOException。
     *
     * @return ByteBufferInputStream（缓存命中或可缓存时）或底层 SeekableInputStream
     */
    @Override
    public SeekableInputStream newStream() {
      try {
        // read from cache if file length is less than or equal to maximum length allowed to
        // cache.
        if (getLength() <= contentCache.maxContentLength()) {
          return cachedStream();
        }

        // fallback to non-caching input stream.
        return wrappedInputFile().newStream();
      } catch (FileNotFoundException e) {
        throw new NotFoundException(
            e, "Failed to open input stream for file %s: %s", location, e.toString());
      } catch (IOException e) {
        throw new UncheckedIOException(
            String.format("Failed to open input stream for file %s: %s", location, e), e);
      }
    }

    /** 返回文件路径。 */
    @Override
    public String location() {
      return location;
    }

    /**
     * 判断文件是否存在。
     *
     * <p>逻辑：缓存命中即视为存在；否则委托底层 InputFile.exists()。
     */
    @Override
    public boolean exists() {
      CacheEntry buf = contentCache.getIfPresent(location);
      return buf != null || wrappedInputFile().exists();
    }

    /**
     * 将整个文件按 4MB 分块读入 ByteBuffer 列表，构造 CacheEntry。
     *
     * <p>逻辑：通过 wrappedInputFile().newStream() 打开流；循环读取 4MB 块，调用 {@link IOUtil#readRemaining}
     * 读满；若实际读取少于预期则视为遇到 EOF，抛 IOException 触发上层回退；全部读完后构造 CacheEntry 返回。任何 IOException 包装为
     * UncheckedIOException。
     *
     * @return 文件内容对应的 CacheEntry
     */
    private CacheEntry cacheEntry() {
      long start = System.currentTimeMillis();
      try (SeekableInputStream stream = wrappedInputFile().newStream()) {
        long fileLength = getLength();
        long totalBytesToRead = fileLength;
        List<ByteBuffer> buffers = Lists.newArrayList();

        while (totalBytesToRead > 0) {
          // read the stream in 4MB chunk
          int bytesToRead = (int) Math.min(BUFFER_CHUNK_SIZE, totalBytesToRead);
          byte[] buf = new byte[bytesToRead];
          int bytesRead = IOUtil.readRemaining(stream, buf, 0, bytesToRead);
          totalBytesToRead -= bytesRead;

          if (bytesRead < bytesToRead) {
            // Read less than it should be, possibly hitting EOF. Abandon caching by throwing
            // IOException and let the caller fallback to non-caching input file.
            throw new IOException(
                String.format(
                    "Expected to read %d bytes, but only %d bytes read.",
                    fileLength, fileLength - totalBytesToRead));
          } else {
            buffers.add(ByteBuffer.wrap(buf));
          }
        }

        CacheEntry newEntry = new CacheEntry(fileLength, buffers);
        LOG.debug("cacheEntry took {} ms for {}", (System.currentTimeMillis() - start), location);
        return newEntry;
      } catch (IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }

    /**
     * 返回基于缓存的 SeekableInputStream。
     *
     * <p>逻辑：通过 contentCache.get(location, k -> cacheEntry()) 取或加载缓存项；用其 ByteBuffer 列表构造 {@link
     * ByteBufferInputStream}。UncheckedIOException 解包为 IOException； 其他 RuntimeException 包装为
     * IOException。
     *
     * @return 基于 ByteBuffer 列表的 SeekableInputStream
     * @throws IOException 加载缓存失败时抛出
     */
    private SeekableInputStream cachedStream() throws IOException {
      try {
        CacheEntry entry = contentCache.get(location, k -> cacheEntry());
        Preconditions.checkNotNull(
            entry, "CacheEntry should not be null when there is no RuntimeException occurs");
        LOG.debug("Cache stats: {}", contentCache.stats());
        return ByteBufferInputStream.wrap(entry.buffers);
      } catch (UncheckedIOException ex) {
        throw ex.getCause();
      } catch (RuntimeException ex) {
        throw new IOException("Caught an error while reading from cache", ex);
      }
    }
  }
}
