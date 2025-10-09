import requests


BASE_URL = "https://llm.teleai.com.cn"
TOKEN = "sk_01HV3Y4904JXQ67890abcdef"
OPERTOKEN = "eyJhbGciOiJIUzUxMiIsInppcCI6IkdaSVAifQ.H4sIAAAAAAAAAG2RO07EMBCG7-Lai2LnnYqXECukLXhUiMJZzxJDYgfbEawQBddAgnNQcJxtuAVjwi4IKOf7Z37PP74nV16RisQyZSxL64ksGJ8kEpJJvZDZJBNFDDJa1HMBhBIlPKlYnkac51lZUOKGGqfd0nnogu4clqYHK7wyGokY5Bc5c2ARwF0fLJIi5UkeM0o8aKH9NLQlLMGOARtDWZRREXP6PVzdE_WDzy0ID6eqA1LpoW1xspe_iIT2oBWX61KLIJFlc4Pv9MK5W2PlWoROqBbVpmm2WRZvzU2I1DdGw2zo6rAAYWkclZyXaZmidgt7jfBnGqNONzbOCz-EMzAS3u_HaOtiNm6wentcvT4FaE0LU4n955zGNLnAFy048McbniU0ZzTnNCsu_jnXCP74KrcjO6WV8_gXxqJRhBG1B9tb5WBcKg4pvuEfkw70cOKXLXxOL5R1_tB0sItBwStNqoVoHTzgEuYa9Hhp8v78crJ_dBoIefgAzWZw32ECAAA.k3yBiYzPghF6u6G9XNvFO9NkPUsjKebMIsEuNT7smP0jkAG2zdyWaKdYPqs1-88vyvGSoooeXKwJqvWtRB5HHA"

USE_STREAM = False
PROXY_BATCH_URL = "/kms/api/v1/sync/chat/completion"  
PROXY_STREAM_URL = "/kms/api/v1/sse/chat/completion"
def get_stream():
    return PROXY_STREAM_URL if USE_STREAM else PROXY_BATCH_URL


def get_model_answer(question: str):
    url = f"{BASE_URL}{get_stream()}"

    headers = {
        "Authorization": f"Bearer {TOKEN}",
        "Content-Type": "application/json",
        "Opertoken": f"{OPERTOKEN}"
    }

    data = {
        "model": "",
        "messages": [
            {
                "role": "user",
                "content": f"{question}"
            }
        ],
        "sessionId": "sessionId_db83123a8762",
        "stream": f"{USE_STREAM}"
    }

    response = requests.post(url, headers=headers, json=data)
    full_answer = ""

    try:
        if response.status_code == 200:
            resp_json = response.json()
            
            # 提取相关字段
            full_answer = resp_json["choices"][0]["message"]["content"]
        else:
            raise RuntimeError("未能获取生成内容")

    except Exception as e:
        raise e

    return full_answer
