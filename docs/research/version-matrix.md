# 1Panel 面板版本 → API 契约对照矩阵（安卓客户端用）

> 产出人：api-matrix（task-1）｜取证方式：GitHub 源码逐 tag 抓取（`raw.githubusercontent.com` / `codeload` tarball）+ `git ls-remote` 交叉验证，全部结论标注 `tag + 文件路径(+行号)`。
> 覆盖版本：`release-1.0`（v1.0）、`v1.10.33-lts`、`v1.10.34-lts`、`v2.0.0`/`v2.0.17`/`v2.1.0`/`v2.1.13`/`v2.2.5`/`v2.3.0`/`v2.3.2`、`dev`、`dev-v2`。
> 任务口径：**v1 = 1.10.34-lts，v2 = 2.3.x**（本文件同时给出更早的 1.10.33-lts / 1.0，因为差异恰恰发生在它们之间，见 §0.2）。

---

## 0. 结论速览

### 0.1 四个「契约家族」（这才是客户端该建模的维度，而不是版本号）

| 家族 | 面板版本区间 | 仓库布局 | API 前缀 | API 密钥认证 | `POST /dashboard/current` | `GET /containers/list/stats` | `GET /apps/installed/list` |
|---|---|---|---|---|---|---|---|
| **D0** 远古 | 1.0.x ～ 1.9.x（`release-1.0`） | `backend/` 单体 | `/api/v1` | ❌ 不支持（仅 Cookie 会话 + JWT） | ❌ 无 POST；`GET /dashboard/current/{ioOption}/{netOption}` | ❌ **不存在** | ❌ **不存在** |
| **D1** v1 单体 | 1.1 ～ **1.10.33-lts** | `backend/` 单体 | `/api/v1` | ✅ MD5 | ✅ 有（body `{scope,ioOption,netOption}`） | ✅ 有 | ✅ 有 |
| **D2** v2 早期 | **1.10.34-lts** ～ 2.2.x | `core/`(面板) + `agent/`(主机代理) | `/api/v2` | ✅ MD5（2.2.5 起可选 HMAC-SHA256） | ❌ 无 POST；`GET /dashboard/current/{ioOption}/{netOption}` | ✅ 有 | ✅ 有 |
| **D3** v2 现代 | 2.3.x（当前 `dev-v2`） | `core/` + `agent/` | `/api/v2` | ✅ MD5 / HMAC-SHA256 + `1Panel-Key-ID` + `1Panel-Signature-Version` | ❌ 无 POST；`GET /dashboard/current/{ioOption}/{netOption}` | ✅ 有 | ✅ 有 |

来源：布局与前缀逐 tag 探测（§1.2 表）；`release-1.0 · backend/router/ro_dashboard.go:19-20`；`v1.10.33-lts · backend/router/ro_dashboard.go:19-21`、`backend/init/router/router.go:191,206`；`v1.10.34-lts · core/init/router/router.go:92`、`agent/init/router/router.go:20`、`agent/router/ro_dashboard.go:14-22`；`v2.3.2 · core/init/router/router.go:98`、`agent/init/router/router.go:20`、`agent/router/ro_dashboard.go:14-24`。

> **面板本身只有两代 API 前缀**（`/api/v1`、`/api/v2`）；全仓 grep 未发现 `/api/v3`（`v2.3.2 · core/`、`agent/`、`frontend/src/` 命中的 `api/v3` 均为第三方 LLM/etcd 地址）。因此客户端若必须有 **v1/v2/v3/v4 四个选项 + 自动**，建议按下表的「可观测契约」而非面板版本号来映射：

| 客户端选项 | 建议映射到的契约 | 覆盖面板版本 | 首选探测特征 |
|---|---|---|---|
| v1 | D0 | 1.0.x ～ 1.9.x | `/api/v1/dashboard/base/os` 不存在，但 `GET /api/v1/dashboard/current/all/all` 存在 |
| v2 | D1 | 1.1 ～ 1.10.33 | `GET /api/v1/dashboard/base/os` 存在 + `POST /api/v1/dashboard/current` 存在 |
| v3 | D2/D3（**当前主流**） | 1.10.34-lts 及全部 2.x | `GET /api/v2/dashboard/base/os` 存在 |
| v4 | 预留槽位（无对应实现） | —（未来 `/api/v3` 或企业版） | 保留枚举值，`自动` 探测不到 v1/v2/v3 时兜底 |
| 自动 | 按 §10 顺序探测 | 全部 | — |

### 0.2 三个最容易翻车的点（务必先看）

1. **`1.10.34-lts` 已经不是「老 v1 接口」了**：它和 2.x 一样是 `core/` + `agent/` 架构、`/api/v2` 前缀、`GET /dashboard/current/{io}/{net}`。断崖发生在 `v1.10.33-lts` → `v1.10.34-lts` 之间（tag 时间 2025-12-04 → 2026-01-04），**不能按主版本号 1.x/2.x 选择方言**。
2. **`/dashboard/current` 形态变了**：D1 是 `POST` + body `{scope,ioOption,netOption}`；D2/D3 是 `GET` + 路径参数，且 **`scope` 参数被彻底删除**（响应永远返回全部指标）。客户端若沿用 POST 会拿到 404。
3. **容器/网站的 `orderBy` 枚举值改名**：D1 `created_at` → D2/D3 `createdAt`（容器 + 网站都有）。这是**服务端 `validate:"oneof=..."` 强校验**，发错值直接 400。
   来源：`v1.10.33-lts · backend/app/dto/container.go:7` vs `v2.3.2 · agent/app/dto/container.go:7`；`v1.10.33-lts · backend/app/dto/request/website.go:7` vs `v2.3.2 · agent/app/dto/request/website.go:7`；前端交叉验证 `v1.10.33-lts · frontend/src/views/container/container/index.vue:363`（`orderBy: 'created_at'`）vs `v2.3.2 · frontend/src/views/container/container/operate/index.vue:682`（`orderBy: 'createdAt'`）。

### 0.3 认证速览（全部版本统一可用的一套）

```
1Panel-Token:     md5("1panel" + <API密钥> + <Unix秒级时间戳>)     ← D1 / D2 / D3 通用
1Panel-Timestamp: <Unix 秒级时间戳字符串>
```
- 请求头名、`md5` 前缀 `"1panel"`、时间戳单位=**秒**、容差=**60 秒**：`v1.10.33-lts · backend/middleware/session.go:23-24,97`；`v1.10.34-lts · core/middleware/api_auth.go:24-25,77-79`；`v2.3.2 · core/app/auth/api_auth.go:50-51,177`。
- 必须开启「API 接口」开关（`ApiInterfaceStatus == "enable"`），否则 401 `ErrApiConfigStatusInvalid`。
- **IP 白名单为空 = 所有 API 密钥请求都会被拒绝**（源码里空白名单直接 `return false`），这是客户端最常见的「密钥明明对却 401」原因。
- D3（2.3.2）新增：`1Panel-Key-ID`（多密钥）、`1Panel-Signature-Version: v1|md5|hmac-sha256`，且默认（不带版本头）**MD5 与 HMAC-SHA256 任一通过即可**（`HMAC-SHA256(secret, "1panel:" + timestamp)`）。
- **JWT 只存在于 D0/D1**：1.0 用 `Authorization` 头，1.10.33 用 `PanelAuthorization` 头（`backend/constant/session.go:7-8`）；`v1.10.34-lts`/`v2.3.2` 的 `core/` 里 **没有任何 JWT 代码**（仅 go.mod 残留依赖）。所以客户端**不要指望 JWT**，统一用 API 密钥。

---

## 1. 取证方法与版本时间线

### 1.1 方法（可复现）

