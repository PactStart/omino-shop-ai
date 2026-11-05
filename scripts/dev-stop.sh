#!/usr/bin/env bash
# 按端口停止本仓库的一个本地应用。
# 用法: dev-stop.sh PORT NEEDLE NAME
# NEEDLE 是 grep -E。监听进程或它的父进程命令行必须命中，否则不杀。
# 命中后连同这条链上的父进程一起停，这样 uvicorn --reload 不会把子进程拉起来。

set -u

port="${1:?port}"
needle="${2:?needle}"
name="${3:?name}"

if ! command -v lsof >/dev/null 2>&1; then
  echo "缺少 lsof，无法停止 ${name}" >&2
  exit 1
fi

listen_pids() {
  lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null | sort -u || true
}

matches() {
  local cmd
  cmd="$(ps -p "$1" -o command= 2>/dev/null || true)"
  [ -n "${cmd}" ] && printf '%s\n' "${cmd}" | grep -Eq -- "${needle}"
}

pids="$(listen_pids)"
if [ -z "${pids}" ]; then
  echo "${name} 没在跑（:${port}）"
  exit 0
fi

kill_set=""
foreign=""
for pid in ${pids}; do
  chain="${pid}"
  cur="${pid}"
  hit=0
  if matches "${pid}"; then
    hit=1
  fi
  while true; do
    ppid="$(ps -p "${cur}" -o ppid= 2>/dev/null | tr -d ' ' || true)"
    if [ -z "${ppid}" ] || [ "${ppid}" = "1" ] || [ "${ppid}" = "0" ]; then
      break
    fi
    if matches "${ppid}"; then
      hit=1
      chain="${ppid} ${chain}"
      cur="${ppid}"
      continue
    fi
    break
  done
  if [ "${hit}" = "1" ]; then
    kill_set="${kill_set} ${chain}"
  else
    foreign="${foreign} ${pid}"
  fi
done

unique="$(printf '%s\n' ${kill_set} | awk 'NF && !seen[$0]++')"
if [ -z "${unique}" ]; then
  echo "${name} 的端口 :${port} 被其他进程占用，未停止" >&2
  ps -p ${foreign} -o pid=,command= >&2 || true
  exit 1
fi

echo "停止 ${name}（:${port}）"
# 同一条命令里停掉整条链，避免 reload 父进程先收到子进程退出后又拉起一个。
kill ${unique} 2>/dev/null || true

for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  left="$(listen_pids)"
  still=""
  for pid in ${left}; do
    if printf '%s\n' ${unique} | grep -qx "${pid}" || matches "${pid}"; then
      still="${still} ${pid}"
    fi
  done
  if [ -z "${still}" ]; then
    if [ -n "${foreign}" ]; then
      echo "${name} 已停，但 :${port} 仍有其他进程" >&2
      exit 1
    fi
    echo "${name} 已停"
    exit 0
  fi
  sleep 0.25
done

echo "${name} 未在 5 秒内退出，强制结束" >&2
kill -9 ${unique} 2>/dev/null || true
sleep 0.3
left="$(listen_pids)"
still=""
for pid in ${left}; do
  if printf '%s\n' ${unique} | grep -qx "${pid}" || matches "${pid}"; then
    still="${still} ${pid}"
  fi
done
if [ -n "${still}" ]; then
  echo "${name} 仍在监听 :${port}" >&2
  exit 1
fi
echo "${name} 已停"
