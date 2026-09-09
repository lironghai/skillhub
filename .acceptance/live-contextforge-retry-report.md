# Live ContextForge Retry

Date: 2026-09-09

## Result

**PASS for authentication and the requested partial query.**

- Base URL: `https://skmcp-bdc.yingxiong.com/contextforge`
- Login: `POST /auth/email/login` returned an access token (credential omitted).
- Requested endpoint: `GET /admin/servers/partial?page=1&per_page=10&include_inactive=true&team_id=92a2948634a14f84b1f68db02d95ab7d`
- HTTP status: `200`
- Rendered result: 1 server row; its HTML `data-team-id` equals the requested team ID.
- No ContextForge source, configuration, or data was changed.

## SkillHub client path

The checked-in SkillHub client builds:

`GET /admin/servers?include_inactive=false&page=1&per_page=100&team_id=<configured team ID>`

The live request returned HTTP `200` and 8 records. The response included records from more than one team, so the `team_id` query parameter is not sufficient to claim server-side filtering on this JSON endpoint. The SkillHub client then filters records by `team_id` before exposing them; this is consistent with the current implementation and prevents cross-team records from being returned.

## Conclusion

Authentication and the ContextForge UI partial endpoint passed. The client endpoint is reachable and the client-side team filter is required because the live JSON response was mixed-team.