```bash
# tag 列表 / SHA
curl -sSL "https://api.github.com/repos/1Panel-dev/1Panel/tags?per_page=100"
git ls-remote --tags https://github.com/1Panel-dev/1Panel.git | grep v2.3.2
# 单文件（快）
curl -sSL https://raw.githubusercontent.com/1Panel-dev/1Panel/v2.3.2/agent/router/ro_container.go
# 整包（无 API 限流，2~7MB）
curl -sSL "https://codeload.github.com/1Panel-dev/1Panel/tar.gz/refs/tags/v2.3.2" -o t.tgz && tar xzf t.tgz
```
注意：本环境 `raw.githubusercontent.com/<tag>/<旧路径>` 会 404 —— 因为不同 tag 的**目录结构不同**，必须同时试 `backend/…` 与 `core/…`、`agent/…`。

### 1.2 版本时间线（GitHub API `commits/<sha>` 的 commit date）

| tag | commit SHA（前 8 位） | 提交时间 | 布局 | API 前缀 | `/dashboard/current` 形态 |
|---|---|---|---|---|---|
| `release-1.0`（分支） | — | — | `backend/` | `/api/v1` | `GET /current/{io}/{net}` |
| `v1.10.33-lts` | `abd3c245` | 2025-12-04 | `backend/` | `/api/v1` | `POST /current`（body） |
| `v1.10.34-lts` | `2c92226f` | 2026-01-04 | `core/` + `agent/` | `/api/v2` | `GET /current/{io}/{net}` |
| `v2.0.0` ～ `v2.3.2` | `v2.3.2` = `65243c68` | v2.3.2 = 2026-09-24 | `core/` + `agent/` | `/api/v2` | `GET /current/{io}/{net}` |
| `dev` | — | — | `backend/` | `/api/v1` | `POST /current`（body） |
| `dev-v2`（默认分支） | — | pushed_at 2026-09-30 | `core/` + `agent/` | `/api/v2` | `GET /current/{io}/{net}` |

> 关键事实：`dev` 是 **v1 单体线**（`/api/v1` + POST），`dev-v2` 才是 v2 线（默认分支）。任务简报里「dev(v2)」的说法在 `dev-v2` 上成立、在 `dev` 上不成立。
> `dev-v2` 与 `v2.3.2` 在本文件涉及的 DTO 上**逐字节一致**（对 `agent/app/dto/container.go`、`request/app.go`、`request/website.go`、`dashboard.go` 做过 diff）。

---

## 2. 主表 A：客户端必用接口总览（相对路径；前缀见家族表）

| # | 用途 | 方法 + 相对路径 | D0 (1.0) | D1 (1.10.33) | D2 (1.10.34) | D3 (2.3.x) |
|---|---|---|---|---|---|---|
| 1 | 概览-OS | `GET /dashboard/base/os` | ❌ | ✅ | ✅ | ✅ |
| 2 | 概览-基础+IO/网络 | `GET /dashboard/base/{ioOption}/{netOption}` | ✅ | ✅ | ✅ | ✅ |
| 3 | 概览-实时指标 | `POST /dashboard/current`（body） | ❌ | ✅ | ❌ | ❌ |
| 3' | 概览-实时指标 | `GET /dashboard/current/{ioOption}/{netOption}` | ✅ | ❌ | ✅ | ✅ |
| 4 | 容器列表 | `POST /containers/search` | ✅ | ✅ | ✅ | ✅ |
| 5 | 容器统计 | `GET /containers/list/stats` | ❌ | ✅ | ✅ | ✅ |
| 6 | 容器操作 | `POST /containers/operate` | ✅（`name` 单数） | ✅（`names` 数组） | ✅ | ✅ |
| 7 | 已装应用-分页搜索 | `POST /apps/installed/search` | ✅ | ✅ | ✅ | ✅ |
| 8 | 已装应用-下拉列表 | `GET /apps/installed/list` | ❌ | ✅ | ✅ | ✅ |
| 9 | 已装应用-操作 | `POST /apps/installed/op` | ✅ | ✅ | ✅ | ✅ |
| 10 | 网站-分页搜索 | `POST /websites/search` | ✅ | ✅ | ✅ | ✅ |
| 11 | 网站-列表 | `GET /websites/list` | ✅ | ✅ | ✅ | ✅ |
| 12 | 网站-操作 | `POST /websites/operate` | ✅ | ✅ | ✅ | ✅ |
| 13 | io/net 选项枚举 | `GET /hosts/monitor/iooptions`、`/netoptions` | ⚠️ `GET /monitors/{iooptions,netoptions}` | ✅ | ✅ | ✅ |

来源：`release-1.0 · backend/router/ro_dashboard.go:19-20`、`ro_container.go:22,25`、`ro_app.go:30,31`、`ro_website.go:18,19,21,22`、`ro_monitor.go:12-21`；
D1 `v1.10.33-lts · backend/router/ro_dashboard.go:19-21`、`ro_container.go:25,27,36`、`ro_app.go:30-32`、`ro_website.go:18,19,21,22`、`ro_host.go:13,41-42`；
D2 `v1.10.34-lts · agent/router/ro_dashboard.go:14,20,22`、`ro_container.go:22,26,35`、`ro_app.go:32-34`、`ro_website.go:16,17,19`、`ro_host.go:35-36`；
D3 `v2.3.2 · agent/router/ro_dashboard.go:14,20,22`、`ro_container.go:20,24,33`、`ro_app.go:32-34`、`ro_website.go:16,17,19`、`ro_host.go:60-61`。

### 2.1 `v2.0 → v2.3` 的增量（同一家族内的细微差异）

| 特性 | v2.0.0 | v2.0.17 | v2.1.0 | v2.1.13 | v2.2.5 | v2.3.0 | v2.3.2 |
|---|---|---|---|---|---|---|---|
| 认证实现文件 | `core/middleware/api_auth.go` | 同左 | 同左 | 同左 | `core/app/auth/api_auth.go` | 同左 | `core/app/auth/api_auth.go` + `api_key.go` |
| HMAC-SHA256 可选 | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ |
| `1Panel-Signature-Version` / `1Panel-Key-ID` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| 请求体 `checkUpdate`（已装应用搜索） | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 响应 `dashboardBase.agentNumber` | ❌ | ❌ | ❌ | ✅ | ✅ | ✅ | ✅ |

取证：逐 tag 拉取 `core/middleware/api_auth.go` / `core/app/auth/api_auth.go` / `agent/app/dto/request/app.go` / `agent/app/dto/dashboard.go` 后 grep 关键字计数（`hmac-sha256`、`1Panel-Key-ID`、`1Panel-Signature-Version`、`checkUpdate`、`agentNumber`）。

---

## 3. 认证契约（逐版本）

### 3.1 API 密钥（客户端主用）

| 项 | D0 `release-1.0` | D1 `v1.10.33-lts` | D2 `v1.10.34-lts` | D3 `v2.3.2` |
|---|---|---|---|---|
| 头 | ❌ 无 | `1Panel-Token`+`1Panel-Timestamp` | 同 D1 | 同 D1，另支持 `1Panel-Key-ID`、`1Panel-Signature-Version` |
| 算法 | — | `md5("1panel"+apiKey+ts)` | 同 D1 | 默认：`md5(...)` **或** `hmacSHA256(apiKey,"1panel:"+ts)`；`Signature-Version: v1\|md5` 仅 MD5；`hmac-sha256` 仅 HMAC |
| 时间戳 | — | Unix **秒**；未来偏差 ≤60s | 同 D1 | 同 D1 |
| 有效期容差 | — | `ApiKeyValidityTime==0` → 不过期；`<0` → 恒失败；`>0` → `now-ts ≤ validity*60 + 60` | 同 D1 | 同 D1 |
| 开关 | — | 必须 `ApiInterfaceStatus=="enable"` | 同 D1 | 同 D1 |
| IP 白名单 | — | 空 = 拒绝；`0.0.0.0`、`0.0.0.0/0`、`::/0` 表示放行；支持 CIDR | 同 D1 | 同 D1（另支持 `ApiTrustedProxies` 反代取真实 IP） |
| 大小写 | — | **严格相等**（不发 ToLower/Trim） | 严格相等 | `ToLower(TrimSpace())` 后再比 |

