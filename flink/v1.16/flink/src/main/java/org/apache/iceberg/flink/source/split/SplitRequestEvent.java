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

/** We can remove this class once FLINK-21364 is resolved. */
@Internal
/**
 * 分片请求事件，reader 向 enumerator 请求新分片时发送。
 *
 * <p>所属模块：iceberg-flink v1.15。职责：封装请求信息，实现 Flink SourceEvent。
 *
 * <p>设计意图：事件对象；被 SourceReader 发送、enumerator 接收。
 */
public class SplitRequestEvent implements SourceEvent {
  private static final long serialVersionUID = 1L;

  private final Collection<String> finishedSplitIds;
  private final String requesterHostname;

  public SplitRequestEvent() {
    this(Collections.emptyList());
  }

  public SplitRequestEvent(Collection<String> finishedSplitIds) {
    this(finishedSplitIds, null);
  }

  public SplitRequestEvent(Collection<String> finishedSplitIds, String requesterHostname) {
    this.finishedSplitIds = finishedSplitIds;
    this.requesterHostname = requesterHostname;
  }

  public Collection<String> finishedSplitIds() {
    return finishedSplitIds;
  }

  public String requesterHostname() {
    return requesterHostname;
  }
}
