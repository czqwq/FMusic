# FMusic 对外 API (供其他模组调用)

> 入口类: `com.Lilith.FMusic.api.FMusicApi` (全部静态方法, 服务端可用)
> 适用: Minecraft 1.7.10 Forge; FMusic 已安装并启动(单人集成服务器/专用服务器均可)

## 目录
- [1. 引入方式](#1-引入方式)
- [2. 快速开始](#2-快速开始)
- [3. 调用流程](#3-调用流程)
- [4. API 参考](#4-api-参考)
- [5. 返回类型](#5-返回类型)
- [6. 线程模型与注意事项](#6-线程模型与注意事项)
- [7. 完整示例](#7-完整示例)
- [8. 常见问题](#8-常见问题)

---

## 1. 引入方式

把 FMusic 作为依赖(编译期 + 运行期都由玩家安装的 FMusic 提供):

```kotlin
// build.gradle.kts
dependencies {
    // 方式一: 本地 jar (mods/ 里的 FMusic-<version>.jar)
    compileOnly(files("libs/FMusic-5.09.52.417.jar"))
  
   // 方式二: compileOnly(rfg.deobf('com.github.czqwq:FMusic:版本号:dev'))
  // 不保证能跑
}
```

```java
import com.Lilith.FMusic.api.FMusicApi;
import com.Lilith.FMusic.api.AddResult;
```

**注意**: 只在**服务端**调用(集成服务器也可以)。API 类是纯服务端代码, 客户端不要引用。

---

## 2. 快速开始

三步: **判断可用 → 调用 → 处理回调**

```java
// 1. 判断 FMusic 是否已启动
if (!FMusicApi.isEnabled()) {
    return;
}

// 2. 点歌 (默认音源, 异步; 回调可为 null)
FMusicApi.addMusic("1887467085", "Steve", result -> {
    // 3. 处理结果
    if (result.ok()) {
        System.out.println("点歌成功: " + result.song);
    } else {
        System.out.println("点歌失败: " + result.status + " " + result.message);
    }
});
```

指定音源 / 直接用分享链接:

```java
// qqmusic / kugou / netapi (用 FMusicApi.getApiIds() 查询)
FMusicApi.addMusic("qqmusic", "https://i.y.qq.com/v8/playsong.html?songid=329062147", "Steve", null);
FMusicApi.addMusic("kugou", "004St6qH2bhV2a", "Steve", null);
```

---

## 3. 调用流程

### 3.1 点歌 (`addMusic` / `addMusicSync`)

```
你的模组                     FMusic
   |                            |
   |-- addMusic(api, id, who) ->|
   |                            |-- 1. 判断 FMusic 是否运行 (isRun)
   |                            |-- 2. 查音源 (MUSIC_APIS)
   |                            |-- 3. 提交后台线程 (SaveTask)
   |                            |-- 4. 解析 ID: 链接/短ID -> 音源内部 ID (getMusicId)
   |                            |-- 5. 校验: checkId / 列表未满 / 未被禁 / 不重复 /
   |                            |      玩家未超限 / 未被禁 / 有可接收玩家
   |                            |-- 6. 拉取歌曲信息 (getMusic, 网络请求)
   |                            |-- 7. 触发 MusicAddEvent (其他模组可取消)
   |                            |-- 8. 入队 (PlayMusic.addTask)
   |<-- 回调 AddResult ----------|
   |                            |
   |                            |-- 播放线程: 取队首 -> 获取播放地址 -> 推给客户端
```

- **不会立即播放**: FMusic 按队列顺序播放, 队列为空且当前无歌时才会开始播这首
- 回调在**后台线程**触发, 拿到的 `AddResult.song` 包含解析后的歌曲信息
- 想等结果再继续(例如自己的命令处理): 用 `addMusicSync(...)` (阻塞, 请在后台线程调用)

### 3.2 搜索 (`search` / `searchSync`)

```
search(api, keyword, callback)   -> 独立线程执行
searchSync(api, keyword)         -> 当前线程阻塞执行(网络请求, 不要在服务器主线程调用)
```

返回 `List<SearchResult>` (最多 10 条, 与游戏内 /music search 第一页一致)。

### 3.3 投票 (`startSwitchVote` / `startPushVote` / `agreeVote`)

```
发起: startSwitchVote("Steve")                 // 对当前播放的歌发起切歌投票
      startPushVote("netapi", "123456", "Steve")  // 对队列里的歌发起插歌投票(通过后移到队首)
同意: agreeVote("Alex")                        // 每人一次; 到 needCount 自动执行
取消: cancelVote("Steve", false)               // false=切歌投票, true=插歌投票
查询: getCurrentVote() / getQueuedVoteCount()
```

- 投票是**序列**: 同一时间只处理一个, 其余排队(`voteList`), 逐个计时/执行
- 通过条件: 同意人数 >= `min(配置 vote.minVote, 在线人数)`
- 超时/歌曲结束会自动终止并处理下一个

### 3.4 直链即时播放 (`playUrl` / `playUrlAll`)

```
playUrl("Steve", "http://example.com/a.mp3")   // 只对 Steve 播放
playUrlAll("http://example.com/a.mp3")         // 对所有人播放
```

**不入队、不做时长/歌词/进度处理**(队列播放依赖音源返回的时长)。
需要进度条/歌词/投票时请用 `addMusic` 传音源歌曲 ID。

---

## 4. API 参考

### 4.1 状态查询

| 方法 | 说明 |
|---|---|
| `boolean isEnabled()` | FMusic 是否已启动(所有调用前都应判断) |
| `String getVersion()` | FMusic 版本号 |
| `List<String> getApiIds()` | 已注册音源 ID(netapi / qqmusic / kugou / api 目录外部 jar) |
| `boolean hasApi(String apiId)` | 指定音源是否可用 |

### 4.2 点歌

| 方法 | 说明 |
|---|---|
| `void addMusic(String musicId, String requester, AddCallback cb)` | 用**默认音源**点歌(异步) |
| `void addMusic(String apiId, String musicId, String requester, AddCallback cb)` | 用**指定音源**点歌(异步) |
| `AddResult addMusicSync(String apiId, String musicId, String requester)` | 同步点歌(阻塞, 后台线程调用) |

`musicId` 支持: 音源歌曲 ID / songmid / 歌曲 hash / 分享链接(如 `https://i.y.qq.com/v8/playsong.html?songid=329062147`)
`requester` 为点歌者名字(用于队列显示/个人限额/禁言判断), 可为空字符串

### 4.3 直链播放

| 方法 | 说明 |
|---|---|
| `void playUrl(String player, String url)` | 对指定玩家即时播放直链 |
| `void playUrlAll(String url)` | 对所有玩家即时播放直链 |

### 4.4 搜索

| 方法 | 说明 |
|---|---|
| `void search(String keyword, SearchCallback cb)` | 默认音源搜索(异步) |
| `void search(String apiId, String keyword, SearchCallback cb)` | 指定音源搜索(异步) |
| `List<SearchResult> searchSync(String apiId, String keyword)` | 同步搜索(阻塞) |

### 4.5 投票

| 方法 | 说明 |
|---|---|
| `AddResult startSwitchVote(String requester)` | 对当前歌发起切歌投票 |
| `AddResult startPushVote(String apiId, String musicId, String requester)` | 对队列中的歌发起插歌投票 |
| `AddResult agreeVote(String player)` | 同意当前投票 |
| `boolean cancelVote(String requester, boolean push)` | 取消自己发起的投票 |
| `VoteInfo getCurrentVote()` | 当前投票信息(无则 null) |
| `int getQueuedVoteCount()` | 排队中的投票数量 |

### 4.6 播放控制

| 方法 | 说明 |
|---|---|
| `void skip()` | 强制切歌到下一首 |
| `void stopAll()` | 停止所有客户端播放并清空队列 |
| `void clearQueue()` | 清空队列(不影响当前播放) |
| `int getQueueSize()` | 队列长度 |
| `List<String> getQueueList()` | 队列内容("歌名 - 作者") |
| `boolean removeFromQueue(int index)` | 按序号删除(从 1 开始) |
| `void resyncPlayer(String player)` | 让玩家重新同步当前播放(重发 PLAY/POS) |

### 4.7 当前播放

| 方法 | 说明 |
|---|---|
| `boolean isPlaying()` | 是否有歌在播放 |
| `NowPlaying getNowPlaying()` | 当前歌曲信息(歌名/作者/音源/时长/进度/链接) |

### 4.8 消息与 HUD

| 方法 | 说明 |
|---|---|
| `void sendMessage(String player, String miniMessageText)` | 给玩家发消息(MiniMessage 格式, 支持颜色/按钮) |
| `void broadcast(String miniMessageText)` | 广播消息 |
| `HudPosObj getHud(String player)` | 玩家 HUD 配置 |
| `void setHud(String player, HudPosObj hud)` | 修改玩家 HUD 配置 |
| `void updateHud(String player)` | 刷新玩家 HUD 显示 |

---

## 5. 返回类型

### 5.1 AddResult

```java
result.ok()          // 是否成功
result.status        // 失败原因枚举
result.message       // 详细信息
result.song          // 成功时的歌曲信息 (api/id/name/author/album/alia/requester/lengthMs/picUrl/trial)
```

| Status | 含义 |
|---|---|
| `SUCCESS` | 成功入队 |
| `NOT_RUNNING` | FMusic 未启动 |
| `API_MISSING` | 音源不存在(检查 `getApiIds()`) |
| `INVALID_ID` | 歌曲 ID/链接无法解析 |
| `PARSE_FAILED` | 歌曲信息解析失败(网络/版权/音源变更) |
| `NO_PLAY_URL` | 播放地址获取失败 |
| `LIST_FULL` | 播放列表已满(`limit.maxPlayList`) |
| `SONG_BANNED` | 该歌被管理员禁止 |
| `DUPLICATE` | 歌曲已在队列中 |
| `PLAYER_LIMIT` | 该玩家点歌数量达到上限 |
| `PLAYER_BANNED` | 该玩家被禁止点歌 |
| `NO_PLAYER` | 当前没有可接收音乐的玩家 |
| `EVENT_CANCELLED` | 被 `MusicAddEvent` 取消(其他模组拦截) |

### 5.2 其他

- `SearchResult`: `api / id / name / author / album`
- `NowPlaying`: `playing / api / id / name / author / album / alia / requester / lengthMs / nowMs / url / trial`
- `VoteInfo`: `type(SWITCH|PUSH) / api / id / sender / agreeCount / needCount / remainSeconds / queuedCount`

---

## 6. 线程模型与注意事项

1. **异步方法**(带 callback)内部使用 FMusic 的 `SaveTask` 或独立线程执行网络请求, **回调在后台线程触发**——
   不要直接在回调里操作世界/实体, 需要主线程时用 `MinecraftServer` 调度任务。
2. **同步方法**(`addMusicSync` / `searchSync`)会阻塞当前线程做网络请求(通常数百毫秒),
   **不要在服务器主线程调用**, 请在后台线程使用。
3. **队列与播放由 FMusic 管理**: `addMusic` 成功后不代表立刻播放, 请用 `getNowPlaying()`/`isPlaying()` 观察。
4. **事件可拦截**: FMusic 会触发 `MusicAddEvent`(点歌)/`MusicPlayEvent`(播放),
   其他模组可取消; 你的调用可能因此返回 `EVENT_CANCELLED`。
5. **单人游戏同样可用**: 集成服务器进程内也能调用(你的模组同时装客户端时注意判断 `isEnabled()`)。
6. **版本兼容**: API 只依赖 `com.Lilith.FMusic.api` 包, 内部实现(PlayMusic/IMusicApi 等)可能变动,
   请只使用本包内的方法与类型。

---

## 7. 完整示例

一个"自定义方块右键点歌"的示例:

```java
import com.Lilith.FMusic.api.AddResult;
import com.Lilith.FMusic.api.FMusicApi;
import com.Lilith.FMusic.api.SearchResult;

public class ExampleCaller {

    /** 玩家点了一首 QQ 音乐的分享链接 */
    public void requestByLink(String playerName, String url) {
        if (!FMusicApi.isEnabled()) {
            return;
        }
        FMusicApi.addMusic("qqmusic", url, playerName, new FMusicApi.AddCallback() {
            @Override
            public void onResult(AddResult result) {
                if (!result.ok()) {
                    // 回调在后台线程: 需要发消息时交给主线程
                    FMusicApi.sendMessage(playerName, "<red>点歌失败: " + result.status);
                }
            }
        });
    }

    /** 先搜索再让玩家选择 */
    public void searchThenPick(String playerName, String keyword) {
        FMusicApi.search("netapi", keyword, results -> {
            if (results.isEmpty()) {
                FMusicApi.sendMessage(playerName, "<yellow>没有搜到: " + keyword);
                return;
            }
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                FMusicApi.sendMessage(playerName, "<yellow>" + (i + 1) + ". " + r.name + " - " + r.author);
            }
            SearchResult first = results.get(0);
            FMusicApi.addMusic(first.api, first.id, playerName, null);
        });
    }

    /** 发起切歌投票 / 同意投票 */
    public void voteFlow(String playerName) {
        AddResult r = FMusicApi.startSwitchVote(playerName);
        if (r.ok()) {
            FMusicApi.broadcast("<yellow>" + playerName + " 发起了切歌投票");
        }
        // 其他玩家同意
        FMusicApi.agreeVote("Alex");
    }

    /** 观察播放状态(可在自己的 tick 里调用) */
    public void printState() {
        if (FMusicApi.isPlaying()) {
            System.out.println("正在播放: " + FMusicApi.getNowPlaying());
            System.out.println("队列: " + FMusicApi.getQueueList());
        }
    }
}
```

---

## 8. 常见问题

**Q: 返回 `NOT_RUNNING`?**
FMusic 未加载或服务端还没启动音乐系统。先判断 `isEnabled()`, 并确认玩家端装的是同一个 FMusic。

**Q: 返回 `NO_PLAYER`?**
表示当前没有"可接收音乐"的玩家(所有玩家都在单人游戏里关掉了接收、或被静音)。属于 FMusic 的正常保护。

**Q: 返回 `INVALID_ID`, 但链接在浏览器能打开?**
音源只解析"歌曲页链接/歌曲 ID", 不支持歌单/歌手页/短链跳转。可先用 `searchSync` 拿 ID 再点歌。

**Q: 想在歌进入队列后做点什么?**
监听 Forge 事件 `MusicAddEvent`(点歌时触发, 可取消) 或 `MusicPlayEvent`(开始播放时触发),
事件类在 `com.Lilith.FMusic.server.event`(`MusicAddEvent` / `MusicPlayEvent`, 随 FMusic 提供)。

**Q: 客户端也要显示?**
不用做任何事: FMusic 客户端负责 HUD/播放, 你的模组只需在服务端调用 API。
