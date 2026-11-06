import os
from dataclasses import dataclass, field
from pathlib import Path

from dotenv import load_dotenv

SERVER_DIR = Path(__file__).resolve().parents[1]
load_dotenv(SERVER_DIR / ".env")


@dataclass
class Settings:
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_base_url: str = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
    default_model: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    database_path: Path = Path(os.getenv("STARSPACE_DB", str(SERVER_DIR / "starspace.db")))
    scheduler_enabled: bool = os.getenv("ENABLE_SCHEDULER", "true").lower() == "true"
    scheduler_poll_seconds: int = max(5, int(os.getenv("SCHEDULER_POLL_SECONDS", "30")))


settings = Settings()


def update_llm_config(api_key: str, base_url: str, default_model: str) -> None:
    """更新 LLM 服务配置（运行时生效，不持久化到 .env）。"""
    global settings
    settings.openai_api_key = api_key or settings.openai_api_key
    settings.openai_base_url = (base_url or settings.openai_base_url).rstrip("/")
    settings.default_model = default_model or settings.default_model
