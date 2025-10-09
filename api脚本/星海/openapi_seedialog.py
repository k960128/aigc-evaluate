import hmac
import hashlib
import base64
from datetime import datetime
import requests
import json
import uuid
import time
import threading
from sseclient import SSEClient

#api接口--小智对话框

HMAC_ALGORITHM = "hmac-sha256"
BASE_URL = "https://openxh.teleagi.cn"
SEE_DIALOG = "/ais/bot/openapi/dcc/sseDialog"
AGENT_LIST = "/ais/bot/openapi/queryAgentList"
AK = "e173f6253f72434e81f0290ca52b8d07"
SK = "65a05936f38d40be82ee698a5c7a1ba6"


# 自定义请求头
CUSTOM_HEADERS = {
    "x-userid": "8623884808018395136",
    "x-tenantid": "beijingAItelecom",
    "x-source": "KS"  # 操作来源系统，KS: 智文; BOT: 机器人; OUTBOUND: 外呼
}

def generate_auth(ak, sk, method, uri, headers, params=None, body=None):
    # 获取 Date 头部
    formatted_utc_time = headers.get("Date")
    if not formatted_utc_time:
        raise ValueError("Missing 'Date' header in request")

    string_to_sign = (
        f"{ak}\n"
        f"{method.upper()} {uri}\n"
        f"date: {formatted_utc_time}\n"
    )

    # HMAC 签名
    hasher = hmac.new(sk.encode(), string_to_sign.encode(), hashlib.sha256)
    signature_base64 = base64.b64encode(hasher.digest()).decode()

    # header 字段只包含 @request-target 和 date，固定
    header_str = "@request-target date"

    # 构造最终的 Authorization 头部
    return (
        f'Signature keyId="{ak}",'
        f'algorithm="{HMAC_ALGORITHM}",'
        f'headers="{header_str}",'
        f'signature="{signature_base64}"'
    )


def get_formatted_date():
    return datetime.utcnow().strftime("%a, %d %b %Y %H:%M:%S GMT")


def make_request(url, method, path, ak, sk, body=None, params=None, custom_headers=None):
    full_url = f"{url}{path}"

    # 构建基础头部
    headers = {
        "Host": url.split("//")[-1].split("/")[0],
        "Content-Type": "application/json",
        "Date": get_formatted_date(),  # 例如：Tue, 01 Jul 2025 08:00:00 GMT
        "x-userid": "",  # 补充实际值
        "x-tenantid": "",
        "x-source": "KS"
    }

    # 添加额外头部
    if custom_headers:
        headers.update(custom_headers)

    # 确保 body 是字符串（和 Java 中的 reqBody 一致）
    body_str = body if isinstance(body, str) else json.dumps(body) if body else ""

    # 生成签名
    auth_header = generate_auth(ak, sk, method, path, headers, params or {}, body_str)
    headers["Authorization"] = auth_header

    # 发起请求
    method = method.upper()
    if method == "GET":
        response = requests.get(full_url, headers=headers)
    elif method == "POST":
        response = requests.post(full_url, headers=headers, data=body_str)
    elif method == "PUT":
        response = requests.put(full_url,  headers=headers, data=body_str)
    elif method == "DELETE":
        response = requests.delete(full_url, headers=headers)
    else:
        raise ValueError(f"不支持的HTTP方法: {method}")

    return response


def query_agent_code():
    # 构建请求参数
    query_params = {
        "agentName": "",
        "pageNo": 1,
        "pageSize": 100,
        "botType": None
    }

    response = make_request(BASE_URL, "POST", AGENT_LIST, AK, SK, body=json.dumps(query_params), custom_headers=CUSTOM_HEADERS)

    if response.status_code != 200:
        raise Exception(f"接口请求失败，状态码: {response.status_code}")

    response_json = response.json()
    #print("Agent列表查询结果:", json.dumps(response_json, ensure_ascii=False, indent=2))

    # 解析响应
    if response_json.get("code") != "00000":
        raise RuntimeError("查询Agent列表失败: " + response_json.get("message", "未知错误"))

    data = response_json.get("data", {})
    records = data.get("records", [])

    if not records:
        raise RuntimeError("未找到任何Agent")

    selected_agent_code = None
    first_agent_code = None

    for agent in records:
        agent_code = agent.get("agentCode")
        agent_name = agent.get("agentName")

        if first_agent_code is None:
            first_agent_code = agent_code

        if agent_name and "小智" in agent_name:
            selected_agent_code = agent_code
            break

    if selected_agent_code is None:
        selected_agent_code = first_agent_code
        raise RuntimeError(f"未找到名称包含'小智'的Agent，使用第一个Agent: {selected_agent_code}")

    return selected_agent_code


def get_model_answer(question: str):
    try:
        agent_code = query_agent_code()
        message_id = str(uuid.uuid4())
        session_id = str(uuid.uuid4())
        user_id = str(uuid.uuid4())

        message_request = {
            "messageId": message_id,
            "sessionId": session_id,
            "msgType": "TEXT",
            "userId": user_id,
            "content": question,
            "query": question,
            "agentCode": agent_code,
            "test": 1,
            "chatType": "chat",
            "requestTime": int(time.time() * 1000)
        }

        post_response = make_request(
            BASE_URL,
            "POST",
            SEE_DIALOG,
            AK,
            SK,
            body=json.dumps(message_request),
            custom_headers=CUSTOM_HEADERS,
        )


        response.encoding = 'utf-8'
        if post_response.status_code != 200:
            raise RuntimeError(f"请求失败，HTTP 状态码: {response.status_code}")

        # 解析 SSE 流
        client = SSEClient(post_response)
        full_answer = ""


        for event in client.events():
            if not event.data.strip():
                continue

            try:
                data = json.loads(event.data)
                answer = data.get("answer", {})
                content = answer.get("content")
                if content:
                    full_answer += content
            except json.JSONDecodeError:
                print("解析失败:", event.data)

            # 可选：根据 eventType 判断结束
            if data.get("eventType") == "finish":
                break

    except Exception as e:
        raise e

    return full_answer
