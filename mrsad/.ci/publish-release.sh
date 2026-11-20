#!/usr/bin/env bash
#
# Publishes the signed APK as a GitHub release.
#
# Two things about a release are easy to get wrong, and both of them
# cost a run:
#
# * A published release is *immutable*. Its files can no longer be
#   replaced, and asking for it is answered with `Cannot delete asset
#   from an immutable release`. The release is therefore created as a
#   draft, the files are uploaded into the draft, and the draft is
#   published only after every file is there.
# * The version number is the name of the release. A new build needs a
#   new version number; an old release cannot be made to hold a new
#   build.
#
# The script says what it does, prints what happened, and returns a
# status that says whether the release is really there.
#
# Usage:
#   publish-release.sh <tag> <apk> <notes-file> <commit> [app-dir]
#
# Environment:
#   GH_TOKEN            the token used for the API
#   GITHUB_REPOSITORY   "owner/name"
#   GITHUB_SERVER_URL   the base URL of the server (for the notes)
#   GITHUB_RUN_ID       the run, quoted in the notes

set -uo pipefail

TAG="${1:?usage: publish-release.sh <tag> <apk> <notes-file> <commit> [app-dir]}"
APK="${2:?}"
NOTES="${3:?}"
COMMIT="${4:?}"
APP_DIR="${5:-app}"

REPO="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"
SERVER="${GITHUB_SERVER_URL:-https://github.com}"
RUN_ID="${GITHUB_RUN_ID:-unknown}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

log() { echo "[release] $*"; }
fail() { echo "[release] error: $*" >&2; }

# ---- The files --------------------------------------------------------------
cp "$APK" "$WORK/mrsad.apk"
CHECKSUM="$(sha256sum "$WORK/mrsad.apk" | cut -d' ' -f1)"
( cd "$WORK" && sha256sum mrsad.apk > SHA256SUMS.txt )
log "the APK is $(wc -c < "$WORK/mrsad.apk") bytes, sha256 $CHECKSUM"

# A digest of the source the APK was built from: the hash of every
# tracked file of the application, with the path next to it, sorted by
# path. Two builds of the same source produce the same digest, whatever
# the commit says, so a release can be compared with a later build of
# the same code.
#
# Only the tracked files count: the output of the build (build/ and
# .gradle/) is not part of the source, and it changes with every run.
source_digest() {
  local dir="$1"
  if [ ! -d "$dir" ]; then
    echo "unknown"
    return
  fi
  (
    cd "$dir" || exit 1
    git ls-files . 2>/dev/null | sort | while read -r file; do
      if [ -f "$file" ]; then
        printf '%s  %s\n' "$(sha256sum "$file" | cut -d' ' -f1)" "$file"
      fi
    done | sha256sum | cut -d' ' -f1
  )
}

# The same digest, but for the source of a given commit.
digest_at_ref() {
  local ref="$1"
  local root path file
  root="$(git -C "$APP_DIR" rev-parse --show-toplevel 2>/dev/null)" || return 1
  path="$(cd "$APP_DIR" && pwd)"
  if [ "$path" = "$root" ]; then
    path="."
  else
    path="${path#"$root"/}"
  fi
  git -C "$root" ls-tree -r --name-only "$ref" -- "$path" 2>/dev/null | sort | while read -r file; do
    if [ "$path" = "." ]; then
      printf '%s  %s\n' "$(git -C "$root" show "$ref:$file" | sha256sum | cut -d' ' -f1)" "$file"
    else
      printf '%s  %s\n' "$(git -C "$root" show "$ref:$file" | sha256sum | cut -d' ' -f1)" "${file#"$path"/}"
    fi
  done | sha256sum | cut -d' ' -f1
}

DIGEST="$(source_digest "$APP_DIR")"
log "the digest of the source is $DIGEST"

