#!/bin/sh
# 发布到私服。地址只从当前激活的 settings profile 读取，不写在脚本里。
set -eu
cd "$(dirname "$0")"
SETTINGS=${MAVEN_SETTINGS:-$HOME/.m2/settings.xml}
if [ ! -f "$SETTINGS" ]; then
  exit 1
fi

URL=$(python3 - "$SETTINGS" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
ns = root.tag.split("}")[0] + "}" if root.tag.startswith("{") else ""

def child(parent, name):
    return parent.find(f"{ns}{name}")

def text(parent, name):
    node = child(parent, name)
    return "" if node is None or node.text is None else node.text.strip()

active = set()
active_node = child(root, "activeProfiles")
if active_node is not None:
    for profile in active_node.findall(f"{ns}profile"):
        if profile.text and profile.text.strip():
            active.add(profile.text.strip())

profiles = []
profiles_node = child(root, "profiles")
if profiles_node is not None:
    profiles = list(profiles_node.findall(f"{ns}profile"))

if not active:
    for profile in profiles:
        flag = text(profile, "activeByDefault") or text(child(profile, "activation") or profile, "activeByDefault")
        activation = child(profile, "activation")
        if activation is not None and text(activation, "activeByDefault").lower() == "true":
            active.add(text(profile, "id"))

found = ""
for profile in profiles:
    if text(profile, "id") not in active:
        continue
    props = child(profile, "properties")
    if props is None:
        continue
    value = text(props, "omino.sdk.repo.url")
    if value:
        found = value

servers = child(root, "servers")
ok = False
if servers is not None:
    for server in servers.findall(f"{ns}server"):
        if text(server, "id") == "omino-shop-sdk":
            ok = True
if not ok or not found:
    sys.exit(1)
print(found)
PY
)

one() {
  extra=${5:-}
  # shellcheck disable=SC2086
  mvn -q deploy:deploy-file \
    -Dfile="$1" \
    -DgroupId="$2" \
    -DartifactId="$3" \
    -Dversion="$4" \
    -Dpackaging=jar \
    -DgeneratePom=true \
    -DrepositoryId=omino-shop-sdk \
    -Durl="$URL" \
    $extra
}

tb=$(ls taobao-sdk-java-*-20250925.jar | grep -v source)
src=$(ls taobao-sdk-java-*-source.jar)
one "$tb" com.taobao taobao-sdk-java 20250925
one "$src" com.taobao taobao-sdk-java 20250925 "-Dclassifier=sources"
one jd-api-sdk-java-20250925.jar com.jd open-api-sdk 20250925
one pop-sdk-1.10.16.jar com.pdd pop-sdk 1.10.16
one vop-sdk-20250214094310.jar com.vip vop-sdk 20250214094310
one osp-sdk-20250214094310.jar com.vip osp-sdk 20250214094310
