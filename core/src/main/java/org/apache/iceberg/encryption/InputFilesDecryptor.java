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
package org.apache.iceberg.encryption;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.iceberg.CombinedScanTask;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;
import org.apache.iceberg.relocated.com.google.common.collect.Maps;

/**
 * 文件级说明：扫描任务输入文件批量解密器。
 *
 * <p>所属模块：iceberg-core（加密包），在读取扫描任务时把任务涉及的所有加密文件一次性批量解密， 缓存为按位置索引的明文 {@link InputFile} 映射。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>收集 {@link CombinedScanTask} 中所有数据文件与删除文件的密钥元数据。
 *   <li>调用 {@link EncryptionManager#decrypt(Iterable)} 批量解密，减少 KMS 往返。
 *   <li>按文件位置缓存解密后的 {@link InputFile}，供后续按任务或位置查询。
 * </ul>
 *
 * <p>设计意图：批量解密是关键优化——把同一扫描任务中的多个文件交给加密管理器一次性处理， 使 envelope 加密实现可批量预取密钥，避免对 KMS 的多次 RPC。结果以不可变 Map
 * 缓存， 避免重复解密。
 *
 * <p>上下游关系：由扫描读取路径在构造任务执行上下文时创建；被读取端通过 {@link #getInputFile} 获取已解密的输入文件。
 */
public class InputFilesDecryptor {

  private final Map<String, InputFile> decryptedInputFiles;

  /**
   * 构造解密器并立即批量解密任务中的所有文件。
   *
   * <p>逻辑：
   *
   * <ol>
   *   <li>遍历 {@link CombinedScanTask#files()}，把每个 {@link FileScanTask} 的数据文件与其删除文件 汇总到“位置 →
   *       密钥元数据”映射（同位置去重）。
   *   <li>对每个映射项用 {@link FileIO#newInputFile} 创建原始输入文件，包装为 {@link EncryptedInputFile}。
   *   <li>调用 {@link EncryptionManager#decrypt(Iterable)} 做批量解密（若实现支持可批量预取密钥）。
   *   <li>把解密结果按位置存入不可变 Map 缓存。
   * </ol>
   *
   * @param combinedTask 组合扫描任务
   * @param io 用于创建原始输入文件的 FileIO
   * @param encryption 加密管理器，负责实际解密
   */
  public InputFilesDecryptor(
      CombinedScanTask combinedTask, FileIO io, EncryptionManager encryption) {
    Map<String, ByteBuffer> keyMetadata = Maps.newHashMap();
    combinedTask.files().stream()
        .flatMap(
            fileScanTask ->
                Stream.concat(Stream.of(fileScanTask.file()), fileScanTask.deletes().stream()))
        .forEach(file -> keyMetadata.put(file.path().toString(), file.keyMetadata()));
    Stream<EncryptedInputFile> encrypted =
        keyMetadata.entrySet().stream()
            .map(
                entry ->
                    EncryptedFiles.encryptedInput(
                        io.newInputFile(entry.getKey()), entry.getValue()));

    // decrypt with the batch call to avoid multiple RPCs to a key server, if possible
    @SuppressWarnings("StreamToIterable")
    Iterable<InputFile> decryptedFiles = encryption.decrypt(encrypted::iterator);

    Map<String, InputFile> files = Maps.newHashMapWithExpectedSize(keyMetadata.size());
    decryptedFiles.forEach(decrypted -> files.putIfAbsent(decrypted.location(), decrypted));
    this.decryptedInputFiles = Collections.unmodifiableMap(files);
  }

  /**
   * 按扫描任务获取已解密的输入文件。
   *
   * @param task 文件扫描任务
   * @return 对应的已解密 {@link InputFile}
   * @throws IllegalArgumentException 若 task 是数据任务（非文件任务）
   */
  public InputFile getInputFile(FileScanTask task) {
    Preconditions.checkArgument(!task.isDataTask(), "Invalid task type");
    return decryptedInputFiles.get(task.file().path().toString());
  }

  /** 按文件位置获取已解密的输入文件。 */
  public InputFile getInputFile(String location) {
    return decryptedInputFiles.get(location);
  }
}
