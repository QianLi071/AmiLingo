# 获取用户 p:页数（>=0）； n:数量(>=1; <=20)
GET /api/v1/users?
p=0&
n=1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIyOTA5MDA2MjUzMzAyNzQzMDQiLCJzdWIiOiJsb3RpeXUiLCJpYXQiOjE3ODkyMzkyMTksImV4cCI6MTc4OTMyNTYxOX0.DhqFZF_V6ljW87F13mGNRh0029fB0mcI81rjzt_XFjw

### 示例返回 （200）：
```json
[
{
"id": 290900625330274304,
"email": "dsdwfwe",
"name": "lotiyu",
"department": null,
"createdAt": "2026-09-12T01:35:13.834299"
},
{...}
]
```
### 示例返回 （401）：
```json
{
"success": false,
"message": "User not authenticated",
"data": null
}
```
# 登录
POST /api/v1/auth/portal/login
Content-Type: application/json

### 示例请求
```json
{
"loginType": "EMAIL_PWD",
"credential": {
"email": "dsdwfwe",
"password": "123abc"
}
}
```
### 示例返回 （200）：
```json
{
"access_token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIyOTA5MDA2MjUzMzAyNzQzMDQiLCJzdWIiOiJsb3RpeXUiLCJpYXQiOjE3ODkyMzkyMTksImV4cCI6MTc4OTMyNTYxOX0.DhqFZF_V6ljW87F13mGNRh0029fB0mcI81rjzt_XFjw",
"success": true,
"data": {
"id": 290900625330274304,
"email": "dsdwfwe",
"name": "lotiyu",
"department": null,
"createdAt": "2026-09-12T01:35:13.834299"
}
}
```

### 示例返回 （429）：
```json
{
"success": false,
"message": "请求过于频繁，请稍后再试",
"data": null
}
```

# 注册
POST /api/v1/auth/portal/register
Content-Type: application/json

### 示例请求
```json
{
"email": "dsdwfwe",
"name": "lotiyu",
"password": "123abc"
}
```
### 示例返回 （200）：

```json
{
"data": {
"id": 291086990902820864,
"username": "lotiyu",
"email": "dsdwfwe",
"createdAt": "2026-09-12T13:55:46.766491"
},
"success": true,
"token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIyOTEwODY5OTA5MDI4MjA4NjQiLCJzdWIiOiJsb3RpeXUiLCJpYXQiOjE3ODkxOTI1NDYsImV4cCI6MTc4OTI3ODk0Nn0.P27oS1qlieqBXWi35L869DXpLXgjWHEavJ2nluzXI64"
}
```

### 示例返回 （429）：
```json
{
"success": false,
"message": "请求过于频繁，请稍后再试",
"data": null
}
```
# 宠物状态
GET /api/v1/pets/status
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIyOTA5MDA2MjUzMzAyNzQzMDQiLCJzdWIiOiJsb3RpeXUiLCJpYXQiOjE3ODkyMzg4NzgsImV4cCI6MTc4OTMyNTI3OH0.Qj8LoDHFBS-F9o9wzehCFSYB-cbHqXu24GlFJCiFMG8

###示例返回 （200）：
```json
{
"code": 200,
"data": {
"name": "Angel",
"level": 0,
"exp": 0,
"expToNext": 50,
"evolution": "BABY",
"mood": "SAD"
}
}
```

### 示例返回 （401）：
```json
{
"success": false,
"message": "User not authenticated",
"data": null
}
```


# 验证请求
POST /api/v1/auth/portal/validate
Content-Type: application/json

### 示例请求
```json
{
"email": "example@163.com",
"code": "838101"
}
```

### 示例返回 （200）：
```json
{
"success": true,
"message": "OK",
"data": "验证成功"
}
```
### 示例返回 （403）：
```json
{
"success": false,
"message": "error",
"data": "验证码错误"
}
```


# 发送验证码请求
POST /api/v1/users/send
Content-Type: application/json

```json
{
"email": "example@163.com"
}
```

### 示例返回 （200）：
```
{
"success": true,
"message": "OK",
"data": "验证码已发送，请查收邮箱"
}
```
### 示例返回 （429）：
```json
{
"success": false,
"message": "请求过于频繁，请稍后再试",
"data": null
}
```
