# PanelOne · 1Panel 安卓客户端

一个用 **原生 Kotlin + Jetpack Compose** 写的 1Panel 面板客户端，通过 1Panel 官方 API（`/api/v1/*`）
管理服务器：看监控、管容器、管应用、管网站。

> ⚠️ **本机（1 核 1G）绝对不要构建**。本仓库的所有 Gradle 操作都应放在 GitHub Actions 云端或你自己的电脑上完成。
> 仓库里不包含任何构建产物，也不需要在部署机上安装 Android SDK / JDK。

---

## 一、拿到 APK 的三种方式（按推荐排序）

### 方式 1：GitHub Actions 云端构建（推荐，本机零负担）

```bash
# 在能上网的机器上，把本仓库推到你的 GitHub
git init && git add -A && git commit -m "feat: PanelOne 1Panel 安卓客户端"
git branch -M main
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

推送后 GitHub **自动开始构建**（配置见 `.github/workflows/android.yml`）：

1. 打开仓库 → **Actions** → 左侧 `Build APK`；
2. 等约 3~6 分钟（首次会下载依赖，之后有缓存会快很多）；
3. 进入该次运行页面底部 **Artifacts** → 下载 `PanelOne-apk`；
4. 解压得到两个 APK：
   - `app-debug.apk`（debug 包，包名带 `.debug`，可与正式包共存）
   - `app-release.apk`（release 包，使用 debug 签名，可直接安装）

也可以直接在 GitHub 网页上 **Add file → Upload files** 把整个目录传上去，不需要命令行。

> 想手动触发：Actions → Build APK → **Run workflow**。

### 方式 2：Android Studio（自己的电脑）

1. 安装 Android Studio（自带 JDK 17 与 Android SDK）；
2. `File → Open` 选择本目录；
3. 等 Gradle Sync 完成后 → `Build → Build Bundle(s)/APK(s) → Build APK(s)`。

### 方式 3：命令行（自己的电脑，需要 JDK 17 + Android SDK）

```bash
./gradlew assembleDebug      # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # 产物：app/build/outputs/apk/release/app-release.apk
```

**不要**在 1 核 1G 的服务器上执行上面两条命令：Gradle + Kotlin 编译进程至少需要 2G 内存。

### 不构建也能先看代码是否健康

```bash
node tools/check-static.mjs
```

这是随仓库提供的零依赖自检脚本：检查 XML 良构性、Android 资源引用是否齐全、Kotlin 跨包 import 是否可解析、
括号是否平衡、块注释与字符串是否闭合、属性 setter 与同类函数是否有 JVM 签名冲突。CI 里也会先跑它，再开始构建。

> ⚠️ **注释里不要写 `**/api/v1**` 这种「加粗路径」**。Kotlin 的块注释遇到 `*/` 就结束，
> 于是 `**/api/v1**` 中间的 `*/` 会把 KDoc 提前截断，后面的正文被当成代码，
> 编译器报一长串 `Expecting a top level declaration`。写成 `` `/api/v1` `` 即可，自检脚本会拦住这种写法。
>
> ⚠️ **ViewModel 里不要写 `fun setXxx(...)`**。`var xxx` 自身就会生成 `setXxx()`，
> 两者 JVM 签名相同，编译器报 `Platform declaration clash`。改用 `fun updateXxx(...)`。

---

## 二、手机端首次使用

1. **面板侧准备**（网页端登录 1Panel）：
   - 进入「面板设置 → API 接口」→ **打开接口开关**（不开的话所有请求都会返回 401）；
   - 点「创建密钥」，复制生成的 **API 密钥**；
   - 把手机的出口 IP 加到 **IP 白名单**（`0.0.0.0/0` 表示不限制，安全性较低，建议填具体 IP）。
2. **APP 侧**：打开 PanelOne → 右下角「添加服务器」→ 填写
   - 面板地址：`http://192.168.1.10:12345`（不带 `/api/v1`，APP 会自动补全路径）
   - API 密钥：上一步复制的密钥
   - 面板版本：不确定就选 **自动**
   - 面板是自签名 HTTPS 证书时，打开「忽略证书错误」
3. 点 **测试连接**，看到「连接成功 · 识别为 x.y.z」即可保存。

### 版本选择（自动 / v1 / v2 / v3 / v4）

1Panel 至今只有**两代接口**，而且分界线不等于主版本号（官方 `v1.10.34-lts` 这个 tag 实际已经是新架构）：

