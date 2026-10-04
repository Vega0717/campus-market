# 校园二手交易平台 campus-market

> Spring Boot 3.5 · MyBatis · MySQL · Redis · Vue 3 · Vite

一个面向校园场景的二手闲置交易平台：学生可以发布自己的闲置物品，按关键词和状态检索，联系卖家成交。

这是我个人从零独立完成的项目，目的不是"做一个大而全的系统"，而是**完整走一遍真实项目的全流程**：需求拆解 → 接口设计 → 业务实现 → 加缓存调优 → 打包 → 前端对接。每一处设计决策和踩过的坑，在下面的「实现要点」里都有说明。

## 功能

- 商品发布、编辑、删除
- 分页列表（关键词搜索 + 状态筛选）
- 商品详情
- 标记已售出 / 重新上架
- 统一响应体 `{code, msg, data}` + 全局异常处理
- Redis 缓存热点查询，并提供缓存命中率统计接口

## 技术栈

| 分层 | 选型 |
|---|---|
| 后端框架 | Spring Boot 3.5.7、Spring MVC |
| 持久层 | MyBatis（XML 映射，手写动态 SQL 与分页） |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7 + Spring Cache，Lettuce 连接池 |
| 前端 | Vue 3（`<script setup>`）、Vite、axios |
| 构建 | Maven、npm |
| 其他 | Lombok、Jakarta Validation、JUnit 5 + H2 |

## 项目构成

前后端分成两个仓库，本仓库是**后端**：

