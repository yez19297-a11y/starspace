# 架构说明

## 总览

```
┌──────────────────────────────┐
│        Android 客户端         │
│  Compose UI / AppStore       │
│  SharedPreferences(JSON)     │
│         │                    │
│    ┌────┴────┐               │
│  直连      后端中转           │
└────┬────────────┬────────────┘
     │            │
     ▼            ▼
 OpenAI 兼容   FastAPI + SQLite
   模型服务     ├─ /v1/chat         聊天
                ├─ /v1/personas     角色同步
                ├─ /v1/space/posts  动态
                └─ scheduler        定时发动态
```

## 客户端

| 模块 | 职责 |
|---|---|
| `data/AppStore.kt` | 单例状态容器，整份数据序列化成 JSON 存 SharedPreferences |
| `data/ApiClient.kt` | OkHttp 调用：直连模型、后端接口、连接测试 |
| `data/Models.kt` | 数据模型、模型目录、演示数据 |
| `ui/StarSpaceApp.kt` | 路由、会话状态、AI 回复编排、通知触发 |
| `ui/Theme.kt` | 浅色 / 深色配色，`AppTheme.colors` |
| `notify/` | 通知渠道、系统通知、后台动态检查（WorkManager） |

数据规模按个人使用设计：全部数据一次性读入内存，写入时整体覆盖。消息量特别大时应改为 Room。

## 后端

FastAPI + 标准库 sqlite3，启动时自动建表：

- `personas`：角色与发帖排期（`next_post_at`）
- `posts` / `comments`：空间动态与评论
- `messages` 不落库，聊天历史始终由客户端携带

调度器是一个 `asyncio` 循环，每 `SCHEDULER_POLL_SECONDS` 秒检查到期角色，调用模型生成动态后随机排下一次。

## LLM 调用策略

- **直连**：客户端拿 Key 直接请求 `{baseUrl}/chat/completions`，不经过后端
- **中转**：客户端请求后端 `/v1/chat`，后端用 `.env` 或运行时下发的配置调用模型
- 群聊时每个成员单独请求一次，由模型自行输出 `[SKIP]` 决定是否发言；被 `@` 的成员跳过判断，强制回复
- 评论回复优先用当前配置：直连则在本地生成后回写后端，中转则调用 `/ai-comments`

## 数据与隐私

客户端不向除「你配置的模型服务」和「你的后端」之外的任何地址发请求。没有账号服务器、没有统计上报。
