---
title: MCP 身份透传设计
sidebar_position: 4
description: SkillHub 与飞书 Aily 身份通过 ContextForge 无感透传到上游 MCP 的实现指导
---

# MCP 身份透传设计

本文定义 SkillHub、飞书 Aily 与 ContextForge 集成时的统一身份透传方案。目标是让用户只在入口侧完成一次认证，后续上游 MCP Server 可以无感读取 `userId`、`openId` 等业务身份字段。

## 目标

- SkillHub API Token 可以直接作为 MCP 调用凭据，不要求用户再到 ContextForge 后台单独创建 token。
- 飞书 Aily 调用 MCP 时，可以通过可信 header 携带用户邮箱和 `openId`，并被 ContextForge 解析成统一身份。
- ContextForge 在 `http_auth_resolve_user` 完成一次认证和身份解析，后续工具调用、资源读取、prompt 获取等链路复用同一个用户身份。
- 上游 MCP Server 优先通过 identity headers 或 MCP `_meta` 获取身份，不依赖每个工具单独增加 `userId` 参数。

## 当前限制

ContextForge 默认 identity propagation 只透传标准用户字段。即使自定义插件在认证阶段解析出了 `userId` 或 `openId`，这些扩展字段也不会自动传给上游 MCP。

当前默认链路如下：

```text
http_auth_resolve_user
  -> 返回 email/full_name/is_active 等用户字典
  -> ContextForge 解析为 EmailUser
  -> _inject_userinfo_instate 构建 UserContext
  -> build_identity_headers/build_identity_meta 输出固定字段
```

默认输出字段包括：

- `id`
- `email`
- `full_name`
- `groups`
- `teams`
- `roles`
- `is_admin`
- `auth_method`
- `service_account`
- `delegation_chain`

默认不会输出：

- SkillHub `userId`
- 飞书 `openId`
- `rolesId`
- 其他业务扩展字段

因此，不能只把 `userId/openId` 放进认证插件返回值里就期待自动透传。需要显式扩展 ContextForge 的身份映射和序列化逻辑。

## 目标架构

```text
MCP Client / 飞书 Aily
  |
  | Authorization: Bearer <SkillHub token>
  | 或 x-feishu-email / x-feishu-open-id / x-aily-signature
  v
ContextForge http_auth_resolve_user
  |
  | 1. 验证 SkillHub token 或 Aily 签名
  | 2. 解析 email、skillhub_user_id、feishu_open_id
  | 3. 构建 ContextForge UserContext
  v
ContextForge identity propagation
  |
  | headers / _meta / both
  v
上游 MCP Server
```

## ContextForge 修改点

### 1. 认证插件返回扩展身份

自定义认证插件负责支持多个身份来源：

- SkillHub Token：从 `Authorization: Bearer <token>` 或 `x-skillhub-token` 读取，并调用 SkillHub introspection 接口校验。
- 飞书 Aily：从 `x-feishu-email`、`x-feishu-open-id` 读取，并校验可信来源或 HMAC 签名。
- ContextForge 原生 token：非 SkillHub/飞书请求继续交给 ContextForge 标准 JWT/API Token 认证。

认证成功时，插件返回标准用户字段，并在扩展字段中携带业务身份：

```python
return PluginResult(
    modified_payload={
        "email": identity.email,
        "full_name": identity.full_name,
        "is_active": True,
        "auth_provider": identity.source,
        "attributes": {
            "skillhub_user_id": identity.skillhub_user_id,
            "feishu_open_id": identity.feishu_open_id,
            "roles_id": identity.roles_id,
        },
    },
    metadata={
        "auth_method": identity.auth_method,
    },
    continue_processing=False,
)
```

安全要求：

- SkillHub Token 必须服务端校验，不允许仅按 token 前缀或本地格式判断。
- 飞书 Aily header 必须来自可信网络边界，或带签名与时间戳，例如 `x-aily-signature`、`x-aily-timestamp`。
- 明确属于当前认证方式但校验失败时，应拒绝请求；不属于当前认证方式时才允许 `continue_processing=True`。

### 2. 保留插件返回的 attributes

修改 ContextForge 认证链，让 `http_auth_resolve_user` 返回的 `attributes` 不被丢弃。

建议在解析 plugin user dict 时保留扩展字段，并在 `_inject_userinfo_instate` 构建 `UserContext` 时写入：

```python
global_context.user_context = UserContext(
    user_id=user.email,
    email=user.email,
    full_name=user.full_name,
    is_admin=user.is_admin,
    teams=token_teams if isinstance(token_teams, list) else None,
    team_id=team_id,
    auth_method=auth_method,
    authenticated_at=datetime.now(timezone.utc),
    attributes=request.state.auth_attributes,
)
```

实现方式可以二选一：

- 在 `request.state.auth_attributes` 保存 plugin 返回的扩展字段。
- 扩展 `_resolve_plugin_authenticated_user_sync` 的返回结构，使其同时返回 `EmailUser` 和 `attributes`。

推荐第一种，改动面更小，也避免污染 `EmailUser` 模型。

### 3. 扩展 identity propagation 序列化

修改 `build_identity_headers()` 和 `build_identity_meta()`，支持从 `UserContext.attributes` 透传白名单字段。

建议增加配置：

```env
IDENTITY_PROPAGATION_ENABLED=true
IDENTITY_PROPAGATION_MODE=both
IDENTITY_PROPAGATION_HEADERS_PREFIX=X-Forwarded-User
IDENTITY_PROPAGATION_ATTRIBUTE_ALLOWLIST=skillhub_user_id,feishu_open_id,roles_id
```

