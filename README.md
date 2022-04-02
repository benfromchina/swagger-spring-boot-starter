# 目录

- [功能](#功能)
- [效果展示](#效果展示)
- [配置](#配置)
- [项目实例](#项目实例)

# 功能

- 支持 Swagger 自动配置
- 支持 OAuth2.0 登录
- 支持 Zuul 网关汇集微服务接口
- 支持 Gateway 网关汇集微服务接口

# 效果展示

- Spring Boot 单体应用

  <img src="docs/lib/standalone.png" width="1440" height="818">

- Zuul 或 Gateway 网关收集微服务接口
  
  <img src="docs/lib/gateway.png" width="1439" height="815">
  
- OAuth2.0 登录
  
  <img src="docs/lib/oauth2.png" width="1438" height="817">


# 配置

1. . pom.xml 中引入依赖

```xml
<dependency>
    <groupId>com.github.benfromchina</groupId>
    <artifactId>druid-admin-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<!-- druid 监控底层基于 servlet ，需要 web 模块支持 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <version>${spring-cloud.version}</version>
</dependency>
```

- eureka 注册中心引入

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    <version>${spring-cloud.version}</version>
</dependency>
```

- nacos 注册中心引入

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    <version>${spring-cloud-alibaba.version}</version>
</dependency>
```

2. yaml 中配置

- eureka 注册中心

```yml
swagger:
  base-package: com.stark.demo.controller                   # 扫描包路径
  title: demo api                                           # 标题
  description: restful api                                  # 描述
  version: 1.0                                              # 版本号
  terms-of-service-url: urn:tos                             # 接口使用条件说明
  contact-name: tony                                        # 接口维护人姓名
  contact-email: tony@stark.com                             # 接口维护人电子邮件
  license: Apache 2.0                                       # 证书名称
  license-url: ttp://www.apache.org/licenses/LICENSE-2.0    # 证书链接地址
  vendor-extensions:                                        # 额外功能扩展
  index-redirect: true                                      # 是否将首页 {"/", "/index"} 重定向到 swagger 接口文档页
  oauth2:                                                   # oauth2登录配置
    enabled: false                                          # 是否开启
    type: password                                          # 支持 authorization_code(授权码)、password(密码)
    access-token-url: http://xxx/auth/oauth/token           # 获取 token 链接地址
    authorize-url: http://xxx/auth/oauth/authorize          # 认证授权链接地址
    client-id: jarvis                                       # 客户端ID
    client-secret: 7fc1a04f90df4e8ba7b310ab6fbb17b4         # 客户端秘钥
    scopes:                                                 # 授权作用域列表，scope 和 description 自定义
      - scope: all
        description: 所有权限
  zuul:                                                     # zuul网关收集微服务接口统一展示
    enabled: false                                          # 是否开启
    service-id-regex: .*-service-.*                         # 微服务ID正则，匹配的微服务收集swagger
  gateway:                                                  # gateway网关收集微服务接口统一展示
    enabled: false                                          # 是否开启
    service-id-regex: .*-service-.*                         # 微服务ID正则，匹配的微服务收集swagger
```

- nacos 注册中心

```yml
spring:
  cloud:
    nacos:
      server-addr: 192.168.22.100:8848  # 单机 nacos 地址，或 nacos 集群虚拟 IP
  datasource:
    druid:
      admin:
        login-username: user
        login-password: 123456
        applications:                # 需要监控的微服务名，默认为 spring.application.name
        - escloud-service-elk
        - escloud-service-manager
        - escloud-service-ocr
        - escloud-service-user
```

3. 客户端微服务配置

```yml
spring:
  datasource:
    druid:
      filter:
        stat:
          enabled: true
      web-stat-filter:
        enabled: true
        url-pattern: /*
        exclusions: '*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*'
      stat-view-servlet:
        enabled: true
        allow: ''                # ''表示允许所有地址访问，默认只能本服务访问
        url-pattern: /druid/*
```

4. 访问 uri `/druid/service.html`

# 项目实例

[druid-admin-samples](https://gitee.com/jarvis-lib/druid-admin-samples)
