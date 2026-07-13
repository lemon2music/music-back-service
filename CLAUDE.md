# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建、运行、测试

基于 Maven wrapper 的项目（Windows 环境；git-bash shell 下可直接运行 `./mvnw`）：

- 构建：`./mvnw clean package`（加 `-DskipTests` 可跳过测试阶段）
- 启动应用：`./mvnw spring-boot:run`（默认端口 8080——未配置 `server.port`）
- 运行全部测试：`./mvnw test`
- 运行单个测试类：`./mvnw test -Dtest=MusicBackServiceApplicationTests`
- 运行单个测试方法：`./mvnw test -Dtest=MusicBackServiceApplicationTests#contextLoads`

需要 Java 21。项目未集成 checkstyle/spotless/format 之类的插件，因此不强制代码格式。

## 技术栈

Spring Boot **4.1.0**（web starter 为 `spring-boot-starter-webmvc`，而非 `…-web`），MyBatis 3.0.4 采用**注解式** mapper（无 XML mapper 文件），spring-data-redis，spring-security-**crypto**（仅用 BCrypt——见下方鉴权说明），MySQL（运行时）+ H2（测试），Lombok，Jakarta Validation。所有代码位于包 `com.lemon.music.musicbackservice` 下。

## 架构

### 鉴权是自定义实现——并非 Spring Security

引入 `spring-security-crypto` 依赖**仅仅**是为了用 `BCryptPasswordEncoder`。项目没有 Spring Security 的 filter chain、没有 `SecurityContext`、没有 `@PreAuthorize`。鉴权完全通过一个普通的 MVC 拦截器实现：

- `auth/AuthInterceptor` 在 `config/WebConfig` 中注册到 `/**`。每个请求进来时，它会取出 `Authorization: Bearer <token>` 头，通过 `AuthService` 在 Redis 中查 session，再用 `@RequirePermission` 注解校验 session 的权限集合，最后把 session 放进 `auth/AuthContext`（一个 `ThreadLocal`，在 `afterCompletion` 中清除）。
- **公开端点**（无需 token）通过路径前缀硬编码在 `AuthInterceptor.preHandle` 中：`/api/auth/login` 与 `/api/users/register`。任何其他需要公开的路由都必须在那里添加。
- `auth/RequirePermission` 是一个注解，可用于**方法**或**类**。拦截器会拿 session 的 `Set<String> permissions` 做精确字符串匹配。
- 在 controller/service 中获取当前用户，调用 `AuthContext.get()` → `UserSession`。

### Token 模型：Redis 中的不透明 UUID，不是 JWT

`AuthService.login` 生成一个随机 UUID token，并把 JSON 序列化后的 `UserSession` 存入 Redis，key 为 `auth:token:{token}`（TTL = `app.auth.token-expire-hours`，默认 168h = 7 天）。发给某用户的所有 token 都记录在 Redis 集合 `auth:user:tokens:{userId}` 中（供 `logoutAllByUserId` 使用）。累计在线时长记录在 `auth:online:total:{userId}` 下，注销时按本 session 的经过秒数累加。

### RBAC 数据模型

数据表：`app_user`、`app_role`、`app_permission`、`user_role`、`role_permission`（见 `src/main/resources/schema.sql`）。登录时由 `PermissionMapper.findPermissionCodesByUserId` 把权限展平进 session（联表路径：`user_role` → `role_permission` → `app_permission`）。种子角色/权限定义在 `src/main/resources/data.sql` 中，使用 `NOT EXISTS` 守卫以保证幂等。当前权限码：`USER_SELF`、`USER_MANAGE_MEMBERSHIP`、`USER_ASSIGN_ROLE`；角色 `USER`、`ADMIN`。

### 包结构 / 模块约定

`user` 包是参考模块。新增领域时照此结构组织：

```
user/
  domain/   实体（Lombok @Data，可变）+ 枚举
  dto/      请求/响应 record，带 Jakarta validation 注解
  mapper/   MyBatis @Mapper 接口（SQL 以 @Select/@Insert/@Update 字符串形式内联）
  service/  @Service + @Transactional；违反业务规则时抛 BusinessException
  controller/ @RestController，统一挂在 /api/... 下
```

- **DTO、值对象、枚举、配置 record 一律用 `record`。** DB 实体（`@Data` 类）是例外。所有依赖均通过 Lombok `@RequiredArgsConstructor` 构造器注入。
- 每个 controller 方法都返回 `common/ApiResponse<T>`——即 `{success, message, data}` 信封。使用 `ApiResponse.ok(...)` / `ApiResponse.fail(...)` 工厂方法。
- **坑——mapper 扫描固定在 user 模块。** `@MapperScan("...user.mapper")` 在 `MusicBackServiceApplication` 与 `config/MyBatisConfig` **两处**都声明了（冗余）。其他包下的 mapper 默认不会被扫描，除非在两处都加上对应包。（单独标注了 `@Mapper` 的接口也会被扫描，但显式的 `@MapperScan` 才是可靠路径。）
- MyBatis 开启了 `map-underscore-to-camel-case: true`，所以 DB 的 `snake_case` 列会自动映射到 camelCase 字段。

### 错误处理

`common/GlobalExceptionHandler`（`@RestControllerAdvice`）的映射规则：`BusinessException` → HTTP 400；校验失败（`MethodArgumentNotValidException`/`BindException`）→ 400 并返回第一个字段错误的 message；其他任何 `Exception` → 500。在 service 中遇到预期的业务规则失败就抛 `common/BusinessException(message)`；鉴权失败不要靠抛异常——401 的 JSON 是由拦截器自己写的。

### 持久化初始化每次启动都执行

`spring.sql.init.mode: always` 使得 `schema.sql`（`CREATE TABLE IF NOT EXISTS`）和 `data.sql` 在**每次**启动时都执行，dev 和 test 皆是如此。请保持它们幂等。

## 配置 profile

- **主配置（`application.yaml`）** 指向远程 dev 环境的 MySQL 与 Redis（`47.119.121.254`），且凭据已提交进仓库（`admin`/`Test@123456`）。请注意这些是已入库的真实 dev 环境密钥。
- **测试配置（`src/test/resources/application.yaml`）** 把 MySQL 换成内存 H2 的 MySQL 模式（`MODE=MySQL`），排除了 Redis 自动配置，并且上下文测试里把 `StringRedisTemplate` 用 `@MockitoBean` 模拟掉了。因此 mapper 中的 SQL 必须保持 H2 兼容（不要用 MySQL 专有语法），否则测试会失败。
