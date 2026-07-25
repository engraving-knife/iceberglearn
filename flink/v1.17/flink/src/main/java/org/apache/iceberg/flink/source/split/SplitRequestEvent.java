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
package org.apache.iceberg.flink.source.split;

import java.util.Collection;
import java.util.Collections;
import org.apache.flink.annotation.Internal;
import org.apache.flink.api.connector.source.SourceEvent;

/**
 * 文件级说明：reader 向协调器请求更多 split 的 SourceEvent。
 *
 * <p>所属模块：iceberg-flink v1.17（Iceberg 与 Flink v1.17 集成模块的 source/split 子包）。
 *
 * <p>职责：携带已完成的 split ID 列表与请求者 hostname，向协调器表明需要新的 split。
 *
 * <p>设计意图：Flink 内置的 SourceEvent 不支持 split 请求细节， 因此自定义该事件；待 FLINK-21364 解决后可移除。
 *
 * <p>上下游关系：上游为 {@link org.apache.iceberg.flink.source.reader.IcebergSourceReader}
 * 发送事件，下游为协调器（{@code SplitAssigner}）接收并分配。
 */
@Internal
public class SplitRequestEvent implements SourceEvent {
  private static final long serialVersionUID = 1L;

  private final Collection<String> finishedSplitIds;
  private final String requesterHostname;

  /** 构造无已完成 split 的事件（启动时使用）。 */
  public SplitRequestEvent() {
    this(Collections.emptyList());
  }

  /** 构造只携带已完成 split ID 的事件。 */
  public SplitRequestEvent(Collection<String> finishedSplitIds) {
    this(finishedSplitIds, null);
  }

  /** 构造携带已完成 split ID 与请求者 hostname 的事件。 */
  public SplitRequestEvent(Collection<String> finishedSplitIds, String requesterHostname) {
    this.finishedSplitIds = finishedSplitIds;
    this.requesterHostname = requesterHostname;
  }

  /** 返回已完成的 split ID 集合。 */
  public Collection<String> finishedSplitIds() {
    return finishedSplitIds;
  }

  /** 返回请求者 hostname，便于协调器做亲和性调度。 */
  public String requesterHostname() {
    return requesterHostname;
  }
}
