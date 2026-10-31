#!/bin/sh
# 把厂商包装进本机 Maven 仓库。坐标以 AGENTS.md 为准。
# 伴随 pom 的 groupId 与锁定坐标不一致，所以这里传坐标参数，不用 -DpomFile。
set -eu
cd "$(dirname "$0")"

one() {
  extra=${5:-}
  # shellcheck disable=SC2086
  mvn -q install:install-file \
    -Dfile="$1" \
    -DgroupId="$2" \
    -DartifactId="$3" \
    -Dversion="$4" \
    -Dpackaging=jar \
    -DgeneratePom=true \
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
