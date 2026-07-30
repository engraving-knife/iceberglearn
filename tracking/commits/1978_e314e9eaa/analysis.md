# 提交 1978：Core: Drop invalid function comment for HTTPClient.isSuccessful (#12742)

## 提交信息

- **序号**：1978 / 4088
- **哈希**：e314e9eaac3939ce248bd96b5e2c82b2b1970ea5
- **短哈希**：e314e9eaa
- **日期**：2025-04-09 19:38:31 +0200
- **作者**：gaborkaszab
- **提交说明**：Core: Drop invalid function comment for HTTPClient.isSuccessful (#12742)
- **PR/Issue**：#12742

## 总体目的

本提交移除 `HTTPClient.isSuccessful` 方法上一条与实现不一致的注释，消除误导性文档。

原注释为 `// Per the spec, the only currently defined / used "success" responses are 200 and 202.`，声称"成功"响应只有 200 和 202 两种。但方法实际实现判断的成功状态码包括三个：`SC_OK`(200)、`SC_ACCEPTED`(202) 以及 `SC_NO_CONTENT`(204)。注释遗漏了 204，与代码不符，属于无效/错误注释。保留这样的注释会让维护者误以为 204 不应被视为成功，从而在修改时做出错误判断。删除该注释是最稳妥的处理方式。

## 如何达成设计目的

直接删除该行注释，让方法实现自解释。

## 修改详情

### `core/src/main/java/org/apache/iceberg/rest/HTTPClient.java` (修改, +0/-1 lines)

**修改目的**：移除与实现不一致的注释。

**工作逻辑**：删除 `isSuccessful(CloseableHttpResponse)` 方法上方的 `// Per the spec, the only currently defined / used "success" responses are 200 and 202.` 注释行。方法本体（判断 200/202/204 为成功）保持不变。

## 总结

文档/注释清理提交，删除 `HTTPClient.isSuccessful` 上一条错误描述（声称仅 200/202 为成功，实际还包含 204）的注释，避免误导。仅删除一行注释，无代码逻辑变更。