来源：`v1.10.33-lts · backend/middleware/session.go:19-115`；`v1.10.34-lts · core/middleware/api_auth.go:17-118`；`v2.3.2 · core/app/auth/api_auth.go:44-180`（`IsValid1PanelTimestamp`、`IsValid1PanelTokenWithVersion`、`isValidMD5Token`、`isValidHMACSHA256Token`、`IsIPInWhiteList`）、`core/app/auth/api_key.go:70`（`HasAPICredentials`）、`core/cmd/server/main.go:21-36`（Swagger 里的官方头说明）。

> 客户端注意：D1 对 Token 不做 `ToLower`，所以 **md5 结果必须小写**（`hex.EncodeToString` 本身就是小写，正常不会踩）。
> D3 的 `HasAPICredentials` 只要看到任一头就进入 API 密钥校验分支 ⇒ 如果只想用手机号/密码登录，别误发空的 `1Panel-Token`。

### 3.2 登录 / 会话 / JWT

| 项 | D0 `release-1.0` | D1 `v1.10.33-lts` | D2 `v1.10.34-lts` | D3 `v2.3.2` |
|---|---|---|---|---|
| 登录路径 | `POST /api/v1/auth/login` | `POST /api/v1/auth/login` | `POST /api/v2/core/auth/login` | `POST /api/v2/core/auth/login` |
| 请求体 | `{name,password,captcha,captchaID,authMethod}`（无校验标签） | `{name,password,captcha,captchaID,authMethod:jwt\|session,language}` | `{name,password,captcha,captchaID,language}`（**无 authMethod**） | 同 D2 + `authSource`；`language` 枚举新增 `fa`/`lo` |
| 响应 | `{name,token,mfaStatus}` | `{name,token,mfaStatus}` | `{name,token,mfaStatus}` | `{name,role,token,mfaStatus,mfaSession}` |
| JWT | ✅ 头 `Authorization` | ✅ 头 `PanelAuthorization`（`JWTBufferTime=3600s`） | ❌ 无 JWT 代码 | ❌ 无 JWT 代码 |
| 会话 Cookie | `psession` | `psession` | `psession` | `psession`（另有 `CSRFTokenName`） |
| 免认证路径 | — | — | `/api/v2/core/auth*` | `/api/v2/core/auth*`（含 `/captcha`、`/login`、`/mfalogin`、`/passkey/*`、`/setting`、`/welcome`） |
| 额外能力 | — | 密码 RSA 加密、图形验证码、MFA | 同 D1 | + Passkey（`/auth/passkey/begin|finish`）、API 密钥自助管理（`/auth/api/generate`、`/auth/api/update`、`/auth/api/keys/search`） |

来源：`release-1.0 · backend/router/ro_base.go:16`、`backend/app/dto/auth.go`（`Login`）、`backend/constant/session.go:5,7,8`；
`v1.10.33-lts · backend/router/ro_base.go:11-20`、`backend/app/dto/auth.go:25-32`（含 `validate:"required,oneof=jwt session"`）、`backend/app/service/auth.go:118-140`（`generateSession`）、`backend/constant/session.go:7-9`；
`v1.10.34-lts · core/router/ro_base.go:11-18`、`core/app/dto/auth.go:8-12,25-31`、`core/init/router/router.go:92`；
`v2.3.2 · core/router/ro_base.go:12-38`、`core/app/dto/auth.go:8-17,35-42`、`core/middleware/session.go`（`isAnonymousAuthPath`）、`core/app/auth/api_auth.go:45`（跳过 `/api/v2/core/auth`）。

> 客户端结论：**只用 API 密钥**。密码登录链路（RSA 公钥、验证码、EntranceCode、MFA、Passkey）在 D2/D3 走的是 `xpack` 企业钩子，社区版行为不稳定，不值得在移动端实现。

### 3.3 统一响应信封

| 版本 | HTTP 状态 | body |
|---|---|---|
| D0/D1 | 恒为 200（业务错误码放 body） | `{"code":200,"message":"","data":...}` |
| D2/D3 | 恒为 200 | `{"code":200,"message":"","data":...}`；D3 另加可选 `errorCode` |

来源：`v1.10.33-lts · backend/app/api/v1/helper/helper.go:21,56-66` + `backend/app/dto/common_res.go`（`Response{code,message,data}`）+ `backend/constant/errs.go:8-14`（`CodeSuccess=200`、`CodeErrBadRequest=400`、`CodeErrUnauthorized=401`、`CodeErrInternalServer=500`）；
`v2.3.2 · agent/app/api/v2/helper/helper.go:23-31,61-71,159-169`、`agent/app/dto/common_res.go`（`Response{code,errorCode,message,data}`）。

> 移动端解析：**只看 body 的 `code`**，不要只看 HTTP 状态码；唯一例外是 D2/D3 面板代理层的「无会话」401 会返回 **HTML**（`html/401.html`），此时 body 不是 JSON，需要按 Content-Type 兜底。
> 来源：`v2.3.2 · core/init/router/proxy.go:44-49`、`v1.10.34-lts · core/init/router/proxy.go:40-45`。

---

## 4. 概览（Dashboard）契约

### 4.1 `GET {prefix}/dashboard/base/os`

- 版本：D1 ✅ / D2 ✅ / D3 ✅；**D0 ❌（1.0 没有这个接口）**。
- 响应 `data` = `OsInfo`：

| 字段 | D1 (v1.10.33) | D3 (v2.3.2) |
|---|---|---|
| `os` `platform` `platformFamily` `kernelArch` `kernelVersion` | ✅ | ✅ |
| `diskSize`（int64，字节） | ✅ | ✅ |
| `prettyDistro`（如 "Ubuntu 22.04.3 LTS"） | ❌ | ✅ 新增 |

来源：`v1.10.33-lts · backend/app/dto/dashboard.go`（`OsInfo`）、`backend/router/ro_dashboard.go:19`、`backend/app/api/v1/dashboard.go`（`LoadDashboardOsInfo`）；
`v2.3.2 · agent/app/dto/dashboard.go:48`（`OsInfo`）、`agent/router/ro_dashboard.go:14`、`agent/app/api/v2/dashboard.go:18-25`。

### 4.2 `GET {prefix}/dashboard/base/{ioOption}/{netOption}`

- 版本：D0/D1/D2/D3 **全部可用**，相对路径一致（仅前缀不同）。
- 路径参数无 `oneof` 校验，服务端把 `ioOption`/`netOption` 直接透传给磁盘/网卡计数器；**约定值 `"all"`**（前端默认也是 `all/all`）。
- 响应 `data` = `DashboardBase`，字段差异：

| 字段 | D0 (1.0) | D1 (1.10.33) | D2 (1.10.34) | D3 (2.3.2) |
|---|---|---|---|---|
| `websiteNumber` `databaseNumber` `cronjobNumber` | ✅ | ✅ | ✅ | ✅ |
| 已装应用数 | `appInstalldNumber`（**带 d 的拼写错误**） | `appInstalledNumber` | `appInstalledNumber` | `appInstalledNumber` |
| `hostname` `os` `platform` `platformFamily` `platformVersion` `kernelArch` `kernelVersion` `virtualizationSystem` `ipv4Addr` `cpuCores` `cpuLogicalCores` `cpuModelName` | ✅ | ✅ | ✅ | ✅ |
| 系统代理 | （无） | `SystemProxy`（**大写 S**） | `systemProxy` | `systemProxy` |
| `prettyDistro` | ❌ | ❌ | ✅（但 **v2.0.x 反而没有**，v2.1.0 起才有） | ✅ |
| `agentNumber`（多节点数量） | ❌ | ❌ | ❌（2.1.13 起 ✅） | ✅ |
| `cpuMhz` | ❌ | ❌ | ✅ | ✅ |
| `quickJump`（快捷跳转） | ❌ | ❌ | ✅ | ✅ |
| `currentInfo`（内嵌 `DashboardCurrent`） | ✅ | ✅ | ✅ | ✅ |

