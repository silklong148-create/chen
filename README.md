# Mysqladmin

Mysqladmin 是一个基于 **Spring Boot 3 + Vue 3** 的 Web 数据库可视化管理工具。项目提供应用账号注册登录、动态数据库连接、数据库与数据表管理、记录 CRUD、视图管理、索引管理等功能，适合本地开发、学习和内网环境下快速管理数据库。

当前项目已支持连接 MySQL、SQL Server、达梦 DM8、PostgreSQL 和 Oracle。应用账号数据使用本地 H2 文件数据库保存，被管理的业务数据库通过页面动态连接。

## 功能特性

- 应用账号：支持注册、登录、退出登录，密码使用 BCrypt 加密保存。
- 动态连接：登录后可填写主机、端口、账号、密码和数据库类型，后端通过 JDBC 验证连接。
- 多数据库支持：支持 MySQL、SQL Server、达梦 DM8、PostgreSQL、Oracle。
- 数据库管理：查询数据库列表、创建数据库、修改数据库配置、删除数据库。
- 数据表管理：查询表列表、创建表、查看表结构、重命名表、修改备注、删除表。
- 字段管理：新增字段、修改字段、删除字段。
- 数据记录 CRUD：新增、编辑、删除表记录，支持按主键定位记录。
- 数据浏览：支持关键字搜索、字段筛选、排序、分页和大数据量虚拟滚动展示。
- 视图管理：查询视图、创建视图、查看定义、预览结果、更新视图、删除视图。
- 索引管理：查看索引、创建普通或唯一索引、修改索引、删除非主键索引。
- API 文档：集成 Swagger UI，后端启动后可查看接口文档。
- 安全控制：对象名白名单校验，高风险删除操作由前端二次确认。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.4.2、Spring Web、Spring Validation、Spring Data JPA |
| 数据库连接 | MySQL Connector/J、Microsoft JDBC Driver、DM JDBC、PostgreSQL JDBC、Oracle JDBC |
| 本地认证库 | H2 Database |
| API 文档 | springdoc-openapi / Swagger UI |
| 前端 | Vue 3、Vite 6 |
| 构建工具 | Maven、npm |

## 项目结构

```text
Mysqladmin/
|-- backend/                         # Spring Boot 后端服务
|   |-- src/main/java/com/mysqladmin/
|   |   |-- auth/                    # 应用账号认证
|   |   |-- controller/              # REST 接口
|   |   |-- dto/                     # 请求参数对象
|   |   |-- service/                 # 数据库管理核心逻辑
|   |   `-- security/                # 登录拦截器
|   `-- src/main/resources/
|       `-- application.yml          # 后端配置
|-- frontend/                        # Vue 3 前端页面
|   |-- src/
|   |   |-- App.vue                  # 主界面
|   |   |-- api.js                   # 前端 API 封装
|   |   `-- *.css                    # 页面样式
|   `-- vite.config.js               # Vite 开发代理配置
|-- PRODUCT.md                       # 产品说明
|-- DESIGN.md                        # 设计说明
`-- README.md
```

## 环境要求

| 工具 | 建议版本 |
| --- | --- |
| JDK | 17 或以上 |
| Maven | 3.9 或以上 |
| Node.js | 20 或以上 |
| npm | 10 或以上 |
| MySQL | 5.7+ / 8.x，可选 |
| SQL Server | 2019+，可选 |
| 达梦 DM8 | 可选 |
| PostgreSQL | 可选 |
| Oracle | 可选 |

说明：只有在需要连接对应数据库时，才需要提前准备该数据库服务。

## 本地启动

### 1. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端默认端口为：

```text
http://localhost:8080
```

Swagger UI 地址：

```text
http://localhost:8080/swagger-ui.html
```

### 2. 启动前端

新开一个终端：

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

Vite 已配置 `/api` 代理到 `http://127.0.0.1:8080`，本地开发时前端可以直接请求后端接口。

## 使用流程

