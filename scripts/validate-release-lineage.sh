#!/usr/bin/env bash
# 发布标签必须是 Java 主线的后代，并包含固定的七服务 Maven 拓扑。
# 本门禁不会移动或删除旧标签。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TAG="${1:-${RELEASE_TAG:-}}"
if [ -z "$TAG" ]; then
  echo "Usage: $0 <release-tag>" >&2
  exit 2
fi

tag_commit="$(git rev-parse --verify "${TAG}^{commit}")"
if ! git merge-base --is-ancestor "$tag_commit" HEAD; then
  echo "FAIL: ${TAG} is not an ancestor of the current Java mainline HEAD" >&2
  exit 1
fi

if ! git show "${tag_commit}:pom.xml" >/dev/null 2>&1; then
  echo "FAIL: ${TAG} does not contain the Java Maven root" >&2
  exit 1
fi
if ! git show "${tag_commit}:pom.xml" | rg -q '<module>big-market-domain</module>'; then
  echo "FAIL: ${TAG} is not a Java mainline release (big-market-domain missing)" >&2
  exit 1
fi
for module in big-market-gateway big-market-auth-service big-market-admin-service \
  big-market-market-service big-market-chatbot-service big-market-message-job-service \
  big-market-account-service; do
  if ! git ls-tree -r --name-only "$tag_commit" | rg -q "^${module}/src/main/"; then
    echo "FAIL: ${TAG} is missing Java service module ${module}" >&2
    exit 1
  fi
done

echo "Release lineage OK: ${TAG} -> ${tag_commit} is on the Java mainline."
