# 提交分析：3867 - Build: Bump datamodel-code-generator

## 提交信息

| 字段 | 值 |
|------|-----|
| 序号 | 3867 |
| 短哈希 | 0c9a23e38 |
| 完整哈希 | 0c9a23e38fbc416469acfc157d7e4d10ac250c9f |
| 日期 | 2026-06-12 10:01:06 -0700 |
| 作者 | Huaxin Gao |
| 提交说明 | Build: Bump datamodel-code-generator from 0.57.0 to 0.59.0 (#16708) |

## 总体目的

将 `datamodel-code-generator` 从 0.57.0 升级到 0.59.0，并使用新版本重新生成 REST Catalog Open API 的 Python 模型代码。

## 修改详情

### 1. 依赖版本升级

**文件路径**: `open-api/requirements.txt`

```diff
-datamodel-code-generator==0.57.0
+datamodel-code-generator==0.59.0
```

### 2. 重新生成 Python 模型代码

**文件路径**: `open-api/rest-catalog-open-api.py`

使用新版本生成器重新生成 Pydantic 模型。主要变化包括：

- `EncryptedKey` 模型的 `encrypted_key_metadata` 字段新增 `json_schema_extra={'contentEncoding': 'base64'}`，在 JSON Schema 中显式标注 base64 编码
- `Summary` 模型新增 `model_config = ConfigDict(extra='allow')` 和 `__pydantic_extra__` 字段，允许额外的键值对（因为 commit summary 可以包含任意自定义属性）

## 依赖升级类提交说明

此提交属于依赖升级类，使用新版本的 `datamodel-code-generator` 重新生成 REST Catalog Open API 的 Python 模型代码。升级跨越了两个次版本（0.57 → 0.59），生成器的输出格式有所改进，主要体现在 JSON Schema 元数据标注和模型配置方面。

## 总结

常规的依赖升级提交，将代码生成工具更新到最新版本并重新生成相关代码。生成的代码变化反映了新版本生成器对 Pydantic 模型 schema 的更完整表达。