来源：`release-1.0 · backend/app/dto/dashboard.go:5-25`；`v1.10.33-lts · backend/app/dto/dashboard.go`（`DashboardBase`，无 `prettyDistro`/`cpuMhz`/`quickJump`/`agentNumber`；`SystemProxy` 为大写 S）；`v2.3.2 · agent/app/dto/dashboard.go:5-31`（`DashboardBase`）；`v1.10.34-lts · agent/app/dto/dashboard.go:26,28`（`cpuMhz`、`quickJump` 已存在）。
> `SystemProxy` → `systemProxy` 是**真实的大小写改名**，Gson/Moshi 严格模式下会解析不到，建议客户端统一 `@SerializedName` 或忽略大小写映射。

### 4.3 `POST {prefix}/dashboard/current`（**仅 D1：1.1～1.10.33**）

- 方法/路径：`POST /api/v1/dashboard/current`（**仅 D1**）。
- 请求体 = `DashboardReq`，**无任何 `validate` 标签**（服务端不会因取值非法而 400，只会静默返回空数据）：

```json
{ "scope": "ioNet", "ioOption": "all", "netOption": "all" }
```

| 字段 | 合法取值 | 语义（源码确认） |
|---|---|---|
| `scope` | `"basic"` \| `"ioNet"` \| `"gpu"` | 只有这三个值会触发对应数据块；其它值/空值 → 只返回 `uptime` + `shotTime` |
| `ioOption` | `"all"` 或具体磁盘设备名（如 `sda`、`nvme0n1`） | 仅在 `scope=="ioNet"` 时生效；`"all"` = 聚合全部磁盘，否则按设备名过滤 |
| `netOption` | `"all"` 或具体网卡名（如 `eth0`） | 仅在 `scope=="ioNet"` 时生效；`"all"` = `net.IOCounters(false)` 汇总，否则按网卡名匹配 |

来源：`v1.10.33-lts · backend/app/dto/dashboard.go`（`DashboardReq{Scope,IoOption,NetOption}`，无 validate 标签）、`backend/app/api/v1/dashboard.go`（`LoadDashboardCurrentInfo`，`@Router /dashboard/current [post]`）、`backend/app/service/dashboard.go:151-230`（`if req.Scope == "gpu" / "basic" / "ioNet"`、`if req.IoOption == "all"`、`if req.NetOption == "all"`）。

- 合法取值枚举可从 `GET /api/v1/hosts/monitor/iooptions` 与 `/hosts/monitor/netoptions` 拉到（返回 `string[]`，前端就是这么填下拉框的）。
  来源：`v1.10.33-lts · backend/router/ro_host.go:41-42`；`v2.3.2 · frontend/src/api/modules/host.ts:17-29`。

### 4.4 `GET {prefix}/dashboard/current/{ioOption}/{netOption}`（D0 / D2 / D3）

- D0：`GET /api/v1/dashboard/current/{ioOption}/{netOption}`。
- D2/D3：`GET /api/v2/dashboard/current/{ioOption}/{netOption}`。
- **没有 `scope`**：D2/D3 的 `LoadCurrentInfo(ioOption, netOption)` 无条件加载 CPU/内存/负载/磁盘/GPU/NPU/XPU，`ioOption`/`netOption` 只影响 IO/网络计数器。
- 如需「只刷新基本盘」的省流量行为，客户端要自己在本地丢弃不需要的块（移动端建议：只映射 uptime/load/cpu/mem/disk/io/net，忽略 top* 与加速卡）。

来源：`release-1.0 · backend/router/ro_dashboard.go:20`、`backend/app/api/v1/dashboard.go:48-61`；
`v1.10.34-lts · agent/router/ro_dashboard.go:22`、`agent/app/api/v2/dashboard.go:166-180`；
`v2.3.2 · agent/router/ro_dashboard.go:22`、`agent/app/api/v2/dashboard.go:166-180`、`agent/app/service/dashboard.go:184-280`、`agent/app/dto/dashboard.go:81-131`（`DashboardCurrent`）、`frontend/src/views/home/index.vue:592-595,852`（`ioOption:'all', netOption:'all'`）。

- `DashboardCurrent` 关键字段（D3）：`uptime`、`timeSinceUptime`、`runningTime{...}`（D3 新增）、`procs`、`load1/5/15`、`loadUsagePercent`、`cpuPercent[]`、`cpuUsedPercent`、`cpuUsed`、`cpuTotal`、`cpuDetailedPercent[]`（D3 新增）、`memoryTotal/memoryUsed/memoryFree/memoryShard/memoryCache/memoryAvailable/memoryUsedPercent`（`memoryFree`/`memoryShard`/`memoryCache` 为 D2/D3 新增）、`swapMemory*`、`ioReadBytes/ioWriteBytes/ioCount/ioReadTime/ioWriteTime`、`diskData[]`、`netBytesSent/netBytesRecv`、`gpuData[]`、`npuData[]`（D3）、`xpuData[]`、`topCPUItems/topMemItems`（D3 新增）、`shotTime`。
  - D1 与 D0 的差异：D1 无 `runningTime`/`npuData`/`top*`/`cpuDetailedPercent`；D0 的 `memoryUsedPercent` 的 JSON 名是 **`MemoryUsedPercent`（大写 M）**、且无 `swapMemory*`/`gpuData`。
  - 来源：`release-1.0 · backend/app/dto/dashboard.go:27-56`（`MemoryUsedPercent` 大小写错误）；`v1.10.33-lts · backend/app/dto/dashboard.go`（`DashboardCurrent`）；`v2.3.2 · agent/app/dto/dashboard.go:81-131`。

---

## 5. 容器契约

### 5.1 `POST {prefix}/containers/search`

- 路径：D0/D1 `/api/v1/containers/search`；D2/D3 `/api/v2/containers/search`（**相对路径全家族一致**）。
- 请求体（D2/D3，推荐形态）：

```json
{
  "page": 1,
  "pageSize": 20,
  "name": "",
  "state": "all",
  "orderBy": "name",
  "order": "ascending",
  "filters": "",
  "excludeAppStore": false
}
```

| 字段 | 必填/校验 | D0 (1.0) | D1 (1.10.33) | D2 (1.10.34) | D3 (2.3.2) |
|---|---|---|---|---|---|
| `page` `pageSize` | **必填** `required,number`（`PageInfo`） | ✅ | ✅ | ✅ | ✅ |
| `name` | 可选 | ✅ | ✅ | ✅ | ✅ |
| `filters` | 可选 | ✅ | ✅ | ✅ | ✅ |
| `state` | **必填** `oneof=all created running paused restarting removing exited dead` | ❌ 字段不存在 | ✅ | ✅ | ✅ |
| `orderBy` | **必填**；D1 `oneof=name state created_at`；D2/D3 `oneof=name createdAt state` | ❌ 不存在 | ✅（`created_at`） | ✅（`createdAt`） | ✅（`createdAt`） |
| `order` | **必填** `oneof=null ascending descending` | ❌ 不存在 | ✅ | ✅ | ✅ |
| `excludeAppStore` | 可选 bool | ❌ | ✅ | ✅ | ✅ |

来源：`release-1.0 · backend/app/dto/container.go`（`PageContainer{PageInfo,Name,Filters}`）；`v1.10.33-lts · backend/app/dto/container.go:7-21`；`v2.3.2 · agent/app/dto/container.go:7-22`；`v1.10.34-lts · agent/app/dto/container.go`（`PageContainer`，与 v2.3.2 一致）。
> 前端实测默认值：`state:'all'`、`orderBy:'name'`、`order:'ascending'`（`v2.3.2 · frontend/src/views/container/container/index.vue:533`）。注意是 `ascending`，不是 `asc`；降序用 `descending`，不排序用 `null`。

- 响应：`data = { "total": <int64>, "items": [ContainerInfo] }`（`dto.PageResult`）。
  `ContainerInfo` 字段：`containerID`、`name`、`imageID`、`imageName`、`createTime`、`state`、`runTime`、`network[]`、`ports[]`、`isFromApp`、`isFromCompose`、`appName`、`appInstallName`、`websites[]`，D3 追加 `isPinned`、`description`。
  来源：`v1.10.33-lts · backend/app/dto/container.go:22-46`；`v2.3.2 · agent/app/dto/container.go:23-100`（对比 diff 结论：D3 新增 `isPinned`/`description`）。

