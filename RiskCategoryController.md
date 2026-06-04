# 【RiskCategoryController-风险分类管理】
## 接口清单
### 1. 分页查询风险分类
- 请求地址：/risk/category/page
- 请求方法：GET
- 接口简介：分页查询风险分类
#### 2.URL查询参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
| current | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页大小，默认10 |
| categoryName | String | 否 | 大类名称，模糊匹配 |
| status | Integer | 否 | 状态：0-禁用，1-启用 |
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
                "categoryName": "核心价值观与政治敏感",
                "description": "涉及核心价值观和政治敏感内容",
                "sortOrder": 1,
                "status": 1,
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

### 2. 查询所有风险分类（不分页）
- 请求地址：/risk/category/list
- 请求方法：GET
- 接口简介：查询所有风险分类（不分页）
#### 2.URL查询参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
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
            "categoryName": "核心价值观与政治敏感",
            "description": "涉及核心价值观和政治敏感内容",
            "sortOrder": 1,
            "status": 1,
            "createTime": "2024-01-01 10:00:00",
            "updateTime": "2024-01-01 10:00:00",
            "deleted": false
        }
    ]
}
```

### 3. 根据ID查询风险分类详情
- 请求地址：/risk/category/{id}
- 请求方法：GET
- 接口简介：根据ID查询风险分类详情
#### 1.路径参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 是 | 风险分类ID |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "categoryName": "核心价值观与政治敏感",
        "description": "涉及核心价值观和政治敏感内容",
        "sortOrder": 1,
        "status": 1,
        "createTime": "2024-01-01 10:00:00",
        "updateTime": "2024-01-01 10:00:00",
        "deleted": false
    }
}
```

### 4. 新增风险分类
- 请求地址：/risk/category
- 请求方法：POST
- 接口简介：新增风险分类
#### 3.Body请求体
| 字段名 | 字段类型 | 是否必填 | 字段说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 否 | 主键ID |
| categoryName | String | 是 | 大类名称 |
| description | String | 否 | 大类描述/防范目标 |
| sortOrder | Integer | 否 | 排序权重 |
| status | Integer | 否 | 状态：0-禁用，1-启用 |
| createTime | LocalDateTime | 否 | 创建时间 |
| updateTime | LocalDateTime | 否 | 更新时间 |
| deleted | Boolean | 否 | 逻辑删除：0-未删除，1-已删除 |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": true
}
```

### 5. 更新风险分类
- 请求地址：/risk/category
- 请求方法：PUT
- 接口简介：更新风险分类
#### 3.Body请求体
| 字段名 | 字段类型 | 是否必填 | 字段说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 是 | 主键ID |
| categoryName | String | 否 | 大类名称 |
| description | String | 否 | 大类描述/防范目标 |
| sortOrder | Integer | 否 | 排序权重 |
| status | Integer | 否 | 状态：0-禁用，1-启用 |
| createTime | LocalDateTime | 否 | 创建时间 |
| updateTime | LocalDateTime | 否 | 更新时间 |
| deleted | Boolean | 否 | 逻辑删除：0-未删除，1-已删除 |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": true
}
```

### 6. 删除风险分类
- 请求地址：/risk/category/{id}
- 请求方法：DELETE
- 接口简介：删除风险分类
#### 1.路径参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 是 | 风险分类ID |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": true
}
```

### 7. 分页查询风险明细
- 请求地址：/risk/category/details/page
- 请求方法：GET
- 接口简介：分页查询风险明细
#### 2.URL查询参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
| current | Integer | 否 | 页码，默认1 |
| size | Integer | 否 | 每页大小，默认10 |
| categoryId | Long | 否 | 关联的风险大类ID |
| detailsName | String | 否 | 具体风险项名称，模糊匹配 |
| status | Integer | 否 | 状态：0-禁用，1-启用 |
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
                "categoryId": 1,
                "detailsName": "煽动颠覆/分裂国家",
                "sortOrder": 1,
                "status": 1,
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

### 8. 根据分类ID查询风险明细列表（不分页）
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

### 9. 根据ID查询风险明细详情
- 请求地址：/risk/category/details/{id}
- 请求方法：GET
- 接口简介：根据ID查询风险明细详情
#### 1.路径参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 是 | 风险明细ID |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": {
        "id": 1,
        "categoryId": 1,
        "detailsName": "煽动颠覆/分裂国家",
        "sortOrder": 1,
        "status": 1,
        "createTime": "2024-01-01 10:00:00",
        "updateTime": "2024-01-01 10:00:00",
        "deleted": false
    }
}
```

### 10. 新增风险明细
- 请求地址：/risk/category/details
- 请求方法：POST
- 接口简介：新增风险明细
#### 3.Body请求体
| 字段名 | 字段类型 | 是否必填 | 字段说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 否 | 主键ID |
| categoryId | Long | 是 | 关联的风险大类ID |
| detailsName | String | 是 | 具体风险项名称 |
| sortOrder | Integer | 否 | 类目内排序权重 |
| status | Integer | 否 | 状态：0-禁用，1-启用 |
| createTime | LocalDateTime | 否 | 创建时间 |
| updateTime | LocalDateTime | 否 | 更新时间 |
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

### 11. 更新风险明细
- 请求地址：/risk/category/details
- 请求方法：PUT
- 接口简介：更新风险明细
#### 3.Body请求体
| 字段名 | 字段类型 | 是否必填 | 字段说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 是 | 主键ID |
| categoryId | Long | 否 | 关联的风险大类ID |
| detailsName | String | 否 | 具体风险项名称 |
| sortOrder | Integer | 否 | 类目内排序权重 |
| status | Integer | 否 | 状态：0-禁用，1-启用 |
| createTime | LocalDateTime | 否 | 创建时间 |
| updateTime | LocalDateTime | 否 | 更新时间 |
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

### 12. 删除风险明细
- 请求地址：/risk/category/details/{id}
- 请求方法：DELETE
- 接口简介：删除风险明细
#### 1.路径参数
| 参数名 | 参数类型 | 是否必填 | 参数说明 |
| ---- | ---- | ---- | ---- |
| id | Long | 是 | 风险明细ID |
#### 4.返回示例
```json
{
    "success": true,
    "code": 200,
    "message": "success",
    "data": true
}
```