cat > "$WORK/BUILD.txt" <<EOF
version: ${TAG#v}
tag: $TAG
commit: $COMMIT
source digest: $DIGEST
apk: mrsad.apk
apk sha256: $CHECKSUM
run: ${SERVER}/${REPO}/actions/runs/${RUN_ID}
EOF

ASSETS="mrsad.apk SHA256SUMS.txt BUILD.txt"

# ---- The API ----------------------------------------------------------------
# A release is found by looking at the list, not by asking for its tag:
# a draft has no tag yet, and asking for one of those answers with a
# 404 that looks like a missing release.
release_id() {
  gh api --paginate "repos/$REPO/releases" \
    --jq ".[] | select(.tag_name == \"$TAG\") | .id" 2>/dev/null | head -1
}

release_exists() {
  [ -n "$(release_id)" ]
}

release_is_immutable() {
  local id
  id="$(release_id)"
  [ -n "$id" ] || return 1
  [ "$(gh api "repos/$REPO/releases/$id" --jq '.immutable' 2>/dev/null)" = "true" ]
}

# The release is published by hand: the tag of a draft does not exist
# until it is published, and the tag has to point at the commit that was
# built.
publish_release() {
  local id="$1"
  gh api -X PATCH "repos/$REPO/releases/$id" -F draft=false >/dev/null 2>&1
}

# The notes are written through the API as well: a release that is still
# a draft cannot be found by its tag, and the notes are part of the
# release that is being filled.
update_notes() {
  local id="$1"
  gh api -X PATCH "repos/$REPO/releases/$id" -F "body=@$NOTES" >/dev/null 2>&1
}

# The two steps of the documented upload: create the asset, then PUT the
# file to the URL that comes back. HTTP/1.1 on purpose: the upload host
# drops HTTP/2 streams often enough to end the transfer with an EOF.
upload_asset() {
  local file="$1" name="$2" id="$3"
  local upload_url attempt

  local old
  old="$(gh api "repos/$REPO/releases/$id/assets" \
          --jq ".[] | select(.name == \"$name\") | .id" 2>/dev/null | head -1)"
  if [ -n "$old" ]; then
    gh api -X DELETE "repos/$REPO/releases/assets/$old" >/dev/null 2>&1 || true
  fi

  for attempt in 1 2 3 4 5; do
    upload_url="$(gh api -X POST "repos/$REPO/releases/$id/assets?name=$name" \
                    --jq .upload_url 2>/dev/null)" || upload_url=""
    if [ -n "$upload_url" ]; then
      upload_url="${upload_url%%\{*}"
      if curl --http1.1 --silent --show-error --fail \
           --retry 5 --retry-all-errors --retry-delay 5 \
           -H "Authorization: Bearer ${GH_TOKEN:-}" \
           -H "Content-Type: application/octet-stream" \
           --data-binary "@$file" \
           "${upload_url}?name=$name" >/dev/null; then
        log "uploaded $name"
        return 0
      fi
      log "the upload of $name failed (attempt $attempt), trying again"
    else
      log "the asset $name could not be created (attempt $attempt), trying again"
    fi
    sleep $((attempt * 5))
  done
  return 1
}

# ---- The three cases --------------------------------------------------------
if release_exists; then
  if release_is_immutable; then
    # The release is published and cannot change. What is asked for is
    # therefore checked, not written.
    log "$TAG is an immutable release; its files are checked instead"
    ID="$(release_id)"
    FAILED=0
    for name in $ASSETS; do
      if ! gh api "repos/$REPO/releases/$ID/assets" \
             --jq ".[] | select(.name == \"$name\") | .name" 2>/dev/null | grep -qx "$name"; then
        fail "$TAG is immutable and has no $name"
        FAILED=1
      fi
    done
    [ "$FAILED" -eq 0 ] || exit 1

    # The published build is compared with this one. The source of the
    # published tag is the source the APK was made of; if it is the same
    # source as this build, the release is already the release of this
    # code, whatever the bytes of the build say.
    git fetch --quiet --depth=1 origin "refs/tags/$TAG:refs/tags/$TAG" >/dev/null 2>&1 || true
    PUBLISHED_DIGEST="$(digest_at_ref "$TAG" 2>/dev/null)"
    if [ -n "$PUBLISHED_DIGEST" ] && [ "$PUBLISHED_DIGEST" != "$DIGEST" ]; then
      fail "$TAG carries another build (its source digest is $PUBLISHED_DIGEST, this one is $DIGEST)"
      fail "raise the version number to publish this build"
      exit 1
    fi
    if [ -n "$PUBLISHED_DIGEST" ]; then
      log "$TAG carries the same source as this build"
    fi

    # The build record of the release, when it has one, says the same
    # thing in a form a person can read.
    BUILD_ID="$(gh api "repos/$REPO/releases/$ID/assets" \
                  --jq ".[] | select(.name == \"BUILD.txt\") | .id" 2>/dev/null | head -1)"
    if [ -n "$BUILD_ID" ]; then
      gh api -H "Accept: application/octet-stream" \
        "repos/$REPO/releases/assets/$BUILD_ID" > "$WORK/published-build.txt" 2>/dev/null || true
      PUBLISHED_DIGEST="$(sed -n 's/^source digest: *//p' "$WORK/published-build.txt" 2>/dev/null | head -1)"
      if [ -n "$PUBLISHED_DIGEST" ] && [ "$PUBLISHED_DIGEST" != "$DIGEST" ]; then
        fail "$TAG carries another build (digest $PUBLISHED_DIGEST, this one is $DIGEST)"
        fail "raise the version number to publish this build"
        exit 1
      fi
      log "$TAG carries the same source as this build"
    else
      SUMS_ID="$(gh api "repos/$REPO/releases/$ID/assets" \
                   --jq ".[] | select(.name == \"SHA256SUMS.txt\") | .id" 2>/dev/null | head -1)"
      if [ -n "$SUMS_ID" ]; then
        gh api -H "Accept: application/octet-stream" \
          "repos/$REPO/releases/assets/$SUMS_ID" > "$WORK/published-sums.txt" 2>/dev/null || true
        if [ -s "$WORK/published-sums.txt" ] \
           && ! grep -q "$CHECKSUM" "$WORK/published-sums.txt"; then
          fail "$TAG carries another build (its checksum is not $CHECKSUM)"
          fail "raise the version number to publish this build"
          exit 1
        fi
        log "$TAG carries the same artifact as this build"
      fi
    fi
    exit 0
  fi

  log "$TAG exists and can still be changed"
  ID="$(release_id)"
  FAILED=0
  for name in $ASSETS; do
    if ! upload_asset "$WORK/$name" "$name" "$ID"; then
      fail "the upload of $name failed five times"
      FAILED=1
    fi
  done
  if ! update_notes "$ID"; then
    fail "the notes of $TAG could not be written"
    FAILED=1
  fi
  [ "$FAILED" -eq 0 ] || exit 1
  log "released $TAG"
  exit 0
fi

# The release does not exist: it is created as a draft, filled, and
# published afterwards.
log "creating the draft $TAG"
if ! gh release create "$TAG" --draft --target "$COMMIT" \
     --title "MRSAD $TAG" --notes-file "$NOTES"; then
  fail "the release $TAG could not be created"
  exit 1
fi

ID="$(release_id)"
if [ -z "$ID" ]; then
  fail "the draft $TAG cannot be found"
  exit 1
fi

FAILED=0
for name in $ASSETS; do
  if ! upload_asset "$WORK/$name" "$name" "$ID"; then
    fail "the upload of $name failed five times"
    FAILED=1
  fi
done

if [ "$FAILED" -ne 0 ]; then
  fail "$TAG stays a draft, because its files are missing"
  exit 1
fi

if ! publish_release "$ID"; then
  fail "the release $TAG could not be published"
  exit 1
fi

log "released $TAG"
exit 0
