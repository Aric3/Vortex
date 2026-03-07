# 登录与鉴权功能修改说明

本文档记录为支持「登录后获得并缓存股东号」及「注册 + 管理员账号」所进行的前端修改：新增文件与修改文件一览。

---

## 一、新增文件

| 文件路径 | 说明 |
|---------|------|
| `vortex-ui/src/stores/auth.ts` | 鉴权 Pinia Store：保存 `token`、`user`（**用户名为股东号**）；内置管理员账号股东号 `A000000000`、密码 `123456`；已注册用户存于 localStorage（`vortex-users`）；提供 `login()`、`logout()`、`verifyLocal()`、`register()`、`isLoggedIn()`，登录/刷新时将 `user.shareholderId` 同步到 filter store。 |
| `vortex-ui/src/views/LoginPage.vue` | 登录页：**股东号**（10 位）+ 密码；先本地校验（管理员或已注册用户），再可选调后端 `POST /api/v1/auth/login`；底部「没有账号？去注册」跳转 `/register`。 |
| `vortex-ui/src/views/RegisterPage.vue` | 注册页：股东号（10 位）、密码、确认密码；调用 `auth.register()` 写入本地 `vortex-users`，不可注册与管理员同号；底部「已有账号？去登录」跳转 `/login`。 |
| `docs/LOGIN_AUTH_CHANGES.md` | 本说明文档。 |

---

## 二、修改文件

| 文件路径 | 修改内容 |
|---------|----------|
| `vortex-ui/src/router/index.ts` | 新增路由：`/login`、`/register`（均为 `meta: { public: true }`）。`beforeEach`：未登录访问非公开页重定向到 `/login`；已登录访问登录/注册页重定向到 `/`。 |
| `vortex-ui/src/services/http.ts` | 请求拦截器：若存在 `token` 则设置 `Authorization: Bearer <token>`。 |
| `vortex-ui/src/views/Layout.vue` | 侧栏展示当前用户（用户名为股东号）、股东号、退出登录。 |
| `README.md` | 「3. 登录与鉴权」中说明未登录重定向、后端登录接口约定等；已去掉演示模式/提示词相关描述。 |

---

## 三、行为简述

- **管理员账号**：股东号 `A000000000`，密码 `123456`，仅用于登录，不可通过注册覆盖。
- **登录**：输入股东号（10 位）与密码；先校验本地（管理员或已注册用户），通过则直接登录；否则再请求后端登录接口（若存在）。
- **注册**：输入股东号、密码、确认密码，写入本地；该股东号不可与管理员重复、不可与已注册重复；注册成功后跳转登录页。
- 用户名称即股东号，登录后侧栏与缓存均以股东号展示；刷新后登录态与股东号从 localStorage 恢复。
- 已去掉原「演示模式」等 AI 功能提示词文案。

---

## 四、未改动的相关文件（参考）

- `vortex-ui/src/views/TradingConsolePage.vue`：仍通过 `filter.shareholderId` / `filter.securityId` 传给 `TradingConsole`，登录后由 auth store 同步到 filter，无需改页面代码。
- `vortex-ui/src/stores/filter.ts`：未改；auth 登录时仅调用其 `shareholderId`、`securityId` 的赋值，原有持久化逻辑不变。
