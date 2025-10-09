import requests
import base64
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes
import json
import os
from typing import Optional

#api接口--对话接口 新版

TOKEN_CACHE_FILE = "rjgc/token_cache.json"
BASE_HOST = "https://starfactoryapi.teleagi.cn"
ACCESS_API_PATH = "/user/getAccessKey"
LOGIN_API_PATH = "/user/login"
CHAT_COMPLETIONS = "/codegen/chat/sendConversation"

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
    url = f"{BASE_HOST}{CHAT_COMPLETIONS}"
    headers = {
        "token": token,
        "tenantId": str(tenant_id),
        "Content-Type": "application/json"
    }

    payload = {
        "projectId": "1861526",
        "serviceIds": [1861560],
        "prompt": user_require,
        "userSelectFile": [],
        "sessionId": "87b5ccd6-83dc-4967-bcf1-40eb96dac6cf",
        "promptAtInfoList": []
    }

    try:
        response = requests.post(url, json=payload, headers=headers, stream=True)

        if response.status_code == 401:
            #print("token 已过期或无效，需重新登录。")
            return None

        result_text = ""
        last_chunk = None

        for line_bytes in response.iter_lines(decode_unicode=False):
            if not line_bytes:
                continue

            try:
                line = line_bytes.decode('utf-8', errors='replace').strip()
            except Exception:
                continue

            if line == "[DONE]":
                break

            if line.startswith("data:"):
                json_str = line[len("data:"):].strip()

                try:
                    parsed = json.loads(json_str)

                    if isinstance(parsed, str):
                        continue

                    content = parsed.get("content")
                    if content and content != last_chunk:
                        result_text += content
                        last_chunk = content

                except Exception as e:
                    print("JSON解析失败:", e)
                    continue
            elif line.startswith("event:"):
                continue

        return result_text if result_text else "ERROR:EmptyResponse"

    except Exception as e:
        return f"ERROR:{type(e).__name__}:{str(e)}"


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
        print("无法读取 token 缓存:", e)
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
            #print("没有找到有效缓存，准备登录")
            token, tenant_id = login_and_get_token()

        response = generate(token, tenant_id, content)

        # 如果返回 None，则尝试重新登录再调用一次
        if response is None:
            #print("\n请求失败，尝试重新登录后重试 generate_code ...")
            token, tenant_id = login_and_get_token()
            response = generate(token, tenant_id, content)

        if response:
            return response
        else:
            raise RuntimeError("未能获取生成内容")

    except Exception as e:
        raise e
