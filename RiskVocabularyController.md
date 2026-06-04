# 【RiskVocabularyController-风险词库极速拦截字典】
## 接口清单
### 1. 分页查询特征词
- 请求地址：/risk/vocabularies/page
- 请求方法：GET
- 接口简介：分页查询特征词
#### 2.URL查询参数
| 参数名 | 参数类型 | 是否必填 | 参数说明      |
| ---- | ---- | ---- |-----------|
| current | Integer | 否 | 页码，默认1    |
| size | Integer | 否 | 每页大小，默认10 |
| riskDetailsId | Long | 否 | 风险子类ID    |
| keyword | String | 否 | 特征词，模糊匹配  |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": {
        "records": [
            {
                "id": 1,
                "groupId": 1,
                "riskDetailsId": 1,
                "keyword": "敏感词",
                "riskLevel": 1,
                "matchType": 1,
                "syncStatus": 1,
                "creator": "admin",
                "updater": "admin",
                "createTime": "2024-01-01 10:00:00",
                "updateTime": "2024-01-01 10:00:00",
                "deleted": false
            }
        ],
        "total": 10,
        "size": 10,
        "current": 1,
        "pages": 1
    }
}
```

### 2. 录入新的特征词
- 请求地址：/risk/vocabularies
- 请求方法：POST
- 接口简介：录入新的特征词 (保存后 syncStatus 默认为 0 待同步)
#### 3.Body请求体
| 字段名 | 字段类型 | 是否必填 | 字段说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 否 | 主键ID，新增时自动生成 |
| groupId | Long | 是 | 所属分组ID |
| riskDetailsId | Long | 是 | 所属风险词库详情ID |
| keyword | String | 是 | 字面量特征词 |
| riskLevel | Integer | 否 | 风险等级：1-致命级别，2-疑似级别 |
| matchType | Integer | 否 | 匹配模式：1-精确匹配，2-模糊包含匹配 |
| syncStatus | Integer | 否 | Redis同步状态，保存后默认为0待同步 |
| creator | String | 否 | 创建人 |
| updater | String | 否 | 更新人 |
| createTime | LocalDateTime | 否 | 创建时间，自动填充 |
| updateTime | LocalDateTime | 否 | 更新时间，自动填充 |
| deleted | Boolean | 否 | 是否删除：0-未删除，1-已删除 |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": true
}
```

### 3. 模拟推送到Redis并更新状态
- 请求地址：/risk/vocabularies/sync-to-redis
- 请求方法：POST
- 接口简介：模拟推送到 Redis 并更新状态，实际业务中这里会调用 RedisTemplate 发布消息，并构建 AC 树
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": "成功推送 5 条特征词，AC自动机将在一分钟内完成热更"
}
```

### 4. 删除特征词
- 请求地址：/risk/vocabularies/{id}
- 请求方法：DELETE
- 接口简介：删除特征词
#### 1.路径参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 是 | 特征词ID |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": true
}
```


### 5. 根据分类ID查询风险明细小类列表（不分页）
- 请求地址：/risk/category/details/list
- 请求方法：GET
- 接口简介：根据分类ID查询风险明细列表（不分页）
#### 2.URL查询参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
| categoryId | Long | 否 | 关联的风险大类ID |
| status | Integer | 否 | 状态：0-禁用，1-启用 |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": [
        {
            "id": 1,
            "categoryId": 1,
            "detailsName": "煽动颠覆/分裂国家",
            "sortOrder": 1,
            "status": 1,
            "createTime": "2024-01-01 10:00:00",
            "updateTime": "2024-01-01 10:00:00",
            "deleted": false
        }
    ]
}
```