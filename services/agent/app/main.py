"""助手进程入口。只监听本机，只挂内网对话路由。

检查点用连接池。取出前先探活，断了换一条。没配数据库、测试进程，
或者启动时库连不上，都不阻止进程起来：健康检查不查库，
授权恢复会退回每轮一张内存图，下一句接不上暂停。
启动之后库再断开，下一轮会再要一条连接。
"""

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.invoke import router
from app.graph.builder import open_saver
from app.observability.log import checkpoint_down


@asynccontextmanager
async def lifespan(app: FastAPI):
    """进程启动时打开检查点连接池，退出时关掉池。

    测试会导入这个应用。若在 pytest 里真去连库，单测就依赖本机 Postgres。
    连接串里有口令，失败时不把异常打出来，避免口令进日志。

    @param app 当前进程的 FastAPI 实例，检查点挂在 app.state 上供开一轮使用
    """
    saver = None
    host = os.environ.get("PG_HOST")
    if host and not os.environ.get("PYTEST_CURRENT_TEST"):
        user = os.environ.get("PG_USERNAME", "")
        password = os.environ.get("PG_PASSWORD", "")
        port = os.environ.get("PG_PORT", "5432")
        database = os.environ.get("PG_DATABASE", "omino_shop_ai")
        url = f"postgresql://{user}:{password}@{host}:{port}/{database}"
        try:
            saver = await open_saver(url)
            app.state.checkpointer = saver
        except Exception:
            # 库不可用时仍提供 /health 和当轮对话，只是同一会话的授权恢复接不上。
            checkpoint_down()
            app.state.checkpointer = None
    yield
    if saver is not None:
        await saver.conn.close()


app = FastAPI(lifespan=lifespan)
app.include_router(router)


@app.get("/health")
def health() -> dict[str, str]:
    """给启动脚本看进程是否在听。不访问数据库，库没连上也会返回。

    @return 固定的存活标记
    """
    return {"status": "ok"}