### 5.2 `GET {prefix}/containers/list/stats`

> **用户重点关注项的答案：v1.10 与 v2 的相对路径完全一致**（`/containers/list/stats`，GET），差异**只在 API 前缀**：`/api/v1/containers/list/stats`（D1）vs `/api/v2/containers/list/stats`（D2/D3）。它**在 D0/1.0 中不存在**。

- 响应：`data = [ContainerListStats]`（数组，非分页）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `containerID` | string | 容器 ID |
| `cpuTotalUsage` / `systemUsage` | uint64 | 累计 CPU 纳秒 |
| `cpuPercent` | float64 | CPU 百分比 |
| `percpuUsage` | int | 每核使用 |
| `memoryCache` / `memoryUsage` / `memoryLimit` | uint64 | 内存缓存/用量/上限 |
| `memoryPercent` | float64 | 内存百分比 |

来源：`v1.10.33-lts · backend/app/dto/container.go:78-91`、`backend/router/ro_container.go:27`；`v2.3.2 · agent/app/dto/container.go:173-186`、`agent/router/ro_container.go:24`、`agent/app/api/v2/container.go:419-426`；`release-1.0 · backend/router/ro_container.go`（**无该路由**）。
> 注意：D2/D3 请求由面板 `Proxy()` 转发到本机 agent（或 `CurrentNode` 指定节点），客户端仍打面板端口。

### 5.3 `POST {prefix}/containers/operate`

- 请求体：

```json
{ "names": ["nginx"], "operation": "restart", "taskID": "" }
```

| 项 | D0 (1.0) | D1 (1.10.33) | D2 (1.10.34) | D3 (2.3.2) |
|---|---|---|---|---|
| 目标字段 | `name`（**string 单数，required**） | `names`（**[]string，required**） | `names` | `names` |
| `operation` | `required,oneof=start stop restart kill pause unpause rename remove` | `required,oneof=up start stop restart kill pause unpause remove` | 同 D1 | 同 D1 |
| 附加字段 | `newName`（配合 `rename`） | — | `taskID` | `taskID`（+ 内部 `taskID` 用于异步任务回执） |
| 响应 | `{code:200}` | `{code:200}` | `{code:200}` | `{code:200}` |

来源：`release-1.0 · backend/app/dto/container.go`（`ContainerOperation{Name,Operation,NewName}`）；`v1.10.33-lts · backend/app/dto/container.go:117-121`；`v2.3.2 · agent/app/dto/container.go:213-218`、`agent/app/api/v2/container.go:612-623`。
> **破坏性差异**：`name`（单数）≠ `names`（数组）；`rename` 只在 D0 的枚举里、`up` 只在 D1+ 的枚举里。移动端把这两个方言分开建模成本极低。

### 5.4 D2→D3 容器路由增量（同一家族内的补充）

- D3 新增：`/containers/files/{search,upload,content,size,del,download}`、`/containers/compose/env`、`/containers/compose/pin`。
- D3 移除：`GET /containers/exec`（WebSocket 终端）、`POST /containers/command`（改为 `/hosts/terminal/container` 等新路径）。
  来源：`v1.10.34-lts · agent/router/ro_container.go:14,18` vs `v2.3.2 · agent/router/ro_container.go`（全量路由 diff）。

---

## 6. 应用（App Store / 已安装）契约

### 6.1 `POST {prefix}/apps/installed/search`

- 请求体：

```json
{
  "page": 1, "pageSize": 20,
  "type": "", "name": "", "tags": [],
  "update": false, "unused": false, "all": false, "sync": false,
  "checkUpdate": false
}
```

| 字段 | D0 (1.0) | D1 (1.10.33) | D2 (1.10.34) | D3 (2.3.2) |
|---|---|---|---|---|
| `page` `pageSize` | ✅ required | ✅ | ✅ | ✅ |
| `type` `name` `tags` `update` `unused` | ✅ | ✅ | ✅ | ✅ |
| `all` `sync` | ❌ | ✅ | ✅ | ✅ |
| `checkUpdate` | ❌ | ❌ | ❌（2.1.0 起 ✅） | ✅ |

来源：`release-1.0 · backend/app/dto/request/app.go:23-31`；`v1.10.33-lts · backend/app/dto/request/app.go:39-48`；`v2.3.2 · agent/app/dto/request/app.go:59-70`；`v1.10.34-lts · agent/app/dto/request/app.go`（与 D3 完全一致）。

- 响应：`data = {"total": <int64>, "items": [AppInstallDTO]}`（`PageResult`）。
  `all:true` 时走 `SearchForWebsite`，**items 类型同样是 `AppInstallDTO`**（v1.10.34 与 v2.3.2 均如此），可放心用同一解析器。
  来源：`v2.3.2 · agent/app/api/v2/app_install.go:19-44`、`agent/app/service/app_install.go:82,203`；`v1.10.34-lts · agent/app/api/v2/app_install.go:19-44`、`agent/app/service/app_install.go:78,198`。
- `AppInstallDTO` 关键字段（客户端主用 **粗体**）：**`id`**、**`name`**、**`version`**、**`status`**、**`canUpdate`**、**`appName`**、**`icon`**、`appID`、`appDetailID`、`message`、`httpPort`、`httpsPort`、`path`、`ready`/`total`、`appKey`、`appType`、`appStatus`、`dockerCompose`、`webUI`、`createdAt`、`favorite`、`container`、`isEdit`、`linkDB`、`serviceName`；D3 追加 `sortOrder`、`resourceKeys`、`env`。

来源：`v1.10.33-lts · backend/app/dto/response/app.go:101-113`；`v2.3.2 · agent/app/dto/response/app.go:106-139`。
> 另有一个 `AppInstalledDTO`（内嵌 `model.AppInstall`，含 `total/ready/appName/icon/canUpdate/path`）在 v1.10.33 与 v2.3.2 中**都是声明后未被任何 service 使用**（grep 全仓仅命中声明处）——不要照它建模，请用 `AppInstallDTO`。

### 6.2 `GET {prefix}/apps/installed/list`

- 版本：D1 ✅ / D2 ✅ / D3 ✅；**D0 ❌**（1.0 无此路由）。
- 响应：`data = [AppInstallInfo]`，**只有 3 个字段**：`id`(uint)、`key`(string)、`name`(string)。用于下拉选择（前端 `AppInstalledOption` 同构）。
- 三个家族**完全一致**（v1.10.33 / v1.10.34 / v2.3.2 逐字段相同）。
  来源：`v1.10.33-lts · backend/app/api/v1/app_install.go:55-62`、`backend/app/dto/app.go:158-162`；`v2.3.2 · agent/app/api/v2/app_install.go:54-61`、`agent/app/dto/app.go:177-182`；`v1.10.34-lts · agent/app/dto/app.go`（`AppInstallInfo`）；`v2.3.2 · frontend/src/api/interface/app.ts:217-221`。

### 6.3 `POST {prefix}/apps/installed/op`

- 请求体（D3）：

```json
{
  "installId": 1,
  "operate": "upgrade",
  "backupId": 0, "detailId": 0,
  "forceDelete": false, "deleteBackup": false, "deleteDB": false,
  "backup": false, "pullImage": false, "dockerCompose": "",
  "taskID": "", "deleteImage": false, "favorite": false
}
```

| 项 | D0 (1.0) | D1 (1.10.33) | D2 (1.10.34) | D3 (2.3.2) |
|---|---|---|---|---|
| `installId` | ✅ required | ✅ required | ✅ required | ✅ required |
| `operate` | ✅ required | ✅ required | ✅ required | ✅ required |
| **operate 合法值** | `up` `down` `start` `stop` `restart` `delete` `sync` `backup` `restore` `update` `rebuild` `upgrade` | `start` `stop` `restart` `delete` `sync` `backup` `update` `rebuild` `upgrade` `reload` | 同 D1 **+ `favorite`** | 同 D2 |
| 其它字段 | `backupId` `detailId` `forceDelete` `deleteBackup` `deleteDB` | + `backup` `pullImage` `dockerCompose` | + `taskID` `deleteImage` `favorite` | 同 D2（另有 `json:"-"` 的内部字段 `UseLifecycleScripts`） |

