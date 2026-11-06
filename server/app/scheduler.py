import asyncio
import logging

from . import database
from .ai import generate_space_post
from .config import settings
from .schemas import PersonaIn

logger = logging.getLogger(__name__)


def row_to_persona(row) -> PersonaIn:
    return PersonaIn(
        id=row["id"],
        name=row["name"],
        avatar=row["avatar"],
        prompt=row["prompt"],
        model=row["model"],
        temperature=row["temperature"],
        auto_post=bool(row["auto_post"]),
        min_post_hours=row["min_post_hours"],
        max_post_hours=row["max_post_hours"],
    )


async def run_due_posts() -> int:
    due = database.due_personas(database.now_ms())
    published = 0
    for row in due:
        persona = row_to_persona(row)
        try:
            content = await generate_space_post(persona)
            database.create_post(
                content=content,
                author_type="ai",
                persona=persona,
            )
            database.reschedule_persona(persona)
            published += 1
            logger.info("Published scheduled post for persona %s", persona.id)
        except Exception:
            logger.exception("Scheduled post failed for persona %s", persona.id)
            database.postpone_persona(persona.id, 15 * 60 * 1000)
    return published


async def scheduler_loop() -> None:
    while True:
        try:
            await run_due_posts()
        except Exception:
            logger.exception("Scheduler iteration failed")
        await asyncio.sleep(settings.scheduler_poll_seconds)
