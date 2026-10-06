# 1Panel 运行时版本探测调研（供 Android 客户端「自动」模式使用）

- **调研对象**：`1Panel-dev/1Panel`（社区版源码）、官方文档站、GitHub Branches/Tags/Releases、社区客户端与服务端兼容实现
- **数据时点**：**2026-10-05**（仓库最新 release tag：`v2.3.2`，发布日 2026-09-24；最新 1.x LTS tag：`v1.10.34-lts`）
- **取证方式**：`curl` 直拉各 tag 的 raw 源码 / `swagger.json` / 官方文档页，逐条对照（见文末「来源链接」，全部链接均为本次实际抓取地址）
- **本文回答**：① 有没有「一次请求拿到版本号」的接口 ② 没有的话最少请求的有序探测方案 ③ v3/v4 是否真实存在 ④ 预留位怎么设计 ⑤ 社区怎么做的

---

## 0. 结论速览（先给答案）

| # | 问题 | 结论 |
|---|------|------|
| 1 | 能否**一个固定路径、一次请求**同时拿到 v1/v2 面板版本号？ | **不能。** v1 与 v2 的 API 前缀不同（`/api/v1` vs `/api/v2/core`），且**错线请求不会返回 JSON 404**，而是返回状态码可配置的 **HTML 错误页**；此外 1Panel 的业务/鉴权错误 **HTTP 状态码恒为 200**，错误码在 JSON 外壳的 `code` 字段（详见 §1.5） |
| 2 | 每条版本线**各自**能否一次请求拿到版本号？ | **能，但都是 POST 且需要 API 密钥（或登录态）**：<br>• v1 线：`POST /api/v1/settings/search` → `data.systemVersion`<br>• v2 线：`POST /api/v2/core/settings/search` → `data.systemVersion`（另有 `POST /api/v2/core/settings/search/base`，≥ v2.2.1） |
| 3 | 任务里给的 `GET /api/v1/settings/search` 对吗？ | **不对。路由是 POST**。源码中 `router.POST("/search", baseApi.GetSettingInfo)`；`GET /api/v1/settings/search` 会落到 NoRoute 的 HTML 错误页（Gin 默认不返回 405） |
| 4 | 版本号字段到底叫什么？ | `systemVersion`（JSON 小写开头驼峰），属于 `dto.SettingInfo.SystemVersion`（v2 的 `dto.SettingBaseInfo` 也有），值形如 `1.10.33-lts` / `2.3.2` |
| 5 | 需要额外权限吗？ | **需要**：`ApiInterfaceStatus=enable` + `IpWhiteList` 命中客户端 IP + 正确签名（见 §1.4）。免认证接口**只能判断版本线，拿不到版本号** |
| 6 | 「自动」模式最少请求数 | **冷启动 2 次**：1 次免密钥版本线判别（`GET /health`）+ 1 次带密钥取版本号；**命中缓存 / 已知版本线后 1 次**。（若 `/health` 被反代拦截，最坏 3 次） |
| 7 | v3 / v4 存在吗？ | **截至 2026-10-05 不存在**。无 `v3*`/`v4*` 分支或 tag、无 v3/v4 Release；官网文档只有 v1、v2。`1Panel AI 网关`、`1Panel AI 一体机`、`1Panel Pro` 是**独立产品线**，不是面板 v3/v4（详见 §3） |
| 8 | 有没有「保留 v1/v2/v3/v4 选项」的坑？ | **有，而且很大**：官方 tag `v1.10.34-lts` 当前指向的是 **dev-v2 分支**的提交（`core/` 布局、`/api/v2/core`、无 `/health`，`frontend/package.json` 版本号写的是 `2.0`）。**版本号字符串与 API 方言不能划等号**，必须探测（详见 §1.6） |

---

## 1. 源码取证

### 1.1 v1 线（1.0.0 ~ 1.10.33-lts，仓库布局 `backend/`）

