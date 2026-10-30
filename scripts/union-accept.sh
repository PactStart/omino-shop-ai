#!/usr/bin/env bash
# 本地验收：搜品、详情、转链。不进 make test，不拉订单。
# APP 须已在跑。用法：scripts/union-accept.sh [taobao|jd|pdd|vip]
# 不带参数则四个都跑。KEYWORD 默认「纸巾」。

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -z "${AGENT_INTERNAL_TOKEN:-}" ]; then
  if [ ! -f "$ROOT/deploy/env/local.infra.env" ] || [ ! -f "$ROOT/deploy/env/local.app.env" ]; then
    echo "缺少 deploy/env/local.infra.env 或 local.app.env，先 make env"
    exit 1
  fi
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/deploy/env/local.infra.env"
  # shellcheck disable=SC1091
  . "$ROOT/deploy/env/local.app.env"
  set +a
fi

if [ "${APP_ENV:-local}" != "local" ]; then
  echo "只在 APP_ENV=local 跑验收"
  exit 1
fi

BASE="${APP_BASE:-http://127.0.0.1:${SERVER_PORT:-8181}/api/app}"
PHONE="${ACCEPT_PHONE:-13800138000}"
KEYWORD="${KEYWORD:-纸巾}"
case "$KEYWORD" in
  *\"*|*\\*)
    echo "关键词不要带引号或反斜杠"
    exit 1
    ;;
esac

if ! curl -fsS --max-time 5 "$BASE/actuator/health" >/dev/null; then
  echo "APP 没在 $BASE 响应。先 make app"
  exit 1
fi

sql() {
  local query="$1"
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="$PG_PASSWORD" psql -h "$PG_HOST" -p "${PG_PORT:-5432}" -U "$PG_USERNAME" -d "$PG_DATABASE" -tA -c "$query"
  else
    docker compose -f "$ROOT/deploy/docker-compose.local.yml" --env-file "$ROOT/deploy/env/local.infra.env" \
      exec -T postgres psql -U "$PG_USERNAME" -d "$PG_DATABASE" -tA -c "$query"
  fi
}

count_of() {
  sql "$1" | tr -d '[:space:]'
}

scan_secrets() {
  local file="$1"
  local name value
  for name in UNION_TAOBAO_APP_KEY UNION_TAOBAO_APP_SECRET UNION_JD_APP_KEY UNION_JD_APP_SECRET \
    UNION_PDD_CLIENT_ID UNION_PDD_CLIENT_SECRET UNION_VIP_APP_KEY UNION_VIP_APP_SECRET \
    AGENT_INTERNAL_TOKEN JWT_ACCESS_SECRET; do
    value="${!name-}"
    if [ -n "$value" ] && [ "${#value}" -ge 8 ] && grep -F -q -- "$value" "$file"; then
      echo "响应里出现了密钥"
      return 1
    fi
  done
}