来源：`release-1.0 · backend/app/dto/request/app.go:41-50` + `backend/constant/app.go`（`AppOperate` 常量块）；`v1.10.33-lts · backend/app/dto/request/app.go:64-75` + `backend/constant/app.go:42-55`；`v2.3.2 · agent/app/dto/request/app.go:85-102` + `agent/constant/app.go:46-47`（`AppOperate`，含 `Favorite = "favorite"`）；`v1.10.34-lts · agent/app/dto/request/app.go`（与 D3 一致）+ `agent/constant/app.go:43-44`（`reload`/`favorite` 已存在）。
> 注意 `operate` 是**字符串常量集合**（不是 `oneof` 校验），发未知值不会 400，只会得到业务错误或无操作——客户端应白名单化。

### 6.4 D2→D3 应用路由增量

- 新增：`POST /apps/installed/sort/update`；`GET /apps/icon/:appID` **改名为** `GET /apps/icon/:key`；新增 `/apps/installed/ignore`、`/apps/ignored/detail`、`/apps/ignored/cancel`。
  来源：`v1.10.34-lts · agent/router/ro_app.go` vs `v2.3.2 · agent/router/ro_app.go`（全量路由 diff）。

---

## 7. 网站契约

### 7.1 `POST {prefix}/websites/search`

- 请求体：

```json
{ "page": 1, "pageSize": 20, "name": "", "orderBy": "favorite", "order": "descending", "websiteGroupId": 0, "type": "" }
```

| 字段 | D0 (1.0) | D1 (1.10.33) | D2 (1.10.34) | D3 (2.3.2) |
|---|---|---|---|---|
| `page` `pageSize` | ✅ required | ✅ | ✅ | ✅ |
| `name` | ✅ | ✅ | ✅ | ✅ |
| `websiteGroupId` | ✅ | ✅ | ✅ | ✅ |
| `orderBy` | ❌ 不存在 | ✅ **必填** `oneof=primary_domain type status created_at expire_date` | ✅ **必填** `oneof=primary_domain type status createdAt expire_date created_at favorite` | 同 D2 |
| `order` | ❌ 不存在 | ✅ **必填** `oneof=null ascending descending` | ✅ | ✅ |
| `type` | ❌ | ❌ | ✅ | ✅ |

来源：`release-1.0 · backend/app/dto/request/website.go:7-11`；`v1.10.33-lts · backend/app/dto/request/website.go:7-13`；`v2.3.2 · agent/app/dto/request/website.go:7-15`；`v1.10.34-lts · agent/app/dto/request/website.go`（与 D3 一致）。
> D2/D3 的 `oneof` 同时接受 `createdAt` 与 `created_at`（向后兼容），但 `order` 与 `orderBy` 都是**必填**，漏发直接 400 `ErrInvalidParams`。

- 响应：`data = {"total": <int64>, "items": [WebsiteRes]}`。
  `WebsiteRes` 字段：`id`、`createdAt`、`protocol`、`primaryDomain`、`type`、`alias`、`remark`、`status`、`expireDate`、`sitePath`、`appName`、`runtimeName`、`sslExpireDate`、`sslStatus`、`appInstallId`、`childSites[]`、`runtimeType`、`favorite`、`IPV6`；D3 追加 `parentSite`。
  来源：`v1.10.33-lts · backend/app/dto/response/website.go`（`WebsiteRes`）、`backend/app/service/website.go:114`；`v2.3.2 · agent/app/dto/response/website.go`（`WebsiteRes`）、`agent/app/service/website.go:163`。

### 7.2 `GET {prefix}/websites/list`

- 三个家族**路径一致**，响应 `data = [WebsiteDTO]`（**注意与 search 的 item 类型不同**）。
- `WebsiteDTO` = 内嵌 `model.Website`（`id`、`createdAt`、`primaryDomain`、`type`、`alias`、`remark`、`status`、`expireDate`、…）+ `errorLogPath`、`accessLogPath`、`sitePath`、`appName`、`runtimeName`、`siteDir`；D3 追加 `runtimeType`、`openBaseDir`、`algorithm`、`udp`、`servers[]`。
  来源：`v1.10.33-lts · backend/app/dto/response/website.go:8-16`、`backend/app/service/website.go:194`；`v2.3.2 · agent/app/dto/response/website.go`（`WebsiteDTO`）、`agent/app/service/website.go:256`；`v1.10.34-lts · agent/app/dto/response/website.go`（与 D3 差 `parentSite` 等）。

### 7.3 `POST {prefix}/websites/operate`

- 请求体：`{ "id": 1, "operate": "stop" }`（`id` **required**，`operate` 无校验标签）。
- **合法值只有两个：`start` / `stop`**（源码用常量比较，未知值静默 `return nil`，不会报错但也不会生效）。
  来源：`v1.10.33-lts · backend/app/dto/request/website.go:70-73`、`backend/app/service/website_utils.go:738+`、`backend/constant/website.go:28-29`；`v2.3.2 · agent/app/dto/request/website.go:119-122`、`agent/app/service/website_utils.go`（`opWebsite`）、`agent/constant/website.go:32-33`；前端实测只调 `operateWebsite('start'|'stop', row)`（`v2.3.2 · frontend/src/views/website/website/index.vue:175,182`）。
- D2/D3 另有批量接口 `POST /websites/batch/operate`（`BatchWebsiteOp`）。
- 来源：`v2.3.2 · agent/router/ro_website.go:30`。

### 7.4 D2→D3 网站路由增量

- **破坏性改名**：`POST /websites/log`（D2）→ `POST /websites/log/operate`（D3），并新增 `POST /websites/log/search`。
- 新增：`/websites/proxies/delete`、`/websites/proxies/status`。
  来源：`v1.10.34-lts · agent/router/ro_website.go` vs `v2.3.2 · agent/router/ro_website.go`（全量路由 diff）。

---

## 8. 差异清单（任务要求：v1=1.10.34-lts vs v2=2.3.x）

### 8.1 有差异的项（8 条）

| # | 项 | v1 = 1.10.34-lts | v2 = 2.3.x | 影响 |
|---|---|---|---|---|
| 1 | 认证-签名版本头 | 仅 MD5，无 `1Panel-Signature-Version` / `1Panel-Key-ID` | 支持 HMAC-SHA256、`1Panel-Key-ID`（多密钥）、`1Panel-Signature-Version` | 客户端只需发 MD5 即可两边通用（D3 默认接受 MD5）；若要支持密钥轮换再加 Key-ID |
| 2 | 认证实现位置 | `core/middleware/api_auth.go` | `core/app/auth/api_auth.go` + `core/app/auth/api_key.go` | 仅源码定位差异，协议不变 |
| 3 | 已装应用搜索请求体 | 无 `checkUpdate` | 有 `checkUpdate`（2.1.0 起） | 客户端发 `checkUpdate:false` 在 v1 上会被忽略（gin 忽略未知字段），安全 |
| 4 | `DashboardBase` 响应 | 无 `agentNumber`（2.1.13 起才有） | 有 `agentNumber`（多节点数） | v2 有值、v1 缺失，解析时给默认 0 |
| 5 | 已装应用 `AppInstallDTO` 响应 | 无 `sortOrder`/`resourceKeys`/`env` | 有 `sortOrder`/`resourceKeys`/`env` | 纯新增，忽略即可 |
| 6 | 网站 `WebsiteRes` 响应 | 无 `parentSite` | 有 `parentSite` | 纯新增 |
| 7 | 网站日志路由 | `POST /websites/log` | `POST /websites/log/operate` + `/log/search` | 若客户端做网站日志，必须分方言 |
| 8 | 应用图标路由 | `GET /apps/icon/:appID` | `GET /apps/icon/:key`（**参数名/语义变了**） | 只在用图标接口时受影响 |