| 仓库 | 说明 |
|---|---|
| `campus-market`（本仓库） | Spring Boot 3 后端，提供 REST 接口 |
| [`campus-market-web`](https://github.com/Vega0717/campus-market-web) | Vue 3 前端，列表页 / 搜索 / 发布表单 |

## 目录结构

```
campus-market
├── pom.xml
└── src
    ├── main
    │   ├── java/com/lzit/campus/
    │   │   ├── common/                     # Result / PageResult 统一响应体
    │   │   ├── config/RedisConfig.java     # Redis 序列化与缓存管理器配置
    │   │   ├── controller/                 # ItemController / CacheController
    │   │   ├── entity/Item.java
    │   │   ├── exception/                  # BizException + 全局异常处理
    │   │   ├── mapper/                     # 数据访问层
    │   │   └── service/                    # 业务逻辑 + 缓存读写
    │   └── resources/
    │       ├── application.yml             # 数据源 / Redis / MyBatis 配置
    │       ├── schema.sql                  # 建表
    │       ├── data.sql                    # 10 条测试数据
    │       └── mapper/ItemMapper.xml       # SQL 映射
    └── test/java/com/lzit/campus/
```

## 快速开始

**环境要求**：JDK 17、Maven 3.6.3+、MySQL 8、Redis 7

```bash
# 1. 建库（库名 campus_market）
CREATE DATABASE campus_market DEFAULT CHARACTER SET utf8mb4;

# 2. 按需修改 src/main/resources/application.yml 里的数据库账号密码

# 3. 启动（首次启动会自动执行 schema.sql 建表、data.sql 灌 10 条测试数据）
mvn spring-boot:run

# 4. 前端单独拉一个仓库再起
git clone https://github.com/Vega0717/campus-market-web.git
cd campus-market-web && npm install && npm run dev     # 打开 http://localhost:5173
```

打包部署：

```bash
mvn clean package -DskipTests
java -jar target/campus-market-0.0.1-SNAPSHOT.jar
```

## 接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/items` | 分页列表，支持 `keyword`、`status`、`page`、`size` |
| GET | `/api/items/{id}` | 商品详情（走缓存） |
| POST | `/api/items` | 发布商品（`@Valid` 参数校验） |
| PUT | `/api/items/{id}` | 修改商品 |
| POST | `/api/items/{id}/sold` | 标记已售出 |
| DELETE | `/api/items/{id}` | 删除商品 |
| GET | `/api/cache/stats` | 缓存命中率统计 |
| POST | `/api/cache/stats/reset` | 重置统计 |

所有接口统一返回：

```json
{ "code": 0, "msg": "ok", "data": {} }
```

`code = 0` 为成功；业务异常、参数校验失败、系统异常由 `GlobalExceptionHandler` 统一翻译成同样的结构，前端只需判断 `code` 即可。

## 实现要点

### 1. 缓存的 key 与过期策略

| 缓存 | key 示例 | TTL |
|---|---|---|
| 商品详情 | `campus:item:1` | 10 分钟 |
| 列表查询 | `campus:itemPage:教材:0:1:10` | 2 分钟 |

列表 key 由「关键词 + 状态 + 页码 + 每页条数」拼成，不同组合互不干扰；两档 TTL 是刻意的——列表变化频繁所以短，详情相对稳定所以长，同时也避免大量 key 同一时刻集体失效。

### 2. 写操作怎么保证缓存不脏

写库之后主动失效缓存（而不是写完顺手回填，那样并发下容易不一致）：

- 详情：按 key 精确删除 `campus:item:{id}`
- 列表：用户的查询组合无法预知，按前缀 `campus:itemPage:*` 批量删除

这里没有用 `@CacheEvict` 注解，因为它要求 key 是常量或 SpEL 能静态表达的，而列表 key 是运行期拼出来的，扫不到。

### 3. 缓存三大经典问题的应对

- **穿透**（查一个库里也没有的数据）：`disableCachingNullValues()`，不把 null 写进缓存，同时保留"查不到就查库"的正确语义
- **击穿**（热点 key 刚过期，并发同时打库）：详情方法用 `@Cacheable(sync = true)`，同一个 key 只放一个线程去查，其余等待
- **雪崩**（大量 key 同时过期）：不同业务不同 TTL，天然错峰

### 4. 缓存不能变成可用性依赖

所有 Redis 读写都包了 try-catch，异常时打日志并**降级为直接查库**，缓存挂了接口不会 500。此外 `RedisConfig`、`CacheStatsService` 都加了 `@ConditionalOnProperty` / `@ConditionalOnBean`，把 `spring.cache.type` 设成 `none` 就能在完全没有 Redis 的环境下启动和跑测试。

### 5. 序列化

默认的 JDK 序列化存进 Redis 是一堆乱码，没法排查问题。这里 key 用 `StringRedisSerializer`、value 用 `GenericJackson2JsonRedisSerializer`，在 `redis-cli` 里 key 长这样：`campus:itemPage:all:all:1:5`，一眼看得懂。

### 6. 实测数据

| 指标 | 结果 |
|---|---|
| 列表查询 P95（5000 条数据） | 4.1 ms → 0.57 ms |
| 缓存命中率（读写混合场景） | 50% |
| 后端 jar 体积 / 冷启动 | 38 MB / 4.157 s |
| 前端构建产物 | 71 模块，185 ms，JS 约 120 KB |

顺带记一个反直觉的发现：**数据量小时，详情查询加缓存反而更慢**。MySQL 主键查询走 Buffer Pool 本来就 0.4 ms 级别，而走 Redis 要经历网络往返和序列化反序列化，开销比查库还大。所以缓存解决的是"查询足够贵"的场景，列表那种带 `LIKE '%x%'` 的查询才有明显收益——不是无脑加就有用。

## 已知限制与后续计划

- `schema.sql` 里有 `DROP TABLE`，每次重启会把数据重置成初始 10 条（改成 `spring.sql.init.mode=never` 可保留数据）
- 列表缓存失效用的是 `KEYS` 命令，会阻塞 Redis 单线程；生产环境应换成 `SCAN` 游标分批遍历，本项目数据量小图省事
- 缓存命中率是进程内计数器，不是专业指标（专业做法是 Micrometer + Actuator）
- 暂未实现登录与权限控制，规划中是 JWT + RBAC 的用户 / 管理员双角色
- 暂未容器化部署，规划中是 Docker + Nginx 上线

## 说明

本项目为个人学习练手项目，代码以"能讲清楚每一处为什么这么写"为标准，注释写得比较啰嗦，欢迎交流指正。

- 后端（本仓库）：https://github.com/Vega0717/campus-market
- 前端：https://github.com/Vega0717/campus-market-web
- GitHub：[@Vega0717](https://github.com/Vega0717)