Header 输出示例：

```http
X-Forwarded-User-Id: user@example.com
X-Forwarded-User-Email: user@example.com
X-Forwarded-User-Auth-Method: skillhub_token
X-Forwarded-User-Attr-Skillhub-User-Id: 12345
X-Forwarded-User-Attr-Feishu-Open-Id: ou_xxx
X-Forwarded-User-Attr-Roles-Id: 1,2,3
```

MCP `_meta` 输出示例：

```json
{
  "user": {
    "id": "user@example.com",
    "email": "user@example.com",
    "auth_method": "skillhub_token",
    "attributes": {
      "skillhub_user_id": "12345",
      "feishu_open_id": "ou_xxx",
      "roles_id": "1,2,3"
    }
  }
}
```

不要默认透传全部 attributes。扩展字段必须经过 allowlist 过滤，避免把 token、手机号、内部权限对象等敏感信息泄露给所有上游 MCP。

## SkillHub 修改点

SkillHub 需要提供一个服务端 introspection 接口给 ContextForge 插件调用。

建议接口：

```http
POST /internal/mcp/token/introspect
Authorization: Bearer <service-token>
Content-Type: application/json
```

请求体：

```json
{
  "token": "skillhub_token_value",
  "clientIp": "10.0.0.1",
  "mcpServer": "bdc-query"
}
```

响应体：

```json
{
  "active": true,
  "userId": "12345",
  "email": "user@example.com",
  "username": "user",
  "realname": "User Name",
  "rolesId": "1,2,3",
  "roleNames": ["USER"],
  "scopes": ["mcp:call"],
  "expireTime": "2026-07-25T00:00:00Z"
}
```

校验要求：

- token 必须存在、未撤销、未过期。
- token scope 必须允许 MCP 调用。
- 如果要按 MCP Server 授权，需校验 token 是否允许访问请求中的 `mcpServer`。
- 接口只允许 ContextForge 服务账号或内网访问。

## 飞书 Aily 接入要求

飞书 Aily 请求至少包含：

```http
X-Feishu-Email: user@example.com
X-Feishu-Open-Id: ou_xxx
X-Aily-Timestamp: 1780000000
X-Aily-Signature: hmac-sha256(...)
```

ContextForge 插件处理要求：

- 校验 timestamp 时间窗口，避免重放。
- 校验 signature，签名内容至少包含 method、path、timestamp、email、openId。
- 将 email 作为 ContextForge 标准用户身份。
- 将 `openId` 写入 `attributes.feishu_open_id`。
- 如果需要平台 `userId`，用 email 或 openId 调 SkillHub/用户中心做映射。

不要在公网边界直接信任 `x-feishu-email` 或 `x-feishu-open-id`。

## `tool_pre_invoke` 的定位

完成上述改造后，`tool_pre_invoke` 不再负责认证，也不再作为主要身份透传机制。

只在以下兼容场景使用：

- 某些旧 MCP 工具只能从 tool arguments 读取 `userId`。
- 上游不是 HTTP/MCP `_meta` 可见路径。
- 需要对特定 Virtual Server 或特定工具做临时字段补齐。

如果所有上游 MCP 都能读取 identity headers 或 `_meta`，则不需要 `tool_pre_invoke` 参与身份透传。

## 验收用例

### SkillHub Token

1. 在 SkillHub 创建 API Token。
2. 使用该 token 调用 ContextForge MCP endpoint。
3. ContextForge 通过 `http_auth_resolve_user` 调 SkillHub introspection 成功。
4. 上游 MCP Server 在 header 或 `_meta` 中读到：
   - email
   - auth_method
   - skillhub_user_id
   - roles_id
5. 撤销 SkillHub token 后，同一个 MCP 请求返回 401。

### 飞书 Aily

1. 飞书 Aily 请求携带 email、openId、timestamp、signature。
2. ContextForge 校验签名成功。
3. 上游 MCP Server 读到：
   - email
   - auth_method
   - feishu_open_id
   - skillhub_user_id（如果配置了映射）
4. 修改 email/openId 或 timestamp 后，请求被拒绝。

### 原生 ContextForge Token

1. 使用 ContextForge 原生 JWT/API token 调用 MCP。
2. 自定义插件不误拦截。
3. 标准 ContextForge 认证链仍可正常工作。

## 实施顺序

1. 在 SkillHub 增加 token introspection 接口。
2. 在 ContextForge 增加统一身份认证插件，支持 SkillHub Token 和飞书 Aily header。
3. 在 ContextForge auth 链路保留 plugin 返回的 `attributes`。
4. 在 identity propagation 中增加 attributes allowlist 序列化。
5. 启用 `IDENTITY_PROPAGATION_ENABLED=true` 和 `IDENTITY_PROPAGATION_MODE=both`。
6. 用一个测试 MCP Server 回显收到的 headers 和 `_meta`，完成端到端验证。

## 推荐默认配置

```env
IDENTITY_PROPAGATION_ENABLED=true
IDENTITY_PROPAGATION_MODE=both
IDENTITY_PROPAGATION_HEADERS_PREFIX=X-Forwarded-User
IDENTITY_PROPAGATION_ATTRIBUTE_ALLOWLIST=skillhub_user_id,feishu_open_id,roles_id
IDENTITY_SIGN_CLAIMS=true
```

`both` 是推荐默认值，因为它同时兼容 HTTP header 读取和 MCP `_meta` 读取。等所有上游 MCP Server 的身份读取方式统一后，再收敛到 `headers` 或 `meta`。
