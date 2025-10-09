import requests
import base64
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes
import json
import os
from typing import Optional

#api接口--单文件生成

TOKEN_CACHE_FILE = "rjgc/token_cache.json"
BASE_HOST = "https://starfactoryapi.teleagi.cn"
ACCESS_API_PATH = "/user/getAccessKey"
LOGIN_API_PATH = "/user/login"
STREAM_GENRATECODE = "/codegen/stream/generateCode"

USER = "17710139040" 
TENANT_NAME = "软件工厂团队"
PASSWORD = "Aa135246!!"

def format_public_key(raw_key: str) -> str:
    raw_key = raw_key.strip()
    
    # 如果已经是 PEM 格式，就不处理
    if raw_key.startswith("-----BEGIN PUBLIC KEY-----"):
        return raw_key

    # 否则尝试格式化为 PEM 格式
    raw_key = raw_key.replace('\n', '').replace('\r', '')
    lines = [raw_key[i:i+64] for i in range(0, len(raw_key), 64)]
    pem = "-----BEGIN PUBLIC KEY-----\n" + "\n".join(lines) + "\n-----END PUBLIC KEY-----"
    return pem


# Step 1: 获取 RSA 公钥（密钥）
def get_access_key():
    url = f"{BASE_HOST}{ACCESS_API_PATH}"
    response = requests.get(url)
    response_json = response.json()

    if response_json.get("code") == "10000":
        raw_key = response_json["data"]
        formatted_key = format_public_key(raw_key)
        return formatted_key
    else:
        raise Exception("获取密钥失败: " + response_json.get("message", ""))


# Step 2: 对密码进行 RSA 加密并 base64 编码
def encrypt_password(password: str, public_key_pem: str) -> str:
    public_key = serialization.load_pem_public_key(public_key_pem.encode('utf-8'))

    encrypted = public_key.encrypt(
        password.encode('utf-8'),
        padding.PKCS1v15()
    )

    encoded = base64.b64encode(encrypted).decode('utf-8')
    return encoded


# Step 3: 发送登录请求
def login(encrypted_password: str) -> dict:
    url = f"{BASE_HOST}{LOGIN_API_PATH}"
    payload = {
        "loginType": 2,
        "password": encrypted_password,
        "phoneNumber": USER,
        "tenantName": TENANT_NAME
    }

    response = requests.post(url, json=payload)
    result = response.json()

    if result.get("code") == "10000":
        token = result["data"]["token"]
        tenant_id = result["data"]["tenantId"]
        #print("登录成功，获取 token:", token)
        return {"token": token, "tenantId": tenant_id}
    else:
        raise Exception("登录失败：" + result.get("message", "未知错误"))


