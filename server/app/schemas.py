from typing import Literal
from uuid import uuid4

from pydantic import BaseModel, Field, model_validator


class PersonaIn(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid4()), min_length=1, max_length=80)
    name: str = Field(min_length=1, max_length=30)
    avatar: str = Field(default="星", min_length=1, max_length=4)
    prompt: str = Field(min_length=1, max_length=8000)
    model: str = Field(default="", max_length=100)
    temperature: float = Field(default=0.8, ge=0, le=2)
    auto_post: bool = False
    min_post_hours: int = Field(default=4, ge=1, le=168)
    max_post_hours: int = Field(default=12, ge=1, le=720)

    @model_validator(mode="after")
    def validate_schedule(self):
        if self.max_post_hours < self.min_post_hours:
            raise ValueError("max_post_hours 不能小于 min_post_hours")
        return self


class MessageIn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=12000)


class ChatRequest(BaseModel):
    persona: PersonaIn
    messages: list[MessageIn] = Field(min_length=1, max_length=30)


class PostIn(BaseModel):
    author_type: Literal["user"] = "user"
    author_name: str = Field(default="我", min_length=1, max_length=30)
    avatar: str = Field(default="我", min_length=1, max_length=4)
    content: str = Field(min_length=1, max_length=2000)


class CommentIn(BaseModel):
    author_type: Literal["user", "ai"] = "user"
    author_name: str = Field(default="我", min_length=1, max_length=30)
    avatar: str = Field(default="我", min_length=1, max_length=8)
    content: str = Field(min_length=1, max_length=500)


class AICommentIn(BaseModel):
    persona_id: str = Field(min_length=1, max_length=80)
    force: bool = False
    mentioned: bool = False


class DeletePersonaIn(BaseModel):
    id: str = Field(min_length=1, max_length=80)


class LLMConfigIn(BaseModel):
    api_key: str = Field(default="", max_length=500)
    base_url: str = Field(default="https://api.openai.com/v1", max_length=300)
    default_model: str = Field(default="gpt-4o-mini", max_length=100)