1. 打开 `http://localhost:5173`。
2. 注册或登录 Mysqladmin 应用账号。
3. 在工作台中选择数据库类型。
4. 填写数据库主机、端口、用户名、密码和数据库名。
5. 连接成功后即可管理数据库、数据表、字段、记录、视图和索引。

常用默认端口：

| 数据库 | 默认端口 |
| --- | --- |
| MySQL | 3306 |
| SQL Server | 1433 |
| 达梦 DM8 | 5236 |
| PostgreSQL | 5432 |
| Oracle | 1521 |

## 生产构建

后端打包：

```powershell
cd backend
mvn clean package
```

前端构建：

```powershell
cd frontend
npm run build
```

构建产物位置：

| 模块 | 产物 |
| --- | --- |
| 后端 | `backend/target/` |
| 前端 | `frontend/dist/` |

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册应用账号 |
| `POST` | `/api/auth/login` | 登录应用账号 |
| `GET` | `/api/auth/session` | 获取当前登录状态 |
| `POST` | `/api/auth/logout` | 退出登录 |
| `POST` | `/api/connections/test` | 测试并保存数据库连接 |
| `GET` | `/api/connections/status` | 获取连接状态 |
| `DELETE` | `/api/connections` | 断开当前连接 |
| `GET` | `/api/databases` | 查询数据库列表 |
| `POST` | `/api/databases` | 创建数据库 |
| `PUT` | `/api/databases/{database}` | 更新数据库配置 |
| `DELETE` | `/api/databases/{database}` | 删除数据库 |
| `GET` | `/api/databases/{database}/tables` | 查询数据表 |
| `POST` | `/api/databases/{database}/tables` | 创建数据表 |
| `GET` | `/api/databases/{database}/tables/{table}/structure` | 查看表结构 |
| `GET` | `/api/databases/{database}/tables/{table}/rows` | 分页查询表数据 |
| `POST` | `/api/databases/{database}/tables/{table}/rows` | 新增记录 |
| `PUT` | `/api/databases/{database}/tables/{table}/rows` | 更新记录 |
| `DELETE` | `/api/databases/{database}/tables/{table}/rows` | 删除记录 |
| `GET` | `/api/databases/{database}/views` | 查询视图 |
| `POST` | `/api/databases/{database}/views` | 创建视图 |
| `GET` | `/api/databases/{database}/tables/{table}/indexes` | 查询索引 |
| `POST` | `/api/databases/{database}/tables/{table}/indexes` | 创建索引 |

完整请求参数和响应结构请查看 Swagger UI。

## 配置说明

后端配置文件位于：

```text
backend/src/main/resources/application.yml
```

当前默认配置：

| 配置 | 值 |
| --- | --- |
| 后端端口 | `8080` |
| 应用认证库 | `jdbc:h2:file:./data/mysqladmin-auth` |
| Swagger UI | `/swagger-ui.html` |

应用账号数据会保存在后端运行目录下的：

```text
backend/data/mysqladmin-auth.mv.db
```

这个文件只保存 Mysqladmin 的登录账号信息，不是被管理的业务数据库。

## GitHub 更新代码

如果代码已经发布到 GitHub，后续更新可以使用：

```powershell
git status
git add .
git commit -m "更新项目代码"
git push
```

如果第一次推送当前分支时提示没有 upstream，可以使用：

```powershell
git push -u origin main
```

如果你的分支名是 `master`，则改成：

```powershell
git push -u origin master
```

## 注意事项

- 不要把真实数据库密码写入 README、源码或 Git 提交记录。
- 建议为开发环境单独创建数据库账号，并只授予必要权限。
- 删除数据库、删除数据表、删除记录等操作具有风险，执行前请确认目标对象。
- 没有主键的数据表不适合直接做记录更新和删除，建议为业务表设置主键。
- 本项目更适合本地开发、学习和内网管理场景，生产公网部署前需要补充更严格的权限、审计和安全配置。