# 按固定行数读信封。空字段也占一行，方便 bash 3.2。
read_parsed() {
  lines=()
  local line
  while IFS= read -r line; do
    lines[${#lines[@]}]="$line"
  done
  if [ "${#lines[@]}" -eq 0 ]; then
    lines=("" "响应不是 JSON")
  fi
}

parse_json() {
  python3 -c '
import json, sys
mode = sys.argv[1]
body = json.load(sys.stdin)
code = body.get("code")
message = body.get("message") or ""
data = body.get("data") or {}
if mode == "login":
    print(code)
    print(message)
    print(data.get("accessToken") or "")
    print(data.get("userId") or "")
elif mode == "search":
    goods = data.get("goods") or []
    first = goods[0] if goods else {}
    total = data.get("total")
    print(code)
    print(message)
    print(len(goods) if total is None else total)
    print(first.get("goodsId") or "")
    print(first.get("platform") or "")
    print(first.get("platformGoodsId") or "")
    print(first.get("title") or "")
elif mode == "detail":
    print(code)
    print(message)
    sales = data.get("salesCount")
    review = data.get("reviewCount")
    print("null" if sales is None else sales)
    print("null" if review is None else review)
elif mode == "link":
    print(code)
    print(message)
    print(data.get("clickUrl") or "")
    print(data.get("universalLink") or "")
    print(data.get("fallbackHttpUrl") or "")
    print(data.get("deeplink") or "")
else:
    print(code)
    print(message)
' "$1"
}

pretty_json() {
  python3 -c 'import json,sys
try:
    json.dump(json.load(sys.stdin), sys.stdout, ensure_ascii=False, indent=2)
    print()
except Exception:
    sys.exit(1)'
}

# 请求体和响应打到终端。令牌只放在头里，不打印。标准输出仍是原始响应，给后面的解析用。
post_json() {
  local url="$1"
  local payload="$2"
  shift 2
  local response
  echo "" >&2
  echo "--- 请求 POST ${url}" >&2
  printf '%s\n' "$payload" | pretty_json >&2 || printf '%s\n' "$payload" >&2
  response="$(curl -sS --max-time 120 -H "Content-Type: application/json" "$@" -d "$payload" "$url")"
  echo "--- 响应" >&2
  printf '%s\n' "$response" | pretty_json >&2 || printf '%s\n' "$response" >&2
  printf '%s' "$response"
}

login_body="$(post_json "$BASE/v1/auth/sms/login" "{\"phone\":\"$PHONE\",\"code\":\"888888\"}")"
login_file="$(mktemp)"
printf '%s' "$login_body" >"$login_file"
if ! scan_secrets "$login_file"; then
  rm -f "$login_file"
  exit 1
fi
rm -f "$login_file"
read_parsed < <(printf '%s' "$login_body" | parse_json login)
if [ "${lines[0]}" != "0" ] || [ -z "${lines[2]}" ] || [ -z "${lines[3]}" ]; then
  echo "登录失败：${lines[0]} ${lines[1]}"
  echo "local 需要 SMS_PROVIDER=mock，验证码固定 888888"
  exit 1
fi
TOKEN="${lines[2]}"
USER_ID="${lines[3]}"
if ! printf '%s' "$USER_ID" | grep -Eq '^[0-9A-HJKMNP-TV-Z]{26}$'; then
  echo "登录返回的用户号无法写库"
  exit 1
fi

ensure_auth() {
  local platform="$1"
  local existing id
  existing="$(sql "SELECT id FROM platform_authorization WHERE user_id = '$USER_ID' AND platform = '$platform'")"
  if [ -n "$existing" ]; then
    sql "UPDATE platform_authorization SET status = 'authorized', authorized_at = now(), revoked_at = NULL WHERE user_id = '$USER_ID' AND platform = '$platform'" >/dev/null
  else
    id="$(python3 -c 'import secrets; alphabet="0123456789ABCDEFGHJKMNPQRSTVWXYZ"; print("".join(secrets.choice(alphabet) for _ in range(26)))')"
    sql "INSERT INTO platform_authorization (id, user_id, platform, status, authorized_at) VALUES ('$id', '$USER_ID', '$platform', 'authorized', now())" >/dev/null
  fi
}

fail_vip() {
  local platform="$1"
  if [ "$platform" = "vip" ]; then
    echo "唯品会白名单外的 50010 不算通过"
  fi
}

run_platform() {
  local platform="$1"
  local body file goods_id platform_goods_id title
  local clicks_before clicks_after payload
  echo "== $platform =="

  payload="$(PLATFORM="$platform" KEYWORD="$KEYWORD" python3 -c 'import json,os; p=os.environ["PLATFORM"]; k=os.environ["KEYWORD"]; print(json.dumps({"platform":p,"keyword":k,"keywords":[k],"platforms":[p]}))')"
  body="$(post_json "$BASE/internal/v1/goods/search" "$payload" \
    -H "X-Internal-Token: $AGENT_INTERNAL_TOKEN" -H "X-User-Id: $USER_ID")"
  file="$(mktemp)"
  printf '%s' "$body" >"$file"
  if ! scan_secrets "$file"; then rm -f "$file"; return 1; fi
  rm -f "$file"
  read_parsed < <(printf '%s' "$body" | parse_json search)
  if [ "${lines[0]}" != "0" ]; then
    echo "$platform 搜品  失败  ${lines[0]} ${lines[1]}"
    fail_vip "$platform"
    return 1
  fi
  if [ -z "${lines[3]}" ] || [ "${lines[2]}" = "0" ]; then
    echo "$platform 搜品  失败  空列表，不算通过"
    return 1
  fi
  goods_id="${lines[3]}"
  platform_goods_id="${lines[5]}"
  title="${lines[6]}"
  echo "$platform 搜品  通过  ${lines[2]} 条，取「${title}」"

  payload="$(GOODS_ID="$goods_id" PLATFORM="$platform" PLATFORM_GOODS_ID="$platform_goods_id" python3 -c 'import json,os; print(json.dumps({"goodsId":os.environ["GOODS_ID"],"platform":os.environ["PLATFORM"],"platformGoodsId":os.environ["PLATFORM_GOODS_ID"]}))')"
  body="$(post_json "$BASE/v1/goods/detail" "$payload" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Client-Type: h5" -H "X-App-Channel: web" \
    -H "X-App-Version-Code: 20261030" -H "X-App-Version-Name: accept")"
  file="$(mktemp)"
  printf '%s' "$body" >"$file"
  if ! scan_secrets "$file"; then rm -f "$file"; return 1; fi
  rm -f "$file"
  read_parsed < <(printf '%s' "$body" | parse_json detail)
  if [ "${lines[0]}" != "0" ]; then
    echo "$platform 详情  失败  ${lines[0]} ${lines[1]}"
    fail_vip "$platform"
    return 1
  fi
  echo "$platform 详情  通过  salesCount=${lines[2]} reviewCount=${lines[3]}"

  ensure_auth "$platform"
  clicks_before="$(count_of "SELECT count(*) FROM click_event WHERE user_id = '$USER_ID' AND platform = '$platform' AND goods_id = '$goods_id'")"
  payload="$(GOODS_ID="$goods_id" PLATFORM="$platform" PLATFORM_GOODS_ID="$platform_goods_id" python3 -c 'import json,os; print(json.dumps({"goodsId":os.environ["GOODS_ID"],"platform":os.environ["PLATFORM"],"platformGoodsId":os.environ["PLATFORM_GOODS_ID"],"preferApp":False}))')"
  body="$(post_json "$BASE/v1/goods/convert-link" "$payload" \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Client-Type: h5" -H "X-App-Channel: web" \
    -H "X-App-Version-Code: 20261030" -H "X-App-Version-Name: accept")"
  file="$(mktemp)"
  printf '%s' "$body" >"$file"
  if ! scan_secrets "$file"; then rm -f "$file"; return 1; fi
  rm -f "$file"
  read_parsed < <(printf '%s' "$body" | parse_json link)
  if [ "${lines[0]}" != "0" ]; then
    echo "$platform 转链  失败  ${lines[0]} ${lines[1]}"
    fail_vip "$platform"
    return 1
  fi
  if [ -z "${lines[2]}" ] && [ -z "${lines[3]}" ] && [ -z "${lines[4]}" ] && [ -z "${lines[5]}" ]; then
    echo "$platform 转链  失败  没有可购买链接"
    return 1
  fi
  clicks_after="$(count_of "SELECT count(*) FROM click_event WHERE user_id = '$USER_ID' AND platform = '$platform' AND goods_id = '$goods_id'")"
  if [ "$clicks_after" -le "$clicks_before" ]; then
    echo "$platform 转链  失败  没有新的点击"
    return 1
  fi
  echo "$platform 转链  通过  点击 +1"
}

targets=("$@")
if [ "${#targets[@]}" -eq 0 ]; then
  targets=(taobao jd pdd vip)
fi
failed=0
for platform in "${targets[@]}"; do
  case "$platform" in
    taobao|jd|pdd|vip) ;;
    *)
      echo "平台只能是 taobao、jd、pdd、vip"
      exit 1
      ;;
  esac
  run_platform "$platform" || failed=1
done
if [ "$failed" -ne 0 ]; then
  echo "验收未通过"
  exit 1
fi
echo "验收通过"