| 选项 | 对应面板 | 接口前缀 | 实时占用接口 | 列表排序字段 |
| --- | --- | --- | --- | --- |
| **v1 · 经典** | 1.1 ~ 1.10.33（含 1.10.33 LTS） | `/api/v1` | `POST /dashboard/current`（带 body） | `created_at` |
| **v2 · 新架构** | 1.10.34 及以上、2.x（`core/`+`agent/` 架构） | `/api/v2`（面板自身接口在 `/api/v2/core`） | `GET /dashboard/current/{io}/{net}` | `createdAt` |
| **v3 / v4** | 官方尚未发布（最新为 2.3.x） | — | 预留槽位，当前按 v2 契约执行 | — |

- **自动（默认，推荐）**：先用两个**免认证指纹**判断属于哪一代——
  `GET /health` 返回 `ok` ⇒ v1 经典；`GET /api/v2/core/auth/setting` 返回正常 JSON ⇒ v2 新架构；
  指纹被「安全入口」挡住时改用密钥请求版本号接口判定，最后才用接口形态兜底；
  判定完再读一次 `systemVersion`，**只用于展示**。家族永远以接口探测为准，不按版本号猜。
- **手动指定**：知道自己是哪一代时直接选，省掉探测请求。选错时 APP 会提示「接口不存在」或版本号与所选家族不一致。
- **1.0 ~ 1.9**：不支持 API 密钥认证，自动探测到会明确提示升级面板。

家族差异（前缀、`current` 形态、排序字段）全部收敛在 `data/PanelVersion.kt` 的 `PanelDialect` 里，
界面代码不含任何版本判断；官方将来发布 v3/v4 时只需补一个方言。

---

## 三、功能

| 页面 | 能力 |
| --- | --- |
| 概览 | 主机名/系统/内核/CPU 型号/IP/运行时长；CPU 使用率 + 最近 60 次采样折线；负载 1/5/15；内存与 Swap；各挂载点磁盘占用；网络收发与磁盘 IO；网站/应用/数据库/计划任务数量。5 秒自动刷新。 |
| 容器 | 名称搜索、状态筛选（全部/运行中/已停止）、分页加载；每个容器显示镜像、状态、端口、实时 CPU/内存；一键启动/停止，菜单里可重启、暂停、恢复、删除（删除有二次确认）。 |
| 应用 | 已安装应用列表（应用名、安装名、版本、状态、端口、目录、可升级标记），支持启动/停止/重启与按名称搜索。 |
| 网站 | 网站列表（域名、类型、协议、反向代理、到期时间、目录），支持启动/停止与按域名搜索。 |
| 设置 | 当前连接信息与版本切换、服务器增删改、密钥明文存储等安全提示。 |

多服务器：可保存多台面板，随时切换；版本选择、忽略证书等配置按服务器分别保存。

---

## 四、接口契约（来自 1Panel 官方源码）

认证方式与官方实现一致（经典家族 `backend/middleware/session.go`，新架构 `core/app/auth/api_auth.go`）：

```
1Panel-Timestamp: <秒级 Unix 时间戳>
1Panel-Token:     md5("1panel" + apiKey + timestamp)   // 小写十六进制
```

> 新架构（2.3.x）额外支持 `1Panel-Signature-Version: hmac-sha256`，但**仍然兼容上面的 md5 形式**，所以 APP 只实现一种即可。

面板侧还会校验：`API 接口` 开关是否开启、请求来源 IP 是否在白名单内（**白名单为空一律拒绝**，这是 401 最常见原因）、
时间戳是否在「密钥有效期」容差内。响应统一为 `{"code":200,"message":"success","data":{...}}`；
注意 **1Panel 的错误响应 HTTP 状态码也是 200**、错误码在 JSON 的 `code` 字段，未注册路径甚至可能返回 200 + HTML，
所以 APP 的判定条件是「JSON 外壳 + `code == 200`」。

用到的接口（相对路径两代一致，差异只有前缀与 `current` 形态）：

