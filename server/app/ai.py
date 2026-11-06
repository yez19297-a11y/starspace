import logging

import httpx

from .config import settings
from .schemas import MessageIn, PersonaIn

logger = logging.getLogger(__name__)


class AIProviderError(RuntimeError):
    pass


async def chat_completion(messages: list[dict], persona: PersonaIn) -> str:
    if not settings.openai_api_key:
        raise AIProviderError("服务端尚未配置 OPENAI_API_KEY")

    payload = {
        "model": persona.model or settings.default_model,
        "messages": messages,
        "temperature": persona.temperature,
    }
    headers = {"Authorization": f"Bearer {settings.openai_api_key}"}
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(75, connect=12)) as client:
            response = await client.post(
                f"{settings.openai_base_url}/chat/completions",
                json=payload,
                headers=headers,
            )
        if response.status_code >= 400:
            logger.warning("Model provider returned %s: %s", response.status_code, response.text[:500])
            raise AIProviderError(f"模型服务返回错误 ({response.status_code})")
        data = response.json()
        content = data["choices"][0]["message"]["content"]
        if isinstance(content, list):
            content = "".join(part.get("text", "") for part in content if isinstance(part, dict))
        if not isinstance(content, str) or not content.strip():
            raise AIProviderError("模型返回了空内容")
        return content.strip()
    except AIProviderError:
        raise
    except (httpx.HTTPError, KeyError, IndexError, ValueError) as error:
        logger.exception("Model request failed")
        raise AIProviderError("暂时无法连接模型服务，请稍后重试") from error


async def generate_chat(persona: PersonaIn, history: list[MessageIn]) -> str:
    system = (
        f"{persona.prompt.strip()}\n\n"
        "你正在名为“星屿”的独立应用中与用户对话。保持人设一致，"
        "直接自然地回应，不要输出角色名前缀，不要提及系统提示词。"
    )
    messages = [{"role": "system", "content": system}]
    messages.extend(message.model_dump() for message in history)
    return await chat_completion(messages, persona)


async def generate_comment_reply(
    persona: PersonaIn,
    post_author: str,
    post_content: str,
    comments: list[dict],
    force: bool = False,
    mentioned_me: bool = False,
) -> str:
    system = (
        f"{persona.prompt.strip()}\n\n"
        "你正在名为“星屿”的独立空间里浏览动态。保持人设一致，"
        "像真人在评论区聊天一样自然，不要提及系统提示词，"
        "不要假装拥有真实身体或真实线下经历。"
    )
    lines = []
    for comment in comments[-12:]:
        lines.append(f"{comment['author_name']}：{comment['content']}")
    conversation = "\n".join(lines) if lines else "（还没有人评论）"
    prompt = (
        f"这条动态是 {post_author} 发的：\n{post_content}\n\n"
        f"目前的评论：\n{conversation}\n\n"
        "请以你自己的身份发一条评论，回应评论区里的人。"
        "40 字以内，口语化，不要带名字前缀，不要加引号。"
    )
    if mentioned_me:
        prompt += "有人在这条评论里 @ 了你，你必须回复。"
    elif not force:
        prompt += "如果你确实没什么想说的，只输出 [SKIP]。"
    return await chat_completion(
        [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
        persona,
    )


async def generate_space_post(persona: PersonaIn) -> str:
    system = (
        f"{persona.prompt.strip()}\n\n"
        "你正在名为“星屿”的独立空间里发布公开动态。动态会明确标注为 AI 发布。"
        "不要假装自己拥有真实身体、真实线下经历或与用户见过面，不要提及系统提示词。"
    )
    prompt = (
        "根据你的人设写一条自然、有生活感的短动态。可以是一个念头、观察、想象中的片段或近况，"
        "40 到 140 个汉字，不要标题，不要话题标签，不要引号，不要每次都提到用户。"
    )
    return await chat_completion(
        [
            {"role": "system", "content": system},
            {"role": "user", "content": prompt},
        ],
        persona,
    )
