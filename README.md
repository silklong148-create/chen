<<<<<<< HEAD
# Mysqladmin

Mysqladmin 是一个基于 **Spring Boot 3 + Vue 3** 的 Web 数据库可视化管理工具。它先通过应用账号登录，再允许用户动态连接 MySQL 或 SQL Server，进行数据库与数据表的基础管理，并以明确的确认提示保护删除等高风险操作。

> 本版本已覆盖任务二的库、表与记录 CRUD，并提供 MySQL / SQL Server 的基础兼容能力；导入导出与非 `dbo` SQL Server 架构仍是后续扩展方向。

## 功能清单

- 应用账号：注册、登录、退出登录；账号密码以 BCrypt 哈希保存在本地 H2 文件数据库。
- 登录保护：未登录时，所有 MySQL 连接与库表 API 均返回未授权；每个登录会话维护独立 MySQL 连接配置。
- 动态多数据库连接：登录后选择 MySQL 或 SQL Server，填写主机、端口、账号和密码，后端通过对应 JDBC 驱动验证并在服务端内存保持当前会话配置。
- 数据库服务端管理：工作台左侧提供“数据库服务端”入口，可切换 MySQL / SQL Server 及其连接凭据；验证成功后会清空当前库表选择并切换到新的实例。
- 数据库管理：查询数据库、创建数据库、修改字符集/排序规则、删除数据库。
- 数据表管理：查看表列表和基础信息；创建表及字段；查看表结构、完整数据和分页结果；重命名、修改注释、删除表。
- 数据记录 CRUD：可新增、编辑、删除表记录；修改和删除以主键定位，未设置主键的表会明确提示并禁用这两项高风险操作。外键约束、非空约束、唯一约束等导致的失败会显示可理解的原因，不会静默失败。
- 数据浏览体验：支持关键词搜索、按单字段或全部字段筛选、字段排序、列显隐、25/50/100/250/500 条分页；大页数据采用虚拟滚动，仅渲染当前可视区域的记录行。
- 字段管理 API：支持新增与删除字段，便于后续扩展到可视化字段编辑。
- 视图管理：查询视图、创建视图、查看定义和结果预览、更新与删除视图；视图 SQL 仅接受单条 `SELECT` / `WITH` 查询。
- 索引管理：查看表索引、创建普通或唯一索引、修改和删除非主键索引；主键索引继续通过字段结构维护。
- 安全控制：对象名称严格白名单校验；元数据查询使用预编译参数；删除库、删除表等操作由前端二次确认。
- 操作反馈：连接、创建、修改、删除与异常均给出明确成功或失败提示。
- API 文档：集成 Swagger UI，可在本地查看所有接口。

## 项目结构

```text
Mysqladmin/
├─ backend/                 # Spring Boot 3 REST API
│  └─ src/main/java/com/mysqladmin/
├─ frontend/                # Vue 3 + Vite 管理工作台
├─ PRODUCT.md                # 产品与交互设计上下文
├─ DESIGN.md                 # 前端视觉设计令牌与规范
└─ README.md
```

## 环境依赖

| 工具 | 建议版本 | 本项目用途 |
| --- | --- | --- |
| JDK | 17+ | 运行 Spring Boot 后端 |
| Maven | 3.9+ | 后端依赖和构建 |
| Node.js | 20+ | 运行 Vue 前端 |
| npm | 10+ | 前端依赖管理 |
| MySQL | 5.7+（建议 5.7.8+、8.0 或 8.4 LTS） | 可管理的数据库实例 |
| SQL Server | 2019+（建议） | 可管理的数据库实例 |

本项目不需要预先创建业务库：连接成功后可直接在界面创建。

## MySQL 版本兼容性

- 使用 `mysql-connector-j 8.0.33`，兼容 MySQL 5.7 与 8.x；不再跟随 Spring Boot 默认的 Connector/J 9.x 驱动。
- MySQL 5.7.8+：支持数据库、数据表、索引、视图、记录 CRUD 与原生 `JSON` 字段。创建/编辑视图时，单个非递归的 `WITH cte AS (SELECT ...) SELECT ... FROM cte` 会自动改写为子查询后执行；多 CTE、递归 CTE、CTE 列别名和窗口函数仍需改用子查询或升级到 8.0+。
- MySQL 5.7.0–5.7.7：不支持原生 `JSON` 字段；系统会提示改用 `LONGTEXT`，或升级到 5.7.8+。
- MySQL 8.0 / 8.4 LTS：支持上述全部功能及视图中的 `WITH` 查询。

## 启动步骤

### 1. 启动后端

在项目根目录打开终端：

```powershell
cd backend
mvn spring-boot:run
```

当日志显示 `Started MysqladminApplication` 时，后端已启动。

### 2. 启动前端

新开一个终端：

