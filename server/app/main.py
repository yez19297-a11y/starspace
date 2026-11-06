import asyncio
import logging
from contextlib import asynccontextmanager, suppress

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from . import database
from .ai import AIProviderError, generate_chat, generate_comment_reply
from . import config
from .config import settings, update_llm_config
from .scheduler import row_to_persona, scheduler_loop
from .schemas import (
    AICommentIn,
    ChatRequest,
    CommentIn,
    DeletePersonaIn,
    LLMConfigIn,
    PersonaIn,
    PostIn,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


@asynccontextmanager
async def lifespan(_: FastAPI):
    database.init_db()
    task = None
    if settings.scheduler_enabled:
        task = asyncio.create_task(scheduler_loop())
    yield
    if task:
        task.cancel()
        with suppress(asyncio.CancelledError):
            await task


app = FastAPI(title="星屿服务", version="0.1.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {
        "ok": True,
        "model_configured": bool(settings.openai_api_key),
        "scheduler_enabled": settings.scheduler_enabled,
    }


@app.post("/v1/config/llm")
def update_llm(request: LLMConfigIn):
    update_llm_config(request.api_key, request.base_url, request.default_model)
    return {
        "ok": True,
        "model_configured": bool(config.settings.openai_api_key),
        "base_url": config.settings.openai_base_url,
        "default_model": config.settings.default_model,
    }


@app.post("/v1/chat")
async def chat(request: ChatRequest):
    try:
        content = await generate_chat(request.persona, request.messages)
        return {"content": content}
    except AIProviderError as error:
        raise HTTPException(status_code=503, detail=str(error)) from error


@app.post("/v1/personas/upsert")
def upsert_persona(persona: PersonaIn):
    return {"persona": database.upsert_persona(persona)}


@app.post("/v1/personas/delete")
def delete_persona(request: DeletePersonaIn):
    database.delete_persona(request.id)
    return {"ok": True}


@app.get("/v1/space/posts")
def get_posts(limit: int = Query(default=100, ge=1, le=200)):
    return {"posts": database.list_posts(limit)}


@app.post("/v1/space/posts", status_code=201)
def create_post(post: PostIn):
    created = database.create_post(
        content=post.content,
        author_name=post.author_name,
        avatar=post.avatar,
        author_type="user",
    )
    return {"post": created}


@app.get("/v1/space/posts/{post_id}/comments")
def get_comments(post_id: str):
    return {"comments": database.list_comments(post_id)}


@app.post("/v1/space/posts/{post_id}/comments", status_code=201)
def create_comment(post_id: str, comment: CommentIn):
    if database.get_post(post_id) is None:
        raise HTTPException(status_code=404, detail="动态不存在或尚未同步到后端")
    created = database.create_comment(
        post_id=post_id,
        content=comment.content,
        author_name=comment.author_name,
        avatar=comment.avatar,
        author_type=comment.author_type,
    )
    return {"comment": created}


@app.post("/v1/space/posts/{post_id}/ai-comments", status_code=201)
async def create_ai_comment(post_id: str, body: AICommentIn):
    """让某个 AI 角色读一遍动态和评论，写一条回复。"""
    post = database.get_post(post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="动态不存在或尚未同步到后端")
    row = database.get_persona(body.persona_id)
    if row is None:
        raise HTTPException(status_code=404, detail="角色不存在或尚未同步到后端")

    persona = row_to_persona(row)
    comments = database.list_comments(post_id)
    try:
        content = await generate_comment_reply(
            persona,
            post_author=post["author_name"],
            post_content=post["content"],
            comments=comments,
            force=body.force,
            mentioned_me=body.mentioned,
        )
    except AIProviderError as error:
        raise HTTPException(status_code=502, detail=str(error)) from error

    if "[SKIP]" in content or not content.strip():
        return {"comment": None}

    created = database.create_comment(
        post_id=post_id,
        content=content.strip().strip("“”\""),
        author_name=persona.name,
        avatar=persona.avatar,
        author_type="ai",
    )
    return {"comment": created}
