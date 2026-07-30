# 提交 3676：Docs: Update Oracle vendor description (#16261)

## 提交信息

- **序号**：3676 / 4088
- **哈希**：c1477969731e9c65b0ee3a57176f3baa9ff5c61f
- **短哈希**：c14779697
- **日期**：2026-05-09 10:25:03 -0700
- **作者**：Alex Miller
- **提交说明**：Docs: Update Oracle vendor description (#16261)
- **PR/Issue**：#16261

## 总体目的

这个提交是一个纯粹的文档更新，目的是修改 Iceberg 官方网站 vendors 页面中关于 Oracle 厂商的描述文字。原来的描述需要根据 Oracle 产品策略和定位的最新变化进行修订，使其更准确地反映 Oracle 在 Iceberg 生态中的角色和产品能力。

具体来说，Oracle 希望更明确地将其 Autonomous AI Lakehouse 定位为一个完全托管的服务（fully-managed Oracle AI Database service），并强调其在多云（multicloud）之外还支持混合云（hybrid）部署，包括本地部署（on-premises）的能力。此外，原文中"Exadata"被调整为"Oracle Exadata"，进一步明确这是 Oracle 自家的产品。

## 如何达成设计目的

通过修改 `site/docs/vendors.md` 文件中 Oracle 厂商段落的描述文字，对原文进行少量字词调整和增补。修改保持了原有的整体段落结构，仅在描述细节上进行修订，使其与 Oracle 当前产品定位和市场策略保持一致。

## 修改详情

### `site/docs/vendors.md` (+1/-1 lines)

**修改目的**：更新 Oracle 厂商在 Iceberg 生态中的产品描述文字。

**工作逻辑**：

修改点主要包括：

1. **明确服务定位**：在段落开头增加了"As a fully-managed Oracle AI Database service,"的前缀，明确 Oracle Autonomous AI Lakehouse 是基于 Oracle AI Database 的完全托管服务。

2. **产品名称完整化**：将原文"Oracle Autonomous Database and Exadata"修改为"Oracle Autonomous Database and Oracle Exadata"，将"Exadata"前面加上"Oracle"以明确这是 Oracle 的产品。

3. **扩展部署支持范围**：原文描述"Available across Oracle Cloud Infrastructure (OCI), Microsoft Azure, Google Cloud, and AWS"修改为"Available across Oracle Cloud Infrastructure (OCI), Microsoft Azure, Google Cloud, AWS, and on-premises"，增加了本地部署支持。

4. **明确架构定位**：将"a multicloud, open lakehouse architecture"修改为"a multicloud and hybrid open lakehouse architecture"，增加了"hybrid"（混合云）定位，与新增加的本地部署支持相呼应。

## 总结

这是一个轻量级的文档维护提交，目的是保持 Iceberg 官方文档中 Oracle 厂商描述与 Oracle 最新产品策略的一致性。修改增加了 Oracle 在混合云和本地部署能力的描述，进一步明确了其 Autonomous AI Lakehouse 作为完全托管服务的定位。这种厂商描述的更新对于 Iceberg 项目的生态系统展示具有重要意义，确保各厂商在文档中的描述准确反映其当前能力。
