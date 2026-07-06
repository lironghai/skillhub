---
title: Authentication Configuration
sidebar_position: 1
description: Configure user authentication methods
---

# Authentication Configuration

SkillHub supports multiple authentication methods to meet different enterprise security requirements.

## OAuth2 Login

### GitHub OAuth

1. Create an OAuth App on GitHub
2. Configure environment variables:
   ```bash
   OAUTH2_GITHUB_CLIENT_ID=your-client-id
   OAUTH2_GITHUB_CLIENT_SECRET=your-client-secret
   ```

### Feishu OAuth

1. Create an app in the Feishu Open Platform and copy the App ID and App Secret.
2. Add the SkillHub callback URL in the Feishu app security settings:
   `https://<skillhub-domain>/login/oauth2/code/feishu`
3. Configure backend environment variables:
   ```bash
   OAUTH2_FEISHU_CLIENT_ID=cli_xxx
   OAUTH2_FEISHU_CLIENT_SECRET=xxx
   OAUTH2_FEISHU_DISPLAY_NAME=Feishu
   OAUTH2_FEISHU_SCOPE=auth:user.id:read,contact:user.email:readonly
   ```

SkillHub binds Feishu users with `provider=feishu`. The stable subject prefers
`tenant_key + union_id`, falls back to `tenant_key + open_id`, and accounts are not
automatically merged by email. Phase 1 supports only the standard OAuth redirect button to
`/oauth2/authorization/feishu`.

### Extend OAuth Provider

The architecture supports extending to other OAuth providers like GitLab, Gitee, etc.

## Local Account Login

Local account login is supported in development environment, disabled by default in production.

## Enterprise SSO Integration

Supports integrating enterprise SSO (SAML/OIDC) through extension points.

## Next Steps

- [Authorization](./authorization) - Configure access control