来源：§2.1、§3.1、§6.1、§6.4、§7.4 各条已逐项标注；第 3–6 条来自 v1.10.34 与 v2.3.2 的 DTO diff（`agent/app/dto/request/app.go`、`agent/app/dto/dashboard.go`、`agent/app/dto/response/app.go`、`agent/app/dto/response/website.go`）。

### 8.2 明确「无差异」的项（同样重要）

| 项 | 结论 | 证据 |
|---|---|---|
| API 前缀 | **无差异**：都是 `/api/v2` | `v1.10.34-lts · core/init/router/router.go:92`、`agent/init/router/router.go:20`；`v2.3.2` 同位置 |
| `GET /containers/list/stats` 路径 | **无差异**（GET，相对路径相同） | `v1.10.34-lts · agent/router/ro_container.go:26`；`v2.3.2 · agent/router/ro_container.go:24` |
| `/containers/search` 请求体 | **无差异**（含 `orderBy` 枚举 `name createdAt state`） | `v1.10.34-lts` 与 `v2.3.2 · agent/app/dto/container.go:7` 一致 |
| `/containers/operate` 请求体与枚举 | **无差异**（`taskID`+`names`+`oneof=up start stop restart kill pause unpause remove`） | 同上 DTO diff 无输出 |
| `/apps/installed/list` 响应 | **无差异**（`[{id,key,name}]`） | `v1.10.34-lts`/`v2.3.2 · agent/app/dto/app.go` |
| `/apps/installed/op` operate 枚举 | **无差异**（11 个值：`start stop restart delete sync backup update rebuild upgrade reload favorite`） | `v1.10.34-lts · agent/constant/app.go:33-44`；`v2.3.2 · agent/constant/app.go:36-47` |
| `/websites/search` 请求体、`/websites/operate` 枚举 | **无差异** | DTO diff 无输出；`start`/`stop` 两版一致 |
| `/dashboard/base/os`、`/base/{io}/{net}`、`/current/{io}/{net}` 路径与 `scope` 语义 | **无差异**（都无 POST、都无 scope） | `agent/router/ro_dashboard.go` 两版路由逐行相同 |
| 统一响应信封 | **无差异**（`{code,message,data}`，HTTP 恒 200；D3 多一个可选 `errorCode`） | §3.3 |
| `PageInfo` 必填校验 | **无差异**（`page`/`pageSize` `required,number`） | `agent/app/dto/common_req.go` 两版一致 |

---

## 9. 更早/更晚版本的差异（供方言设计参考）

### 9.1 `v1.10.33-lts` → `v1.10.34-lts`：断崖式变化（**最重要**）

| 维度 | 1.10.33-lts（D1） | 1.10.34-lts（D2） |
|---|---|---|
| 仓库布局 | `backend/` 单体 | `core/`（面板）+ `agent/`（主机代理，独立 Go module） |
| API 前缀 | `/api/v1`（`backend/init/router/router.go:191,206`） | `/api/v2`（`core/init/router/router.go:92` + `agent/init/router/router.go:20`） |
| 面板自身接口 | 与业务接口同前缀 | 挪到 `/api/v2/core/*`，非 `core` 的 `/api/v2/*` 全部代理给 agent（`core/middleware/helper.go:9-17`） |
| `/dashboard/current` | `POST /api/v1/dashboard/current` + `{scope,ioOption,netOption}` | `GET /api/v2/dashboard/current/{io}/{net}`，**scope 消失** |
| 容器 `orderBy` | `created_at` | `createdAt` |
| 网站 `orderBy` | `oneof=primary_domain type status created_at expire_date` | `oneof=... createdAt ... favorite`（+`type` 字段） |
| 容器 `ContainerOperation` | 无 `taskID` | 有 `taskID` |
| 已装应用搜索 | 无 `checkUpdate` | 无（2.1.0 才有） |
| 网站日志路由 | `POST /websites/log` | `POST /websites/log`（D3 才改名为 `/websites/log/operate`，并新增 `/websites/log/search`） |
| 公共 `/health` | ✅ 有（`backend/init/router/router.go:200`） | ❌ 移除（core 无 `/health`；只剩 agent 的 `GET /api/v2/health/check`，需认证：`agent/init/router/router.go:27`） |
| JWT | ✅ `PanelAuthorization`（`backend/constant/session.go:7-8`） | ❌ 无 |
| 登录路径 | `POST /api/v1/auth/login`（`backend/router/ro_base.go:16`） | `POST /api/v2/core/auth/login`（`core/router/ro_base.go:16`） |
| 多节点 | 无 | `CurrentNode` 头 / `?operateNode=` 查询参数（`core/init/router/proxy.go:32-36`） |

### 9.2 `release-1.0`（v1.0）相对 D1 的差异

| 项 | v1.0 | 影响 |
|---|---|---|
| API 密钥 | ❌ 完全不存在（`backend/middleware/session.go` 无 `1Panel-Token` 等） | 只能 Cookie 会话/JWT |
| `GET /dashboard/base/os` | ❌ 不存在 | 客户端 OS 卡片需改用 `GET /dashboard/base/{io}/{net}` 的内嵌字段 |
| `/dashboard/current` | `GET /dashboard/current/{io}/{net}`（无 body、无 scope） | 与 D2/D3 形态相同、与 D1 不同 |
| `GET /containers/list/stats` | ❌ 不存在 | 只能逐个 `GET /containers/stats/{id}` |
| `GET /apps/installed/list` | ❌ 不存在 | 用 `POST /apps/installed/search` |
| `/containers/search` body | `{page,pageSize,name,filters}`（无 state/orderBy/order） | 需容忍缺字段 |
| `/containers/operate` body | `{name:"x", operation:"restart", newName:""}`（单数！） | 方言分支 |
| `/apps/installed/op` operate | `up down start stop restart delete sync backup restore update rebuild upgrade`（多 `up/down/restore`，少 `reload`） | 方言分支 |
| `/websites/search` body | `{page,pageSize,name,websiteGroupId}`（无 orderBy/order） | 需容忍缺字段 |
| `DashboardBase` | `appInstalldNumber`（拼写错误）、无 `systemProxy` | 字段映射特例 |
| `DashboardCurrent` | `MemoryUsedPercent`（大写 M）、无 swap/gpu | 字段映射特例 |
| io/net 选项 | `GET /api/v1/monitors/iooptions`、`/monitors/netoptions`（**复数 monitors**） | 与 D1+ 的 `/hosts/monitor/*` 不同 |
| JWT 头 | `Authorization` | 与 D1 的 `PanelAuthorization` 不同 |

来源：`release-1.0 · backend/router/ro_dashboard.go:19-20`、`ro_container.go:22,25`、`ro_app.go:30,31`、`ro_website.go:18,19,21`、`ro_monitor.go:12-21`、`backend/app/dto/container.go`、`backend/app/dto/dashboard.go`、`backend/app/dto/request/{app,website}.go`、`backend/constant/{app,session}.go`、`backend/middleware/session.go`。

---

## 10. 「自动」方言探测方案（给客户端的建议）

因为 `1.10.34-lts` 与 `2.x` 同契约、`1.10.33-lts` 与 `1.0` 不同契约，**探测必须以「接口是否存在 + 响应形态」为准，不能以版本号为准**。

推荐探测顺序（全部无副作用、只读）：

| 步骤 | 请求 | 判定 |
|---|---|---|
| 1 | `GET {base}/api/v2/dashboard/base/os` | 返回 JSON 且 `code==200` → **D3/D2（v2 家族）**；`401`（含 HTML）→ 路径存在，仍属 v2 家族（凭据/开关问题）；`404` → 下一步 |
| 2 | `GET {base}/api/v1/dashboard/base/os` | `200`/`401` → **D1（1.1～1.10.33）**；`404` → 下一步 |
| 3 | `GET {base}/api/v1/dashboard/current/all/all` | `200`/`401` → **D0（1.0.x）**；否则判为未知/非 1Panel |
| 4 | （可选）回调 `GET /api/v1/auth/setting`（D1/D2/D3）或 `GET /api/v1/auth/status`（D0）展示面板信息 | — |

