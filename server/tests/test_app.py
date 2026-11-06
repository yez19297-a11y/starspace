import asyncio
from types import SimpleNamespace

from fastapi.testclient import TestClient

from app import database, main, scheduler


def configure_test_db(monkeypatch, tmp_path):
    db_path = tmp_path / "test.db"
    monkeypatch.setattr(database, "settings", SimpleNamespace(database_path=db_path))
    monkeypatch.setattr(main, "settings", SimpleNamespace(
        scheduler_enabled=False,
        openai_api_key="test-key",
        openai_base_url="https://api.openai.com/v1",
        default_model="gpt-4o-mini",
    ))


def persona_payload(**overrides):
    payload = {
        "id": "persona-test",
        "name": "测试伙伴",
        "avatar": "测",
        "prompt": "真诚、简短地交流。",
        "model": "gpt-4o-mini",
        "temperature": 0.8,
        "auto_post": True,
        "min_post_hours": 4,
        "max_post_hours": 8,
    }
    payload.update(overrides)
    return payload


def test_persona_and_space_crud(monkeypatch, tmp_path):
    configure_test_db(monkeypatch, tmp_path)
    with TestClient(main.app) as client:
        response = client.post("/v1/personas/upsert", json=persona_payload())
        assert response.status_code == 200
        saved = response.json()["persona"]
        assert saved["next_post_at"] > database.now_ms()

        response = client.post("/v1/space/posts", json={
            "author_type": "user",
            "author_name": "我",
            "avatar": "我",
            "content": "第一条独立动态",
        })
        assert response.status_code == 201
        assert response.json()["post"]["author_type"] == "user"

        response = client.get("/v1/space/posts")
        assert response.status_code == 200
        assert response.json()["posts"][0]["content"] == "第一条独立动态"


def test_chat_uses_individual_persona(monkeypatch, tmp_path):
    configure_test_db(monkeypatch, tmp_path)
    captured = {}

    async def fake_generate(persona, history):
        captured["persona"] = persona
        captured["history"] = history
        return "这是测试回复"

    monkeypatch.setattr(main, "generate_chat", fake_generate)
    with TestClient(main.app) as client:
        response = client.post("/v1/chat", json={
            "persona": persona_payload(auto_post=False),
            "messages": [{"role": "user", "content": "你好"}],
        })
    assert response.status_code == 200
    assert response.json()["content"] == "这是测试回复"
    assert captured["persona"].prompt == "真诚、简短地交流。"
    assert captured["history"][0].content == "你好"


def test_scheduler_publishes_and_reschedules(monkeypatch, tmp_path):
    configure_test_db(monkeypatch, tmp_path)
    database.init_db()
    persona = scheduler.row_to_persona({
        "id": "persona-test",
        "name": "测试伙伴",
        "avatar": "测",
        "prompt": "真诚、简短地交流。",
        "model": "gpt-4o-mini",
        "temperature": 0.8,
        "auto_post": 1,
        "min_post_hours": 4,
        "max_post_hours": 8,
    })
    database.upsert_persona(persona)
    with database.connect() as connection:
        connection.execute(
            "UPDATE personas SET next_post_at = ? WHERE id = ?",
            (database.now_ms() - 1000, persona.id),
        )

    async def fake_post(_):
        return "一条由测试模型生成的动态"

    monkeypatch.setattr(scheduler, "generate_space_post", fake_post)
    published = asyncio.run(scheduler.run_due_posts())
    assert published == 1
    posts = database.list_posts()
    assert posts[0]["author_type"] == "ai"
    assert posts[0]["content"] == "一条由测试模型生成的动态"
    assert database.get_persona(persona.id)["next_post_at"] > database.now_ms()


def test_invalid_schedule_is_rejected(monkeypatch, tmp_path):
    configure_test_db(monkeypatch, tmp_path)
    with TestClient(main.app) as client:
        response = client.post(
            "/v1/personas/upsert",
            json=persona_payload(min_post_hours=10, max_post_hours=4),
        )
    assert response.status_code == 422


def test_llm_config_update(monkeypatch, tmp_path):
    configure_test_db(monkeypatch, tmp_path)
    with TestClient(main.app) as client:
        response = client.post("/v1/config/llm", json={
            "api_key": "sk-new-key",
            "base_url": "https://api.deepseek.com/v1",
            "default_model": "deepseek-chat",
        })
    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert body["base_url"] == "https://api.deepseek.com/v1"
    assert body["default_model"] == "deepseek-chat"
    assert body["model_configured"] is True