| 项目 | 事实 | 源码依据 |
|------|------|----------|
| API 前缀 | `/api/v1`（`PrivateGroup := Router.Group("/api/v1")`） | [backend/init/router/router.go@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/init/router/router.go) |
| 版本接口 | `POST /api/v1/settings/search` → `data.systemVersion` | [backend/router/ro_setting.go@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/router/ro_setting.go)：`router.POST("/search", baseApi.GetSettingInfo)` |
| 中间件链 | `JwtAuth()` + `SessionAuth()`（**没有** `PasswordExpired`，`/search` 注册在 `router` 而非 `settingRouter`） | 同上 |
| 版本字段 | `SystemVersion string \`json:"systemVersion"\`` | [backend/app/dto/setting.go@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/app/dto/setting.go) |
| 字段来源 | `GetSettingInfo()` 把设置表 KV 反序列化进 `dto.SettingInfo`，即 DB 设置项 `SystemVersion` | [backend/app/service/setting.go@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/app/service/setting.go) |
| 官方契约 | `swagger.json` basePath=`/api/v1`，`/settings/search` = **post**，`security: [ApiKeyAuth, Timestamp]`，`dto.SettingInfo` 含 `systemVersion` | [cmd/server/docs/swagger.json@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/cmd/server/docs/swagger.json) |
| **免认证指纹** | `GET /health` → `200` + body `"ok"`（`PublicGroup.GET("/health", ... c.JSON(200, "ok"))`，路由注册在 `/api/v1` 之外） | 同上 router.go |
| 其他免认证接口 | `GET /api/v1/auth/setting` → `{needCaptcha, language}`（**无版本字段**）；`GET /api/v1/auth/intl`、`GET /api/v1/auth/demo` | [backend/router/ro_base.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/router/ro_base.go)、[backend/app/api/v1/auth.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/app/api/v1/auth.go)、[backend/app/dto/auth.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/app/dto/auth.go)（`type LoginSetting struct{NeedCaptcha, Language}`） |
| 老版本兼容验证 | `v1.0.0 / v1.5.0 / v1.9.0 / v1.10.0-lts / v1.10.33-lts` **都**有 `GET("/health")`、`Group("/api/v1")`、`dto.SettingInfo.systemVersion`、`POST /settings/search` | 分别抓取上述 tag 的 `backend/init/router/router.go`、`backend/router/ro_setting.go`、`backend/app/dto/setting.go` 验证（例：[v1.0.0 ro_setting.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.0.0/backend/router/ro_setting.go)） |
| 注意 | `GET /api/v1/auth/setting` 在 **v1.0.0 不存在**（当时是 `/auth/status`、`/auth/init`），所以**不能**把它当作通用 v1 指纹；`/health` 才是 | [backend/router/ro_base.go@v1.0.0](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.0.0/backend/router/ro_base.go) |

### 1.2 v2 线（2.0.0 ~ 2.3.2，仓库布局 `core/` + `agent/`）

| 项目 | 事实 | 源码依据 |
|------|------|----------|
| Core API 前缀 | `/api/v2/core`（`PrivateGroup := Router.Group("/api/v2/core")`）；agent 侧接口走 `/api/v2/...` 由 `Proxy()` 转发 | [core/init/router/router.go@v2.3.2](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/init/router/router.go)、[core/init/router/proxy.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/init/router/proxy.go)、[core/middleware/helper.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/middleware/helper.go)（`ShouldProxyToAgent`） |
| 版本接口 | `POST /api/v2/core/settings/search` → `data.systemVersion`（SessionAuth + PasswordExpired）；`POST /api/v2/core/settings/search/base` → `data.systemVersion`（仅 SessionAuth，**≥ v2.2.1** 才有） | [core/router/ro_setting.go@v2.3.2](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/router/ro_setting.go)；`/search/base` 在 [v2.1.13](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.1.13/core/router/ro_setting.go) 不存在、[v2.2.1](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.2.1/core/router/ro_setting.go) 起存在 |
| v2.0.x 起就固定 | v2.0.0 / v2.0.9 / v2.1.0 / v2.3.2 **都是** `/api/v2/core` + `POST /settings/search` | 分别抓取各 tag 的 `core/init/router/router.go`、`core/router/ro_setting.go` |
| 版本字段 | `dto.SettingInfo.SystemVersion`、`dto.SettingBaseInfo.SystemVersion` 均有 `json:"systemVersion"`；另有 `edition`(cn/intl)、`appStoreVersion`、`panelName` | [core/app/dto/setting.go@v2.3.2](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/app/dto/setting.go) |
| 官方契约 | `swagger.json` basePath=`/api/v2`；`/core/settings/search`、`/core/settings/search/base` 均为 **post**，`security: [ApiKeyAuth, Timestamp]`；`dto.SettingInfo`/`dto.SettingBaseInfo` 均含 `systemVersion` | [core/cmd/server/docs/swagger.json@v2.3.2](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/cmd/server/docs/swagger.json) |
| **免认证指纹** | `GET /api/v2/core/auth/setting` → `200` JSON，`data` 含 `panelName/theme/isIntl/isEnterprise/isDemo/needCaptcha/passkeySetting`（**无版本字段**） | [core/router/ro_base.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/router/ro_base.go)、[core/app/api/v2/auth.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/app/api/v2/auth.go)（`GetLoginSetting`）、[core/middleware/session.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/middleware/session.go)（`isAnonymousAuthPath` 白名单：`auth/{captcha,passkey/begin,passkey/finish,mfalogin,login,logout,setting,welcome}`） |
| **v2 没有 `GET /health`** | v2.3.2 的 router.go / ro_base.go / runtime_diagnostics 里都没有根路径 `/health`；`/health/check` 是 agent 接口，必须带密钥（被 `Proxy()` 的 session 校验拦住） | 上述 router.go；`Proxy()` 里 `if !apiReq && ... && !checkSession(...) { 401 }` |
| 匿名判定 | `SessionAuth()` 先看 `API_AUTH`，再看 `isAnonymousAuthPath(path)` | core/middleware/session.go@v2.3.2 |

### 1.3 版本号从哪来（两条线一致）

`systemVersion` 不是编译期常量，而是设置表（KV）里的 `SystemVersion` 项，由 `GetSettingInfo()` 反序列化后返回：

- v1：[`GetSettingInfo()`](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/app/service/setting.go) → `settingRepo.GetList()` → `json.Unmarshal(settingMap, &info)`
- v2：[`GetSettingInfo()/GetSettingBaseInfo()`](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/app/service/setting.go) → 同样的 KV 反序列化