注意事项：

1. **404 与 401 都要当作「路径不存在/存在」的信号**：D2/D3 的面板代理对「已知路径但无会话」返回 **401 HTML**（`core/init/router/proxy.go:44-49`），对未知路径走 `NoRoute` → 404；D1 未认证时返回 **401 JSON**（`helper.ErrorWithDetail(CodeErrUnauthorized)`）。
2. **不要只看 HTTP 200**：D1 的 `NoRoute` 会按设置项 `NoAuthSetting` 返回 HTML（可能是 404、也可能是 200 的登录页），所以要校验 **`Content-Type: application/json` + body 里的 `code`/`data` 结构**。
   来源：`v1.10.33-lts · backend/init/router/router.go:88-110`（`handleNoRoute` + `GetResponsePage`，`backend/app/service/auth.go:185-191`）。
3. 探测请求**不要带 API 密钥**，避免把「有无凭据」和「接口存在性」两个变量混在一起；探测结果按 `host:port + 面板版本提示` 缓存到本地。
4. v2 家族内若要区分 D2/D3，用**响应字段/行为**而不是路径：`dashboard/base/{io}/{net}` 有 `agentNumber` ⇒ 2.1.13+；`apps/installed/search` 支持 `checkUpdate` ⇒ 2.1.0+；`1Panel-Signature-Version` 生效 ⇒ 2.3.2+（v2.3.0/v2.3.1 均无）。**不要用 `prettyDistro` 判别**：它出现在 `v1.10.34-lts` 与 `v2.1.0+`，但 `v2.0.0` 没有（LTS 分支与 v2.0 分支不同步）。
5. 只有 `1.10.34-lts`+ 才需要发 `CurrentNode`（多节点）头；单机场景留空即可（`proxy.go` 会在 `currentNode` 为空或 `"local"` 时转发本机 agent）。

---

## 11. 客户端应如何抽象版本差异（5 条建议）

1. **把「方言」定义成 4 个可组合的开关，而不是 4 个 if-else 分支**。
   建议枚举：`Prefix(V1|V2)`、`DashboardCurrent(POST_BODY|GET_PATH)`、`OperateShape(SINGLE_NAME|NAMES_ARRAY)`、`OrderByStyle(SNAKE|CAMEL)`，再加 `HasListStats/HasInstalledList/HasBaseOs` 三个能力位。探测结果只填这 7 个字段，所有 API 层用同一份代码 + 参数拼装。
2. **把「版本探测」做成一次性能力探测并持久化**（§10 的表），并允许用户手动覆盖（很多人会走反向代理/自定义域名，探测可能被 WAF 干扰）。探测失败时降级为 D3（当前主流），并在设置页展示"检测到：1Panel 1.10.34+ (v2)"。
3. **对 `DashboardCurrent` 做「本地 slice」适配**：D1 用 `scope` 拉三次（`basic`/`ioNet`/`gpu`），D2/D3 一次拿全量后按同样三个 key 在客户端切分。UI 层只认 `BasicBlock/IONetBlock/GpuBlock`，把差异收敛在一个 Mapper 里。
4. **所有请求体字段用可空 + 默认值建模，响应解析忽略未知字段、缺失字段给默认值**：
   - 请求：`page/pageSize` 永远显式带上（三个家族都 `required`）；`state`/`orderBy`/`order` 仅在能力位允许时发送；`orderBy` 值由 `OrderByStyle` 决定发 `created_at` 还是 `createdAt`。
   - 响应：`appInstalldNumber` vs `appInstalledNumber`、`SystemProxy` vs `systemProxy`、`MemoryUsedPercent` vs `memoryUsedPercent` 用显式别名映射，不要依赖自动命名策略。
5. **认证只实现一套「MD5 + 秒级时间戳」，并为 2.3.2+ 预留 HMAC-SHA256**：客户端时间必须与服务器同步（±60s）；UI 上把「API 接口未开启」「IP 白名单为空/不含本机」「时间不同步」三种 401 分开提示——它们都返回 401，但 `message` 不同（`ErrApiConfigStatusInvalid` / `ErrApiConfigIPInvalid` / `ErrApiConfigKeyTimeInvalid` / `ErrApiConfigKeyInvalid`）。

---

## 12. 引用文件清单（便于复核）

| 家族 | tag/分支 | 关键文件 |
|---|---|---|
| D0 | `release-1.0` | `backend/init/router/router.go`、`backend/router/{ro_base,ro_dashboard,ro_container,ro_app,ro_website,ro_monitor}.go`、`backend/middleware/session.go`、`backend/app/dto/{container,dashboard,auth,common_res}.go`、`backend/app/dto/request/{app,website}.go`、`backend/app/dto/response/website.go`、`backend/constant/{app,session}.go` |
| D1 | `v1.10.33-lts`（`abd3c245`，2025-12-04） | `backend/init/router/router.go`、`backend/router/ro_*.go`、`backend/middleware/{session,jwt}.go`、`backend/app/dto/{container,dashboard,auth}.go`、`backend/app/dto/request/{app,website}.go`、`backend/app/dto/response/{app,website}.go`、`backend/app/api/v1/{dashboard,container,app_install,website}.go`、`backend/app/service/{dashboard,website,website_utils,auth}.go`、`backend/constant/{app,website,session,errs}.go`、`frontend/src/views/container/container/index.vue` |
| D2 | `v1.10.34-lts`（`2c92226f`，2026-01-04） | `core/init/router/{router,proxy}.go`、`core/middleware/{api_auth,session,helper}.go`、`core/router/ro_base.go`、`core/app/dto/auth.go`、`core/constant/session.go`、`agent/init/router/router.go`、`agent/router/ro_{container,app,website,dashboard,host}.go`、`agent/app/dto/{container,dashboard,app,common_req}.go`、`agent/app/dto/request/{app,website}.go`、`agent/app/dto/response/{app,website}.go` |
| D3 | `v2.3.2`（`65243c68`，2026-09-24；`dev-v2` 同） | `core/init/router/{router,proxy}.go`、`core/middleware/helper.go`、`core/app/auth/{api_auth,api_key}.go`、`core/app/dto/auth.go`、`core/router/ro_base.go`、`agent/router/ro_{container,app,website,dashboard,host}.go`、`agent/app/dto/{container,dashboard,app,common_res}.go`、`agent/app/dto/request/{app,website}.go`、`agent/app/dto/response/{app,website}.go`、`agent/app/service/{dashboard,app_install,website,website_utils}.go`、`agent/app/api/v2/helper/helper.go`、`frontend/src/api/modules/{container,dashboard,app,website,host}.ts`、`frontend/src/views/home/index.vue` |

### 未覆盖/存疑（如实标注）

- `v2.0.x`/`v2.1.x`/`v2.2.x` 只做了 DTO/路由/认证关键字级别的抽查（§2.1），未逐字段全量 diff。
- `dev` / `dev-v2` 分支为滚动分支，结论基于 2026-10-05 抓取；`dev-v2` 与 `v2.3.2` 在本次覆盖的 DTO 上一致。
- `DashboardBase` 中 `prettyDistro`/`agentNumber`/`cpuMhz`/`quickJump` 的引入版本：`cpuMhz`/`quickJump` 已确认 `v1.10.34-lts` 即存在（`agent/app/dto/dashboard.go:26,28`）；`agentNumber` 确认 `v2.1.13` 起；`prettyDistro` 在 `v1.10.34-lts` 与 `v2.1.0` 存在、`v2.0.0` 与 `v1.10.33-lts` 不存在（已逐 tag grep 确认，故不可作为版本判据）。
- 未验证真实的线上面板实例（本机无面板可测），所有结论均来自源码；如需最终确认，建议 Lead 用一台真实 2.3.x + 一台 1.10.33 面板做一次 `GET /dashboard/base/os` 与 `POST/GET /dashboard/current` 的对比实测。