def generate(token: str, tenant_id: int, user_require: str) -> Optional[str]:
    url = f"{BASE_HOST}{STREAM_GENRATECODE}"
    headers = {
        "token": token,
        "tenantId": str(tenant_id),
        "Content-Type": "application/json"
    }

    payload = {
        "sessionId": "e12ac986-8948-4e65-9c3b-ffafcd365717",
        "oldCode": "",
        "projectId": 1887423,
        "serviceId": 1887457,
        "objectType": "Custom",
        "name": "User",
        "objectId": 1887490,
        "nodeInfo": [],
        "callRelationList": None,
        "configId": None,
        "exceptionInfo": None,
        "prompt": f"{user_require}",
        "promptAtInfoList": [],
        "relationIdList": [],
        "generateCodeType": 1,
        "fragmentCode": ""
    }

    try:
        response = requests.post(url, json=payload, headers=headers, stream=True)

        if response.status_code == 401:
            return None

        text_parts = []
        buffer_lines = []

        for line_bytes in response.iter_lines(decode_unicode=False):
            if not line_bytes or line_bytes.strip() == b"":
                if buffer_lines:
                    raw_bytes = b"".join(buffer_lines).strip()
                    buffer_lines = []
                    if raw_bytes and raw_bytes != b'""':
                        try:
                            raw_str = raw_bytes.decode('utf-8')
                            data = json.loads(raw_str)

                            if isinstance(data, dict):
                                # 主动处理 {"content": "..."} 结构
                                if "content" in data:
                                    text_parts.append(data["content"])

                                # 错误响应（带 code/message）
                                elif "code" in data and "message" in data:
                                    msg = f"错误 [{data['code']}]:{data['message']}"
                                    text_parts.append(msg)

                                # 特定结构处理（视接口而定）
                                elif "type" in data:
                                    data_type = data.get("type")
                                    if data_type == "text":
                                        text = data.get("text", "")
                                        text_parts.append(text)
                                    elif data_type == "dialogue":
                                        dialogue_id = data.get("dialogue", {}).get("id")
                                        info = f"对话 ID：{dialogue_id}"
                                        text_parts.append(info)
                                    elif data_type == "code":
                                        code_info = data.get("code", {})
                                        name = code_info.get("name")
                                        path = code_info.get("path")
                                        info = f"生成文件：{name}（路径：{path})"
                                        text_parts.append(info)

                                elif "message" in data:
                                    text_parts.append(f"提示：{data['message']}")
                                else:
                                    print("未处理的数据结构:", data)
                            else:
                                print("非 dict 类型数据，忽略:", repr(data))

                        except (UnicodeDecodeError, json.JSONDecodeError):
                            try:
                                fixed_raw = raw_bytes.decode('latin1').encode('utf-8', errors='replace').decode('utf-8')
                                #print("JSON 解码失败，修正后原始内容：", fixed_raw)
                            except Exception:
                                print("JSON 解码失败，原始内容(repr): " + repr(raw_bytes))
                continue

            # decode line
            try:
                line_str = line_bytes.decode('utf-8')
            except UnicodeDecodeError:
                line_str = line_bytes.decode('latin1')

            # 按 data/event 分别处理
            if line_str.startswith("data:"):
                buffer_lines.append(line_bytes[len("data:"):].lstrip())
            elif line_str.startswith("event:"):
                event_type = line_str[len("event:"):].strip()
                if event_type == "final":
                    return "".join(text_parts)
            else:
                print(f"未知行格式: {line_str}")

        return "".join(text_parts) if text_parts else None


    except requests.RequestException as e:
        raise RuntimeError(f"请求失败: {e}")


def save_token_cache(token: str, tenant_id: int):
    # 确保目录存在
    os.makedirs(os.path.dirname(TOKEN_CACHE_FILE), exist_ok=True)
    
    # 写入缓存文件
    with open(TOKEN_CACHE_FILE, "w", encoding="utf-8") as f:
        json.dump({
            "token": token,
            "tenantId": tenant_id
        }, f)

def load_token_cache():
    if not os.path.exists(TOKEN_CACHE_FILE):
        return None, None
    try:
        with open(TOKEN_CACHE_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
            return data.get("token"), data.get("tenantId")
    except Exception as e:
        #print("无法读取 token 缓存:", e)
        return None, None

def login_and_get_token():
    key = get_access_key()
    encrypted_pwd = encrypt_password(PASSWORD, key)
    login_result = login(encrypted_pwd)

    token = login_result["token"]
    tenant_id = login_result["tenantId"]
    
    save_token_cache(token, tenant_id)  # 写入缓存

    return token, tenant_id


def get_model_answer(content: str) -> Optional[str]:
    try:
        token, tenant_id = load_token_cache()
        if not token or not tenant_id:
            token, tenant_id = login_and_get_token()

        response = generate(token, tenant_id, content)

        # 如果返回 None，则尝试重新登录再调用一次
        if response is None:
            token, tenant_id = login_and_get_token()
            response = generate(token, tenant_id, content)

        if response:
            return response
        else:
            raise RuntimeError("未能获取生成内容")

    except Exception as e:
        raise e