→ 只要面板安装/升级过，字段就有值；空值基本只出现在异常面板上，客户端应把「JSON 里没有 `systemVersion` 或为空」当作**探测失败**而不是版本 0。

### 1.4 API 密钥鉴权（v1 / v2 差异，客户端必须按线实现）

| 项 | v1 线 | v2 线（≥2.0.0） |
|----|-------|------------------|
| 请求头 | `1Panel-Token`、`1Panel-Timestamp`（秒级 Unix 时间戳） | **同一套** `1Panel-Token`/`1Panel-Timestamp`；可选新增 `1Panel-Signature-Version`、多密钥 `1Panel-Key-ID` |
| 签名 | `token = md5("1panel" + ApiKey + timestamp)`，小写 hex | 默认同时接受 **md5（同上）** 与 `hmac-sha256(key=ApiKey, msg="1panel:"+timestamp)`；`1Panel-Signature-Version: v1\|md5` 或 `hmac-sha256` 可指定 |
| 前置开关 | `ApiInterfaceStatus=enable`，且 `IpWhiteList` 非空并命中客户端 IP（`0.0.0.0/0` + `::/0` 放开） | 同上（另有 `ApiTrustedProxies` 用于取真实 IP） |
| 认证后行为 | `SessionAuth()` 直接放行（`c.Next()`），不查 session | 中间件置 `API_AUTH=true`，`SessionAuth()`/`PasswordExpired()`/`CSRFTokenGuard()` 全部跳过 |
| 源码依据 | [backend/middleware/session.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/middleware/session.go)（`isValid1PanelToken`=`GenerateMD5("1panel"+ApiKey+ts)`、`isIPInWhiteList`） | [core/app/auth/api_auth.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/app/auth/api_auth.go)（`APIAuthMiddleware`/`IsValid1PanelTokenWithVersion`/`isValidMD5Token`/`isValidHMACSHA256Token`）、[core/app/auth/api_key.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/app/auth/api_key.go)（`HasAPICredentials` 认这 4 个头）、[core/middleware/session.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/middleware/session.go)、[core/middleware/password_expired.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/middleware/password_expired.go)（L46 `if c.GetBool("API_AUTH") { c.Next() }`）、[core/middleware/csrf_protect.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/middleware/csrf_protect.go)（`requiresCSRFTokenCheck` 中 `API_AUTH` 返回 false）、[core/utils/xpack/community.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/utils/xpack/community.go) + [core/utils/xpack/helper/auth_helper.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/utils/xpack/helper/auth_helper.go)（`CoreAPIAuthMiddleware`，社区版同样生效） |
| 官方文档 | [v1 API Manual](https://1panel.pro/docs/v1/dev_manual/api_manual/)（示例 `curl ... /api/v1/dashboard/base/os`） | [v2 API Manual](https://1panel.pro/docs/v2/dev_manual/api_manual/)（`Token = md5('1panel' + API-Key + UnixTimestamp)`；白名单说明：`0.0.0.0/0`、`::/0`） |

**工程结论：Android 客户端只需要实现一套 md5 签名即可同时兼容 v1 与 2.0–2.3.x**（源码默认分支接受 md5），差异只在 URL 前缀。

### 1.5 ⚠️ 错误响应的陷阱（决定探测判定条件）

1Panel 对「未注册路径 / 未授权访问」**不返回 JSON**，而是返回 HTML 错误页，且 **HTTP 状态码由面板设置 `NoAuthSetting` 决定**（可选 `200/400/401/403/404/408/416/500/444`）：

- v1：`handleNoRoute()` 取 `GetResponsePage()` → `c.Data(statusCode, "text/html; charset=utf-8", html)`
  [backend/init/router/router.go@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/init/router/router.go)、[backend/middleware/helper.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/middleware/helper.go)（`LoadErrCode()` 默认返回 **200**）
- v2：`HandleNotSecurity()` 取 `LoadErrCode()`（同样默认 200）→ `c.Data(code, "text/html; charset=utf-8", data)`
  [core/utils/security/security.go@v2.3.2](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/utils/security/security.go)
- 另有全局 `BindDomain()` 中间件：面板配置了绑定域名而 `Host` 不匹配时，同样返回 HTML（`err_domain`）
  [backend/middleware/bind_domain.go@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/middleware/bind_domain.go)

**因此判定条件必须是「HTTP 200 + `Content-Type: application/json` + JSON 外壳里 `code == 200` 且 `data.systemVersion` 非空」**；
`404 / 401 / 403 / 200+HTML` 都**不能**单独作为「版本不对」的证据——它们可能只是「密钥错」「IP 不在白名单」「密码过期」「绑定了域名」或「面板把错误码伪装成 200」。

**第二层陷阱（同样关键）：业务/鉴权错误的 HTTP 状态码恒为 `200`，错误码放在 JSON 外壳的 `code` 字段里。**

- v1：`ErrorWithDetail()` 结尾是 `ctx.JSON(http.StatusOK, res)`，`res.Code` = 401/403/500…
  [backend/app/api/v1/helper/helper.go@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/app/api/v1/helper/helper.go)
- v2：`BadAuth()` → `ErrorWithDetail(ctx, http.StatusUnauthorized, ...)`，但写回仍是 `ctx.JSON(http.StatusOK, res)`
  [core/app/api/v2/helper/helper.go@v2.3.2](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/app/api/v2/helper/helper.go)
- 外壳结构（v2 swagger 明确列出）：`dto.Response{ code, data, errorCode, message }`
  [core/cmd/server/docs/swagger.json@v2.3.2](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/cmd/server/docs/swagger.json)

→ **客户端必须解析 JSON 外壳的 `code`，不能依赖 HTTP 状态码做任何判定。**

### 1.6 ⚠️ `v1.10.34-lts` 的异常（版本号 ≠ API 方言）

| 证据 | 内容 |
|------|------|
| tag 存在 | `refs/tags/v1.10.34-lts` → commit `2c92226f886f87e2e31eb72849360000193e74d0`（commit 日期 2026-01-04），Release 发布日 2026-01-05 |
| 该 tag 的仓库布局 | 根目录为 `agent/ ci/ core/ docs/ frontend/`（**没有 `backend/`**），`backend/app/dto/setting.go` 在该 tag 下 **404** |
| 该 tag 的路由 | `PrivateGroup := Router.Group("/api/v2/core")`，**无 `/health`**：[core/init/router/router.go@v1.10.34-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.34-lts/core/init/router/router.go) |
| 归属分支 | 该 commit 是 PR #11551 的合并提交，PR base = **`dev-v2`**；`compare/dev-v2...v1.10.34-lts` = `behind`（ahead_by 0） |
| 前端版本号 | `frontend/package.json` 在该 tag 下 `"name": "1panel-frontend", "version": "2.0"`；而 `v1.10.33-lts` 是 `"name": "1Panel-Frontend", "version": "1.10"` |
| 真正的 1.x LTS 代码在 | 分支 `release-1.10`（head `20b89a8`，仍是 `backend/ cmd/ go.mod` 布局） |

**结论：客户端绝不能用「版本号 → API 方言」的硬映射。** 只能在拿到版本号之后，仍以**端点探测结果**决定用哪套方言；反过来说，`1.10.34-lts` 这个 tag 构建出来的面板会走 `v2` 方言，若客户端按版本号判定就会全线 404。

---

## 2. 探测方案（可执行步骤表）

> 记 `{base}` = 用户填写的面板地址（含协议/端口，**不要**补 `/api/v1`），例如 `https://1panel.example.com:34443`。
> 所有请求带 `Accept: application/json`；判定统一使用 §1.5 的规则：**HTTP 200 + JSON 外壳 + `code==200` + 目标字段非空**，绝不依赖 HTTP 状态码。

### 2.1 第 0 步：免认证版本线指纹（不需要 API 密钥）

| 步骤 | 方法 | 路径 | 请求体 | 成功判定 | 失败 → 下一步 | 源码依据 |
|------|------|------|--------|----------|----------------|----------|
| **P0** | `GET` | `{base}/health` | — | `200` 且 body 去引号后 `== "ok"`（`Content-Type: application/json`）→ **v1 线**（抽查 v1.0.0/v1.5.0/v1.9.0/v1.10.0-lts/v1.10.33-lts 均有；v2.0.0 与 v2.3.2 均无此路由） | 非 `"ok"`（HTML 错误页 / 超时）→ **P1** | [v1 router.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/init/router/router.go)（`PublicGroup.GET("/health", ... c.JSON(200, "ok"))`） |
| **P1** | `GET` | `{base}/api/v2/core/auth/setting` | — | `200` JSON 且 `data` 同时含 `panelName`/`theme`/`isIntl`/`passkeySetting` 中至少两项 → **v2 线**（2.0.0+，含 1.10.34-lts tag 构建） | 非 JSON → **P1b**；仍不匹配 → 无法识别 | [v2 ro_base.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/router/ro_base.go)、[auth.go `GetLoginSetting`](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/app/api/v2/auth.go)、[session.go 匿名白名单](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/middleware/session.go) |
| **P1b** | `GET` | `{base}/api/v1/auth/setting`（可选补充证据，不参与主流程） | — | `200` JSON 且 `data` 只有 `needCaptcha/language` → 进一步确认 **v1 线**（≥1.10 才有此路由） | 都不匹配 → 报「不是 1Panel / 被反代改写 / 安全入口或绑定域名拦截」 | [v1 ro_base.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/router/ro_base.go)、[dto.LoginSetting](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/app/dto/auth.go) |

> P0/P1 均为**免认证**，可在用户还没填 API 密钥时先跑，用于「连接测试」。

### 2.2 第 1 步：带密钥拿版本号（1 次请求）

| 版本线 | 方法 | 路径 | 请求头 | 请求体 | 成功判定 | 失败 → 下一步 |
|--------|------|------|--------|--------|----------|----------------|
| **v1** | `POST` | `{base}/api/v1/settings/search` | `1Panel-Token: md5("1panel"+ApiKey+ts)`（小写 hex）<br>`1Panel-Timestamp: <秒级 ts>`<br>`Content-Type: application/json` | `{}`（handler 不读 body，可空） | HTTP `200` + JSON + 外壳 `code==200` + `data.systemVersion` 非空，例如 `"1.10.33-lts"` | ① 外壳 `code=401/403` → 读 `message` 区分「密钥错/白名单/接口未启用」；② HTML 或非 JSON → 换 v2 路径重试 1 次（§2.3 自愈）；③ `code` 提示密码过期 → v1 无免 `PasswordExpired` 的版本接口，提示用户去面板处理 |
| **v2** | `POST` | `{base}/api/v2/core/settings/search` | 同上（v2 也接受 md5；可选 `1Panel-Signature-Version: md5`） | `{}` | HTTP `200` + JSON + 外壳 `code==200` + `data.systemVersion` 非空，例如 `"2.3.2"` | ① 若返回「路径不存在」→ 降级试 `POST {base}/api/v2/core/settings/search/base`（≥2.2.1）；② 仍失败 → 换 v1 路径重试 1 次；③ 外壳 `code=401/403` → 按 `message` 提示 |
| **v2 备选** | `POST` | `{base}/api/v2/core/settings/search/base` | 同上 | `{}` | HTTP `200` + JSON + 外壳 `code==200` + `data.systemVersion` 非空（返回体更小，仅 SessionAuth；API 密钥本就跳过 `PasswordExpired`） | 同 v2 |

补充事实（用于设计回退顺序）：

- v2.0.0 / v2.0.9 / v2.1.0 的 `POST /core/settings/search` **只有 SessionAuth**（更宽松），`/search/base` 到 v2.2.1 才出现 → **`/core/settings/search` 是 v2 的首选主路径**。
- v2 的 `PasswordExpired()` 对 `API_AUTH=true` 请求直接放行（[password_expired.go L46](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/middleware/password_expired.go)），所以密钥请求在 2.3.x 上也畅通。
- v1 的 `PasswordExpired()` **没有** API 密钥旁路（[v1 password_expired.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/middleware/password_expired.go)），但 `POST /api/v1/settings/search` 恰好没挂这个中间件，所以正常面板不受影响。

### 2.3 请求次数与状态机

```
冷启动（无缓存）：
  P0  GET /health                        ← 免密钥，1 次
      ├─ "ok"        → 方言=v1 → P2v1  POST /api/v1/settings/search        （共计 2 次）
      └─ 其他         → P1 GET /api/v2/core/auth/setting   ← 免密钥，+1 次
                       ├─ JSON → 方言=v2 → P2v2 POST /api/v2/core/settings/search （共计 3 次）
                       └─ 非 JSON → 无法识别（提示：非 1Panel / 入口被拦）
  优化版（已知本机多半是 v2 时）：先打 P2v2，命中即 1 次；失败再 P2v1，共 2 次。
  （若允许在未确认方言时直接打 P2v2，v2 面板 = 1 次请求即拿到版本号）

已有缓存（上次成功的 {方言, 版本}）：直接打对应 P2 → 1 次
```

- **最少请求数**：**1 次**（已知/已缓存方言）；**冷启动 2 次**（`/health` + 目标线版本接口）；`/health` 被反代屏蔽的 v2 环境最坏 **3 次**。
- 建议像 Mono-Dash 一样**缓存「命中的方言」而不是缓存版本号**（版本会因为面板升级而变，方言极少变），并在每次 App 冷启动或用户下拉刷新时重取版本号（1 次请求）。

### 2.4 失败分支与用户提示矩阵

| 现象 | 判定 | 提示 |
|------|------|------|
| HTTP `200` + `text/html`（状态码可能被设成 200/401/404…） | 路径未注册（方言错）/ 绑定域名不匹配 / 安全策略 | 「面板地址或版本不匹配」，自动切换方言重试一次 |
| HTTP `200` + JSON，外壳 `code=401`，`message` 含 `ErrApiConfigStatusInvalid` | API 接口未启用 | 「请在面板 设置→面板→API 接口 中启用」 |
| 同上，含 `ErrApiConfigKeyInvalid` | 密钥错 | 「API 密钥不正确」 |
| 同上，含 `ErrApiConfigIPInvalid` | 客户端 IP 不在白名单 | 「请把本机公网 IP 加入面板 API 白名单（或设 `0.0.0.0/0`+`::/0`）」 |
| 同上，含 `ErrApiConfigKeyTimeInvalid` | 设备时间偏差 > `ApiKeyValidityTime`（默认 60s 容差） | 「请校准手机时间」 |
| 连接被直接关闭（无响应 / 状态码 `444`） | 面板设置 `NoAuthSetting=444` 隐藏自己 | 「面板拒绝了未授权访问（请核对地址/入口）」 |
| 超时/证书错误 | 网络或自签证书 | 提示允许自签证书 / 检查地址端口 |

依据：v1 [middleware/session.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/middleware/session.go)（`ErrApiConfig*` 分支）、v2 [api_auth.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/app/auth/api_auth.go)、[helper.go `LoadErrCode`](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/middleware/helper.go)。

### 2.5 边界清单（写代码前先看）

1. **别用 `GET /api/v1/settings/search`**：那是 POST 路由；GET 命中的是 NoRoute。
2. **别信 HTTP 状态码**：未注册路径的 HTML 错误页状态码默认就是 `200`（`NoAuthSetting` 可配），而**已注册路径的鉴权/业务错误 HTTP 状态码也恒为 `200`**，错误码在 JSON 外壳的 `code` 字段（§1.5）。判定只看「JSON 外壳 + `code==200` + `data.systemVersion`」。
3. **别用版本号推断方言**（§1.6）。
4. **别省 `IpWhiteList`**：白名单为空时 `IsIPInWhiteList` 恒 false（v1/v2 都是），密钥请求必然 401。
5. **时间戳容差 60s**：`panelTime > now+60` 直接失败；`ApiKeyValidityTime=0` 时不做时效校验。
6. **`SecurityEntrance`（安全入口）不影响 `/api/*`**：入口校验只作用于前端页面路径；但**绑定域名**会影响所有请求（`BindDomain` 全局中间件）。
7. **反向代理 / 面板访问白名单**：面板常被 nginx/CDN 包一层；另外 v1/v2 都有全局的 **面板访问 IP 白名单**中间件（v1 `WhiteAllow()` 读设置 `AllowIPs`，注册在所有路由之前，`/health` 也受它约束）→ 探测失败时要能区分「网络层/面板访问白名单」与「API 密钥层」。
   [backend/middleware/ip_limit.go@v1.10.33-lts](https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/middleware/ip_limit.go)
8. **企业版（xpack）**：额外挂了 `xpack.AuthProvider.CoreAPIAuthMiddleware()` 与 RBAC 中间件，但正文路径与响应字段不变；v2 的 `data.edition`（`cn`/`intl`）可用于区分国际版。
9. **多节点 / agent**：v2 中 `/api/v2/xxx`（非 `/core`）会被代理到 agent，密钥请求同样可用（`Proxy()` 对 `apiReq` 跳过 session 校验）；客户端若只做版本探测，不需要碰 agent 接口。

---

## 3. v3 / v4 核实结论与出处

**结论：截至 2026-10-05，1Panel 面板不存在 v3 / v4 版本线；v3/v4 属于客户端预留位。**

| 核查项 | 结果 | 出处 |
|--------|------|------|
| 官方仓库分支 | 仅 `dev`、`dev-v2`、`release-1.0`~`release-1.10`、`release-2.0.9`~`release-2.3.2`（另有 `aliyun`、dependabot/pr 临时分支）；**无 v3/v4/3.x/4.x 分支** | `https://api.github.com/repos/1Panel-dev/1Panel/branches?per_page=100`（本次抓取） |
| 官方 tag | v1.0.0 … v1.10.34-lts、v2.0.0 … **v2.3.2**（最新）；**无 v3*/v4* tag** | `https://api.github.com/repos/1Panel-dev/1Panel/tags?per_page=100` |
| GitHub Releases | 最新 `v2.3.2`（2026-09-24），历史列表回看到 v1.0.0，**无 v3/v4** | `https://api.github.com/repos/1Panel-dev/1Panel/releases` |
| 官方文档 | `https://1panel.cn/docs/v3/` → **404**；`https://1panel.pro/docs/v2/` → 200；文档站只有 v1、v2 两套 | 本次直接请求 |
| 社区/官方口径 | 论坛帖《V3还要付费？》中用户复述官方人员说法：「马上推出 v2 版本，肯定是免费的，但是 **v3 现在不在计划内**，不清楚是否收费」 | <https://bbs.fit2cloud.com/t/topic/10422/4> |
| 「2.x 之后的新架构」 | 存在的是 **v2 自身的 core/agent 拆分**（`core/` 面板控制面 + `agent/` 被控端，`/api/v2/core/*` 与 `/api/v2/*` 分流），不是 v3 | [v2.3.2 core/init/router/router.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/init/router/router.go)、[proxy.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/init/router/proxy.go) |
| 「企业版 / AI 网关」产品线 | FIT2CLOUD 官网产品导航中 **1Panel AI 网关**（<https://1panel.cn/ai-gateway.html>，定位「企业级 AI 统一接入与治理平台」）、**1Panel AI 一体机**（<https://1panel.cn/ai-appliance.html>）、**1Panel Pro**（<https://1panel.pro/>）是**独立产品/商业版本**，与面板 v1/v2 的 API 方言无关；企业版能力在代码里体现为 `xpack` 构建标签（[core/utils/xpack/community.go](https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/utils/xpack/community.go) 的 `//go:build !xpack && !enterprise`） | 文档中心导航 <https://docs.fit2cloud.com/>、上述站点 |

**因此：客户端的「v3 / v4」选项目前只能是预留位，选中后应直接提示「当前无对应官方版本，请使用自动或 v1/v2」，而不是去猜路径。**

---

## 4. 预留位工程建议（不浪费代码的做法）

目标：**今天只实现 v1/v2 两个 profile，但保证未来加 v3 profile 时不用改调用方、不用改 UI。**

1. **把「版本」抽象成可插拔的 `PanelProfile`（方言描述符），而不是散落的 `if (version.startsWith("2"))`**
   建议字段：
   ```kotlin
   interface PanelDialect {
       val id: String                  // "v1" | "v2" | "v3"(预留)
       val label: String               // UI 展示
       val corePrefix: String          // "/api/v1" 或 "/api/v2/core"
       val versionPath: String         // "settings/search"
       val healthProbe: Probe?         // 免认证指纹（v1 用 /health，v2 用 /api/v2/core/auth/setting）
       val signer: ApiSigner           // md5("1panel"+key+ts) 一套签名，未来可替换
       val versionField: String        // "systemVersion"
   }
   ```
   `v3`/`v4` 先注册为 **`UnsupportedDialect`**（`isAvailable=false`），UI 照常显示但选中即提示；未来只需新增一个 object 实现，不动 Repository/ViewModel。
2. **探测结果只缓存「方言 id」，不缓存版本号**；版本号每次连接/刷新时取（1 次请求）。参见 Mono-Dash 的 variant cache 思路（§5.2）。
3. **手动覆盖 = 强制方言**：用户选 `v1`/`v2` 时跳过 P0/P1，直接用对应 profile 的 P2；选「自动」时先按缓存、再按 P0→P1→P2 顺序探测。这样「自动」与「手动」共用同一套代码路径，只是 override 了 `dialectResolver`。
4. **探测逻辑集中在 `VersionProbe`，返回结构化结果**（`Detected(dialect, version, raw)` / `Unsupported` / `AuthError(kind)` / `NetworkError`），UI 只做文案映射 —— 未来加 v3 只需在列表里插一条优先探测项。
5. **判定谓词要覆盖 1Panel 的两层陷阱**：实现 `isPanelJson(resp)`（校验 `Content-Type: application/json` + 外壳含 `code`/`data` 且 `code==200`），把 `HTML`、`code!=200`、`444/连接关闭` 都归为「该方言不可用，试下一个」；注意**不能只看 HTTP 状态码**（业务错误也是 200）。这与社区实现（只认 404/405）不同，是**本项目相对社区实现的必要增强**。
6. **保留 raw 证据**：把探测命中的 `{path, status, contentType, systemVersion}` 存进「连接诊断」页，方便用户/我们排障（尤其 1.10.34-lts 这类异常构建）。
7. **版本号只用于展示与「可能有新特性」提示**，绝不参与接口选择；接口选择永远由方言 id 决定。

---

## 5. 社区实现参考（3 个）

### 5.1 `1Panel-dev/mcp-1panel`（官方 MCP Server，Go）——**只支持 v1**
- 仓库：<https://github.com/1Panel-dev/mcp-1panel>
- 关键代码：[`utils/constants.go`](https://raw.githubusercontent.com/1Panel-dev/mcp-1panel/main/utils/constants.go) 里 `ApiBase = "/api/v1"` **硬编码**；[`utils/http_client.go`](https://raw.githubusercontent.com/1Panel-dev/mcp-1panel/main/utils/http_client.go) 里 `md5Sum("1panel"+token+timestamp)`，请求头 `1Panel-Token`/`1Panel-Timestamp`。
- 借鉴点：**签名算法**可直接照抄（与 v1/v2 都兼容）；**反面教材**是 base path 硬编码 —— v2 面板上这个 MCP 会全线失败，正好说明客户端必须做方言层。

### 5.2 `nowubh/Mono-Dash`（Flutter 手机客户端，Android + iOS）——**有兼容层，最值得抄**
- 仓库：<https://github.com/nowubh/Mono-Dash>（Google Play：<https://play.google.com/store/apps/details?id=cc.boring_lab.monodash>）
- 关键代码：[`lib/core/network/api_compatibility.dart`](https://raw.githubusercontent.com/nowubh/Mono-Dash/main/lib/core/network/api_compatibility.dart)
  - `ApiCompatibility.tryVariants<T>(List<ApiEndpointVariant<T>>, shouldTryNext: isMissingEndpoint, cacheScope, cacheKey)`：**按顺序尝试多个端点变体**，命中后把「变体名」写进缓存（`_variantCache`），下次调用把缓存命中的变体提到最前（`_orderedVariants`）。
  - `isMissingEndpoint(error)` 目前只认 `statusCode == 404 || 405`。
  - 认证：[`lib/core/network/one_panel_auth.dart`](https://raw.githubusercontent.com/nowubh/Mono-Dash/main/lib/core/network/one_panel_auth.dart) → `md5('1panel' + apiKey + timestamp)` + `1Panel-Token`/`1Panel-Timestamp`（与官方一致）。
  - 接口路径：如 [`lib/data/api/setting_api.dart`](https://raw.githubusercontent.com/nowubh/Mono-Dash/main/lib/data/api/setting_api.dart) 使用 `/api/v2/core/settings/search`、`/api/v2/settings/basedir`。
- 借鉴点：**「有序变体 + 命中缓存 + 只对『端点不存在』继续尝试」** 的结构与本项目 §2 方案同构；**需要增强的地方**：把 `isMissingEndpoint` 扩成「非 JSON / HTML / 444 / 连接关闭」，否则遇到 1Panel 默认的 `200+HTML` 会误判为成功。

### 5.3 `certimate-go/certimate`（开源证书自动化，Go）——**v1/v2 双 SDK + 用户手选**
- 仓库：<https://github.com/certimate-go/certimate>
- 关键代码：[`pkg/core/deployer/providers/1panel/1panel.go`](https://raw.githubusercontent.com/certimate-go/certimate/main/pkg/core/deployer/providers/1panel/1panel.go)
  - 配置项 `ApiVersion string \`json:"apiVersion"\``，注释「1Panel 版本。可取值 "v1"、"v2"」；
  - `const (sdkVersionV1 = "v1"; sdkVersionV2 = "v2")` + `createSDKClient(...)` 用 `switch apiVersion` 选择两个 SDK：[`pkg/sdk3rd/1panel`](https://github.com/certimate-go/certimate/tree/main/pkg/sdk3rd/1panel)（v1）与 [`pkg/sdk3rd/1panel/v2`](https://github.com/certimate-go/certimate/tree/main/pkg/sdk3rd/1panel/v2)（v2，多一个 `WithNode`）。
- 借鉴点：**「一个产品两条 SDK、由用户显式选择」是被生产验证过的做法**——正好对应客户端的「手动覆盖」；certimate **没有做自动探测**（`grep detect|probe|auto` 无命中），说明「自动探测」是本项目要自己补的价值点。

---

## 6. 来源链接（本次实际抓取）

**官方源码（raw，按 tag 冻结）**

- v1 线：`backend/init/router/router.go`、`backend/router/ro_setting.go`、`backend/router/ro_base.go`、`backend/router/ro_dashboard.go`、`backend/middleware/{session,jwt,password_expired,bind_domain,helper}.go`、`backend/app/dto/{setting,auth}.go`、`backend/app/api/v1/{setting,auth}.go`、`backend/app/service/setting.go`、`cmd/server/docs/swagger.json`
  例：<https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.33-lts/backend/router/ro_setting.go>（把 `v1.10.33-lts` 换成 `v1.0.0`/`v1.5.0`/`v1.9.0`/`v1.10.0-lts` 可复核老版本）
- v1.10.34-lts（异常 tag）：<https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.34-lts/core/init/router/router.go>、<https://raw.githubusercontent.com/1Panel-dev/1Panel/v1.10.34-lts/frontend/package.json>、Release：<https://github.com/1Panel-dev/1Panel/releases/tag/v1.10.34-lts>
- v2 线：`core/init/router/{router,proxy}.go`、`core/router/{ro_setting,ro_base,ro_runtime_diagnostics}.go`、`core/middleware/{session,password_expired,csrf_protect,helper,frontend_fallback}.go`、`core/app/dto/{setting,auth}.go`、`core/app/api/v2/{setting,auth}.go`、`core/app/service/setting.go`、`core/app/auth/{api_auth,api_key}.go`、`core/utils/xpack/community.go`、`core/utils/xpack/helper/auth_helper.go`、`core/utils/security/security.go`、`core/cmd/server/docs/swagger.json`（v2.3.2；旧版本替换 tag 为 `v2.0.0`/`v2.0.9`/`v2.1.0`/`v2.1.13`/`v2.2.1`/`v2.2.5`/`v2.3.0`）
  例：<https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/core/router/ro_setting.go>

**官方文档 / 元数据**

- v1 API Manual：<https://1panel.pro/docs/v1/dev_manual/api_manual/>
- v2 API Manual：<https://1panel.pro/docs/v2/dev_manual/api_manual/>
- 文档中心（产品线导航，含 1Panel AI 网关/AI 一体机）：<https://docs.fit2cloud.com/>
- 1Panel AI 网关：<https://1panel.cn/ai-gateway.html>；1Panel Pro：<https://1panel.pro/>
- 分支/标签/Release：`https://api.github.com/repos/1Panel-dev/1Panel/{branches,tags,releases}?per_page=100`
- 版本对比：`https://api.github.com/repos/1Panel-dev/1Panel/compare/dev-v2...v1.10.34-lts`（`status=behind`, `ahead_by=0`）
- 社区论坛（v3 计划）：<https://bbs.fit2cloud.com/t/topic/10422/4>

**社区项目**

- <https://github.com/1Panel-dev/mcp-1panel>（官方 MCP，v1-only）
- <https://github.com/nowubh/Mono-Dash>（Flutter 客户端，`api_compatibility.dart` 兼容层）
- <https://github.com/certimate-go/certimate>（1Panel v1/v2 双 SDK，用户手选 apiVersion）

---

## 附：本文结论对应的验收点（自检）

- [x] 明确回答「能否一次请求拿到版本号」：**跨线不能；每条线各能，路径与字段见 §0/§2.2**
- [x] 探测方案每一步都给出方法/路径/请求体/判定/失败下一步，并附源码依据（§2.1、§2.2、§2.4）
- [x] 区分「无需认证的探测」（P0/P1，§2.1）与「需要 API 密钥的探测」（§2.2）
- [x] v3/v4 核实结论 + 出处（§3），并给出预留位工程建议（§4）
- [x] 3 个社区多版本兼容实现参考 + 仓库链接 + 关键做法（§5）
- [x] 所有结论附来源链接（文内 + §6）
