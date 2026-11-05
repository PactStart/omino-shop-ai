from fastapi.testclient import TestClient

from app.main import app


def test_missing_token_is_rejected(monkeypatch):
    monkeypatch.setenv("AGENT_INTERNAL_TOKEN", "secret")
    response = TestClient(app).post("/internal/v1/graph/invoke", json={"text": "你好"})
    assert response.status_code == 401
