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
package org.apache.iceberg.util;

import org.apache.iceberg.expressions.Literal;
import org.apache.iceberg.relocated.com.google.common.base.Preconditions;

/**
 * Unicode 字符串工具类：为 string 类型的截断转换提供按 Unicode 码点（code point）截断的能力， 保证截断结果仍是合法的 Unicode
 * 字符串（不会从代理对中间切断）。
 *
 * <p>所属模块：iceberg-api。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>判断字符是否为高代理项（high-surrogate）。
 *   <li>按码点数量截断字符串，避免破坏代理对。
 *   <li>计算截断后用于下界/上界判定的最小/最大字符串字面量，支撑分区裁剪的范围推导。
 * </ul>
 *
 * <p>设计意图：
 *
 * <ul>
 *   <li>JDK 的 {@code String.length()} 按 UTF-16 code unit 计数，直接按长度截断可能把一个
 *       补充字符（由代理对表示）切成两半导致非法字符串。本类按 codePointCount 计数并使用 {@code offsetByCodePoints}
 *       定位，保证截断点落在完整字符边界。
 *   <li>{@link #truncateStringMax} 从末尾逐码点自增寻找上界，处理溢出/非法码点跳过，全不可行 时返回 null。
 * </ul>
 *
 * <p>上下游关系：被 core 模块的 string 截断转换及统计信息裁剪逻辑调用。
 */
public class UnicodeUtil {
  // not meant to be instantiated
  private UnicodeUtil() {}

  /**
   * 判断给定字符是否为 Unicode 高代理项码元（范围 0xD800 - 0xDBFF）。
   *
   * @param ch 待判断字符
   * @return 若为高代理项则返回 true
   */
  public static boolean isCharHighSurrogate(char ch) {
    return (ch & '\uFC00') == '\uD800'; // 0xDC00 - 0xDFFF shouldn't match
  }

  /**
   * 按码点数量截断字符串，保证截断结果是合法 Unicode 字符串。
   *
   * <p>逻辑：校验 length 为正；统计输入的码点数，若码点数小于等于 length 则原样返回； 否则用 {@code offsetByCodePoints} 定位到第 length
   * 个码点后的偏移，截取前缀返回。
   *
   * @param input 待截断的字符序列
   * @param length 截断后的最大码点数，必须为正
   * @return 截断后的字符序列
   */
  public static CharSequence truncateString(CharSequence input, int length) {
    Preconditions.checkArgument(length > 0, "Truncate length should be positive");
    StringBuilder sb = new StringBuilder(input);
    // Get the number of unicode characters in the input
    int numUniCodeCharacters = sb.codePointCount(0, sb.length());
    // No need to truncate if the number of unicode characters in the char sequence is <= truncate
    // length
    if (length >= numUniCodeCharacters) {
      return input;
    }
    // Get the offset in the input charSequence where the number of unicode characters = truncate
    // length
    int offsetByCodePoint = sb.offsetByCodePoints(0, length);
    return input.subSequence(0, offsetByCodePoint);
  }

  /**
   * 返回一个码点数不超过 length 且小于等于输入的合法 Unicode 字符串字面量，作为截断后的下界。
   *
   * @param input 输入字符串字面量
   * @param length 截断长度
   * @return 截断后的下界字面量
   */
  public static Literal<CharSequence> truncateStringMin(Literal<CharSequence> input, int length) {
    // Truncate the input to the specified truncate length.
    CharSequence truncatedInput = truncateString(input.value(), length);
    return Literal.of(truncatedInput);
  }

  /**
   * 返回一个码点数不超过 length 且大于输入的合法 Unicode 字符串字面量，作为截断后的上界。
   *
   * <p>逻辑：先截断输入到 length 个码点；若截断后长度等于原长度（即未实际截断）则直接返回 原输入；否则从末尾逐码点自增，若自增后码点合法且未溢出则截断到该码点位置并追加新码点
   * 作为上界返回；若所有码点都不可行则返回 null。
   *
   * @param input 输入字符串字面量
   * @param length 截断长度
   * @return 截断后的上界字面量；若无有效上界则返回 null
   */
  public static Literal<CharSequence> truncateStringMax(Literal<CharSequence> input, int length) {
    CharSequence inputCharSeq = input.value();
    // Truncate the input to the specified truncate length.
    StringBuilder truncatedStringBuilder = new StringBuilder(truncateString(inputCharSeq, length));

    // No need to increment if the input length is under the truncate length
    if (inputCharSeq.length() == truncatedStringBuilder.length()) {
      return input;
    }

    // Try incrementing the code points from the end
    for (int i = length - 1; i >= 0; i--) {
      // Get the offset in the truncated string buffer where the number of unicode characters = i
      int offsetByCodePoint = truncatedStringBuilder.offsetByCodePoints(0, i);
      int nextCodePoint = truncatedStringBuilder.codePointAt(offsetByCodePoint) + 1;
      // No overflow
      if (nextCodePoint != 0 && Character.isValidCodePoint(nextCodePoint)) {
        truncatedStringBuilder.setLength(offsetByCodePoint);
        // Append next code point to the truncated substring
        truncatedStringBuilder.appendCodePoint(nextCodePoint);
        return Literal.of(truncatedStringBuilder.toString());
      }
    }
    return null; // Cannot find a valid upper bound
  }
}