| 用途 | 路径（相对） | 经典 v1 | 新架构 v2 |
| --- | --- | --- | --- |
| 系统信息 | `/dashboard/base/os` | GET | GET |
| 基础信息（主机信息+数量统计） | `/dashboard/base/all/all` | GET | GET |
| 实时占用 | `/dashboard/current` | POST + `{scope,ioOption,netOption}` | **GET `/dashboard/current/all/all`**（无 body） |
| 容器列表 | `/containers/search` | POST | POST |
| 容器实时占用 | `/containers/list/stats` | GET | GET |
| 容器操作 | `/containers/operate` | POST `{names:[...],operation}` | POST 同左 |
| 已安装应用 | `/apps/installed/search` | POST | POST |
| 应用操作 | `/apps/installed/op` | POST `{installId,operate}` | POST 同左 |
| 网站列表 | `/websites/search` | POST | POST |
| 网站操作 | `/websites/operate` | POST `{id,operate}` | POST 同左 |
| 面板版本 | `/settings/search`（无参数）→ `data.systemVersion` | POST（前缀 `/api/v1`） | POST（前缀 `/api/v2/core`） |
| 家族指纹 | `/health` / `/core/auth/setting` | `/health` 返回 `ok` | `/api/v2/core/auth/setting` 返回 JSON |

调研明细见 [`docs/research/`](docs/research/)（版本差异矩阵、运行时探测方案），编译风险审计见 [`docs/review/`](docs/review/)。

---

## 五、目录结构

```
app/src/main/java/com/panelone/client/
├── MainActivity.kt            # 入口 + 底栏 5 个 Tab 的脚手架
├── data/
│   ├── Models.kt              # 所有 @Serializable 数据模型（字段与 1Panel json tag 对齐）
│   ├── PanelClient.kt         # OkHttp 客户端：签名、错误映射、超时、自签名证书
│   ├── PanelApi.kt            # 高层接口（概览/容器/应用/网站）+ 版本识别与解析
│   ├── PanelVersion.kt        # 版本线 + 接口方言（版本差异都收敛在这里）
│   └── ServerStore.kt         # 服务器配置持久化 + 全局会话状态 Repo
├── ui/                        # 5 个页面 + 通用组件 + 主题
└── vm/Vms.kt                  # ViewModel：加载/错误/分页/操作中状态
tools/check-static.mjs         # 零依赖静态自检
.github/workflows/android.yml  # 云端构建 APK
```

想加页面/换版本方言，只改 `data/PanelVersion.kt` 与 `ui/` 即可，不需要动 ViewModel 之外的其它层。

---

## 六、安全说明

- API 密钥以**明文**保存在本机 `SharedPreferences`（`panelone`）中。请勿把手机借给他人、勿截图外发；
  面板侧可随时「重置密钥」，重置后旧密钥立即失效。
- 建议只在可信内网或 VPN 下使用本 APP；面板本身建议配置 HTTPS。
- `IP 白名单` 尽量填具体 IP，不要长期使用 `0.0.0.0/0`。
- 「忽略证书错误」会关闭 TLS 校验，只建议在自签名证书的内网环境临时开启。
- 本 APP 只调用 1Panel 官方接口，不采集、不上传任何数据，也不含任何统计 SDK。

---

## 七、排错

| 现象 | 原因与处理 |
| --- | --- |
| `未授权（检查面板「设置 → API 接口」…）` | 接口开关没开 / 密钥错误 / 手机出口 IP 不在白名单 / 手机与面板时间差太大。 |
| `接口不存在：可能是面板版本选错了` | 手动选错了版本，改回「自动」或选择实际代际。 |
| `TLS/证书错误` | 面板是自签名证书：在服务器设置里打开「忽略证书错误」，或给面板换正式证书。 |
| `面板返回了非 JSON 内容` | 地址/端口写错，或面板开了「安全入口」导致路径被改写；确认地址形如 `http://ip:端口`。 |
| `网络错误：failed to connect` | 手机和面板不在同一网络；面板防火墙未放行端口。 |
| 容器应用列表能看但操作失败 | 面板 API 密钥对应的权限/版本限制；看 APP 弹出的具体 message（来自面板自身）。 |
| 检测到 1.0~1.9 老版本 | 该版本不支持 API 密钥，请升级到 1.10 LTS 及以上。 |

---

## 八、开发约定

- 所有网络字段都给了默认值 + `ignoreUnknownKeys`，面板升级新增/改名字段不会导致 APP 崩溃。
- 版本差异只允许出现在 `PanelDialect`（`data/PanelVersion.kt`），禁止在 UI 里写 `if (版本 == ...)`。
- 提交前请先跑 `node tools/check-static.mjs`；真正的编译校验交给 CI。

## 九、免责声明

本项目是非官方第三方客户端，与 FIT2CLOUD / 1Panel 官方无关联。请遵守你所在环境的安全规范使用。
