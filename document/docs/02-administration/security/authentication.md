---
title: 认证配置
sidebar_position: 1
description: 配置用户认证方式
---

# 认证配置

SkillHub 支持多种认证方式，满足不同企业的安全需求。

## OAuth2 登录

### GitHub OAuth

1. 在 GitHub 创建 OAuth App
2. 配置环境变量：
   ```bash
   OAUTH2_GITHUB_CLIENT_ID=your-client-id
   OAUTH2_GITHUB_CLIENT_SECRET=your-client-secret
   ```

### Feishu OAuth

1. 在飞书开放平台创建应用，获取 App ID 和 App Secret。
2. 在飞书应用安全设置中配置回调地址：
   `https://<skillhub-domain>/login/oauth2/code/feishu`
3. 配置 SkillHub 后端环境变量：
   ```bash
   OAUTH2_FEISHU_CLIENT_ID=cli_xxx
   OAUTH2_FEISHU_CLIENT_SECRET=xxx
   OAUTH2_FEISHU_DISPLAY_NAME=Feishu
   OAUTH2_FEISHU_SCOPE=auth:user.id:read,contact:user.email:readonly
   ```

SkillHub 会把飞书用户绑定为 `provider=feishu`。稳定用户标识优先使用
`tenant_key + union_id`，没有 `union_id` 时使用 `tenant_key + open_id`，不会按邮箱自动合并账号。
当前阶段只支持标准 OAuth 跳转登录，登录按钮会跳转到 `/oauth2/authorization/feishu`。

### 扩展 OAuth Provider

架构支持扩展其他 OAuth Provider，如 GitLab、Gitee 等。

## 本地账号登录

开发环境支持本地账号登录，生产环境默认关闭。

## 企业 SSO 集成

支持通过扩展点集成企业 SSO（SAML/OIDC）。

## 下一步

- [权限管理](./authorization) - 配置权限控制
