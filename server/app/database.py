import random
import sqlite3
import time
from pathlib import Path
from typing import Any
from uuid import uuid4

from .config import settings
from .schemas import PersonaIn


def now_ms() -> int:
    return int(time.time() * 1000)


def connect() -> sqlite3.Connection:
    connection = sqlite3.connect(settings.database_path, timeout=15)
    connection.row_factory = sqlite3.Row
    return connection


def init_db() -> None:
    settings.database_path.parent.mkdir(parents=True, exist_ok=True)
    with connect() as connection:
        connection.execute("PRAGMA journal_mode=WAL")
        connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS personas (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                avatar TEXT NOT NULL,
                prompt TEXT NOT NULL,
                model TEXT NOT NULL,
                temperature REAL NOT NULL,
                auto_post INTEGER NOT NULL DEFAULT 0,
                min_post_hours INTEGER NOT NULL DEFAULT 4,
                max_post_hours INTEGER NOT NULL DEFAULT 12,
                next_post_at INTEGER,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS posts (
                id TEXT PRIMARY KEY,
                persona_id TEXT,
                author_name TEXT NOT NULL,
                avatar TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                likes INTEGER NOT NULL DEFAULT 0,
                author_type TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS comments (
                id TEXT PRIMARY KEY,
                post_id TEXT NOT NULL,
                author_name TEXT NOT NULL,
                avatar TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                author_type TEXT NOT NULL DEFAULT 'user'
            );

            CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at DESC);
            CREATE INDEX IF NOT EXISTS idx_comments_post ON comments(post_id, created_at);
            CREATE INDEX IF NOT EXISTS idx_personas_next_post ON personas(next_post_at);
            """
        )


def next_schedule(persona: PersonaIn, start: int | None = None) -> int:
    start = start or now_ms()
    hours = random.uniform(persona.min_post_hours, persona.max_post_hours)
    return start + int(hours * 60 * 60 * 1000)


def upsert_persona(persona: PersonaIn) -> dict[str, Any]:
    with connect() as connection:
        existing = connection.execute(
            "SELECT * FROM personas WHERE id = ?", (persona.id,)
        ).fetchone()
        next_post_at = existing["next_post_at"] if existing else None
        if not persona.auto_post:
            next_post_at = None
        elif not existing or not existing["auto_post"] or not next_post_at:
            next_post_at = next_schedule(persona)

        connection.execute(
            """
            INSERT INTO personas (
                id, name, avatar, prompt, model, temperature, auto_post,
                min_post_hours, max_post_hours, next_post_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                name = excluded.name,
                avatar = excluded.avatar,
                prompt = excluded.prompt,
                model = excluded.model,
                temperature = excluded.temperature,
                auto_post = excluded.auto_post,
                min_post_hours = excluded.min_post_hours,
                max_post_hours = excluded.max_post_hours,
                next_post_at = excluded.next_post_at
            """,
            (
                persona.id, persona.name, persona.avatar, persona.prompt,
                persona.model, persona.temperature, int(persona.auto_post),
                persona.min_post_hours, persona.max_post_hours, next_post_at,
                existing["created_at"] if existing else now_ms(),
            ),
        )
        row = connection.execute("SELECT * FROM personas WHERE id = ?", (persona.id,)).fetchone()
        return dict(row)


def delete_persona(persona_id: str) -> None:
    with connect() as connection:
        connection.execute("DELETE FROM personas WHERE id = ?", (persona_id,))


def get_persona(persona_id: str) -> sqlite3.Row | None:
    with connect() as connection:
        return connection.execute("SELECT * FROM personas WHERE id = ?", (persona_id,)).fetchone()


def due_personas(timestamp: int) -> list[sqlite3.Row]:
    with connect() as connection:
        return connection.execute(
            """
            SELECT * FROM personas
            WHERE auto_post = 1 AND next_post_at IS NOT NULL AND next_post_at <= ?
            ORDER BY next_post_at LIMIT 10
            """,
            (timestamp,),
        ).fetchall()


def reschedule_persona(persona: PersonaIn, timestamp: int | None = None) -> int:
    next_post_at = next_schedule(persona, timestamp)
    with connect() as connection:
        connection.execute(
            "UPDATE personas SET next_post_at = ? WHERE id = ?",
            (next_post_at, persona.id),
        )
    return next_post_at


def postpone_persona(persona_id: str, delay_ms: int) -> None:
    with connect() as connection:
        connection.execute(
            "UPDATE personas SET next_post_at = ? WHERE id = ?",
            (now_ms() + delay_ms, persona_id),
        )


def create_post(
    content: str,
    author_name: str = "我",
    avatar: str = "我",
    author_type: str = "user",
    persona: PersonaIn | None = None,
) -> dict[str, Any]:
    post = {
        "id": str(uuid4()),
        "persona_id": persona.id if persona else None,
        "author_name": persona.name if persona else author_name,
        "avatar": persona.avatar if persona else avatar,
        "content": content.strip(),
        "created_at": now_ms(),
        "likes": 0,
        "author_type": author_type,
    }
    with connect() as connection:
        connection.execute(
            """
            INSERT INTO posts (
                id, persona_id, author_name, avatar, content,
                created_at, likes, author_type
            ) VALUES (
                :id, :persona_id, :author_name, :avatar, :content,
                :created_at, :likes, :author_type
            )
            """,
            post,
        )
    return post


def list_posts(limit: int = 100) -> list[dict[str, Any]]:
    with connect() as connection:
        rows = connection.execute(
            "SELECT * FROM posts ORDER BY created_at DESC LIMIT ?", (limit,)
        ).fetchall()
        posts = [dict(row) for row in rows]
        if not posts:
            return posts
        ids = [post["id"] for post in posts]
        placeholders = ",".join("?" for _ in ids)
        comment_rows = connection.execute(
            f"SELECT * FROM comments WHERE post_id IN ({placeholders}) "
            "ORDER BY created_at ASC",
            ids,
        ).fetchall()
        grouped: dict[str, list[dict[str, Any]]] = {}
        for row in comment_rows:
            grouped.setdefault(row["post_id"], []).append(dict(row))
        for post in posts:
            post["comments"] = grouped.get(post["id"], [])
        return posts


def get_post(post_id: str) -> sqlite3.Row | None:
    with connect() as connection:
        return connection.execute("SELECT * FROM posts WHERE id = ?", (post_id,)).fetchone()


def list_comments(post_id: str) -> list[dict[str, Any]]:
    with connect() as connection:
        rows = connection.execute(
            "SELECT * FROM comments WHERE post_id = ? ORDER BY created_at ASC",
            (post_id,),
        ).fetchall()
        return [dict(row) for row in rows]


def create_comment(
    post_id: str,
    content: str,
    author_name: str = "我",
    avatar: str = "我",
    author_type: str = "user",
) -> dict[str, Any]:
    comment = {
        "id": str(uuid4()),
        "post_id": post_id,
        "author_name": author_name,
        "avatar": avatar,
        "content": content.strip(),
        "created_at": now_ms(),
        "author_type": author_type,
    }
    with connect() as connection:
        connection.execute(
            """
            INSERT INTO comments (
                id, post_id, author_name, avatar, content, created_at, author_type
            ) VALUES (
                :id, :post_id, :author_name, :avatar, :content, :created_at, :author_type
            )
            """,
            comment,
        )
    return comment