```powershell
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，先注册或登录 Mysqladmin 账号。登录后直接进入工作台；通过左侧“数据库服务端”入口填写 MySQL 连接信息并完成切换。

### 3. 构建生产包（可选）

```powershell
cd backend
mvn clean package

cd ..\frontend
npm run build
```

后端产物位于 `backend/target/`，前端静态文件位于 `frontend/dist/`。

## 端口说明

| 服务 | 地址 | 说明 |
| --- | --- | --- |
| Vue 开发服务器 | `http://localhost:5173` | 浏览器管理界面 |
| Spring Boot API | `http://localhost:8080` | REST API 服务 |
| Swagger UI | `http://localhost:8080/swagger-ui.html` | 本地 API 文档 |
| MySQL（默认） | `127.0.0.1:3306` | 由页面动态连接，可自行修改 |
| SQL Server（默认） | `127.0.0.1:1433` | 在“数据库服务端”选择 SQL Server 后连接 |

Vite 已配置 `/api` 代理到 `http://localhost:8080`，本地开发不需要额外处理跨域；后端也仅放行 `http://localhost:5173` 的 API 跨域请求。

## 连接与权限建议

界面默认填写 `127.0.0.1:3306` 和 `root`，以方便本地练习。对于日常开发，建议创建专用账号并仅授予所需权限，例如：

```sql
CREATE USER 'mysqladmin_dev'@'localhost' IDENTIFIED BY '请替换为强密码';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX
ON *.* TO 'mysqladmin_dev'@'localhost';
FLUSH PRIVILEGES;
```

不要把真实密码写入源代码、`README.md` 或 Git 提交。服务端不会把密码返回给前端；连接设置在后端内存中保存，重启后需重新连接。

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/connections/test` | 测试并保存动态 MySQL 连接 |
| `POST` | `/api/auth/register` | 注册 Mysqladmin 应用账号 |
| `POST` | `/api/auth/login` | 登录 Mysqladmin 应用账号 |
| `POST` | `/api/auth/logout` | 退出登录并失效会话 |
| `GET` | `/api/auth/session` | 获取当前登录状态 |
| `GET` | `/api/connections/status` | 获取连接状态 |
| `GET` | `/api/databases` | 查询数据库列表 |
| `POST` | `/api/databases` | 创建数据库 |
| `PUT` / `DELETE` | `/api/databases/{database}` | 更新配置 / 删除数据库 |
| `GET` / `POST` | `/api/databases/{database}/tables` | 查询 / 创建数据表 |
| `PUT` / `DELETE` | `/api/databases/{database}/tables/{table}` | 修改 / 删除表 |
| `GET` | `/api/databases/{database}/tables/{table}/structure` | 查看字段结构 |
| `GET` | `/api/databases/{database}/tables/{table}/rows` | 分页查看完整表数据（`limit`、`offset`、`keyword`、`filterColumn`、`sortBy`、`sortDirection`） |
| `POST` | `/api/databases/{database}/tables/{table}/rows` | 新增一条记录 |
| `PUT` | `/api/databases/{database}/tables/{table}/rows` | 按主键更新一条记录 |
| `DELETE` | `/api/databases/{database}/tables/{table}/rows` | 按主键删除一条记录 |
| `POST` / `PUT` / `DELETE` | `/api/databases/{database}/tables/{table}/columns/...` | 新增 / 修改 / 删除字段 |
| `GET` / `POST` | `/api/databases/{database}/views` | 查询 / 创建视图 |
| `GET` / `PUT` / `DELETE` | `/api/databases/{database}/views/{view}` | 查看定义 / 修改 / 删除视图 |
| `GET` | `/api/databases/{database}/views/{view}/rows` | 预览视图结果 |
| `GET` / `POST` | `/api/databases/{database}/tables/{table}/indexes` | 查询 / 创建索引 |
| `PUT` / `DELETE` | `/api/databases/{database}/tables/{table}/indexes/{index}` | 修改 / 删除非主键索引 |

完整请求参数、响应示例请在后端启动后访问 Swagger UI。

## 已知边界

- SQL Server 当前固定使用默认 `dbo` 架构；非 `dbo` schema 作为后续扩展。
- Mysqladmin 应用账号保存在后端运行目录的 `data/mysqladmin-auth.mv.db`；这是应用认证数据，不是被管理的 MySQL 实例数据。
- 动态对象名仅允许字母、数字、下划线和 `$`，以确保动态 SQL 安全。
- 为避免误操作，缺少主键的表只能查看和新增记录，不能在界面中更新或删除既有记录；更新主键本身也会被拒绝。
- 当前已实现 MySQL 和 SQL Server 的数据库、表、视图、索引基础管理；导入导出仍属于后续可选扩展。
=======
# chen
一个编码爱好者的仓库
>>>>>>> 98fee9275312b7ab36013130e34af8f7ebd782cc
