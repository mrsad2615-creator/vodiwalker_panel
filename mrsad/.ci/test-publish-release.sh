#!/usr/bin/env bash
#
# The test of `publish-release.sh`.
#
# The release step of the pipeline is the one step that cannot be tried
# out on a phone, and the two ways it can go wrong (an immutable release
# that cannot take a file, a release that is published before its files
# are there) are both quiet failures in the log of a long run. So the
# publish script is written to be testable: `gh` and `curl` are replaced
# by stubs that remember what they were asked to do, and the four
# situations that can happen are run through.
#
# Usage: test-publish-release.sh

set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/publish-release.sh"

FAILED=0

say() { echo "  $1"; }
ok() { echo "  ok: $1"; }
bad() { echo "  FAILED: $1"; FAILED=$((FAILED + 1)); }

# ---- The stubs ---------------------------------------------------------------
make_stub() {
  local bin="$1"
  mkdir -p "$bin"

  cat > "$bin/gh" <<'STUB'
#!/usr/bin/env bash
# A stub of the gh command: it remembers what it was asked to do and
# answers from the environment.
echo "gh $*" >> "$STUB_LOG"

method=GET
path=""
jq=""
prev=""
for arg in "$@"; do
  case "$prev" in
    -X) method="$arg" ;;
    --jq) jq="$arg" ;;
  esac
  case "$arg" in
    repos/*) if [ -z "$path" ]; then path="$arg"; fi ;;
  esac
  prev="$arg"
done

if [ "${1:-}" = "release" ]; then
  case "${2:-}" in
    view)   [ "${STUB_EXISTS:-0}" = "1" ] || exit 1 ;;
    create) echo "created $*" >> "$STUB_LOG"; touch "$STUB_LOG.created"; exit 0 ;;
    edit)   echo "edited $*" >> "$STUB_LOG"; exit 0 ;;
    *)      exit 1 ;;
  esac
  exit 0
fi

if [ "${1:-}" = "api" ]; then
  case "$method" in
    DELETE) exit 0 ;;
    PATCH)  echo "patched $*" >> "$STUB_LOG"; exit 0 ;;
  esac

  # The name the query asks about, and the number that belongs to it.
  selected=""
  if [[ "$jq" == *'select(.name == "'* ]]; then
    selected="${jq#*select(.name == \"}"
    selected="${selected%%\"*}"
  fi
  case "$selected" in
    BUILD.txt)      number=77 ;;
    SHA256SUMS.txt) number=78 ;;
    mrsad.apk)      number=79 ;;
    *)              number=0 ;;
  esac

  # The list of the releases: a draft is in it as well, so this is the
  # only way to find one.
  if [ "$path" = "repos/$GITHUB_REPOSITORY/releases" ]; then
    if [ "${STUB_EXISTS:-0}" = "1" ] || [ -f "$STUB_LOG.created" ]; then
      echo "${STUB_RELEASE_ID:-1}"
    fi
    exit 0
  fi

  case "$path" in
    *"/releases/assets/"*)
      # The download of a file of a release.
      id="${path##*/}"
      if [ "$id" = "77" ]; then
        printf '%s\n' "$STUB_PUBLISHED_BUILD"
      elif [ "$id" = "78" ]; then
        printf '%s\n' "$STUB_PUBLISHED_SUMS"
      fi
      exit 0 ;;
    *"/releases/"*"/assets")
      # The files of one release.
      for asset in ${STUB_ASSETS:-}; do
        if [ "$asset" = "$selected" ]; then
          case "$jq" in
            *.name) echo "$asset" ;;
            *.id)
              case "$asset" in
                BUILD.txt)      echo 77 ;;
                SHA256SUMS.txt) echo 78 ;;
                mrsad.apk)      echo 79 ;;
              esac
              ;;
          esac
        fi
      done
      exit 0 ;;
    *"/releases/"*)
      # One release.
      case "$jq" in
        *immutable*) echo "${STUB_IMMUTABLE:-false}" ;;
        *upload_url*) echo "https://uploads.github.com/stub-release-assets{?name,label}" ;;
        .id)         echo "${STUB_RELEASE_ID:-1}" ;;
      esac
      exit 0 ;;
  esac
fi

exit 1
STUB

  cat > "$bin/curl" <<'STUB'
#!/usr/bin/env bash
# A stub of curl: it remembers the call and writes down the file that
# was sent, so the test can look at what was uploaded. When the upload
# host is told to be down, it fails, so that the failure path can be
# exercised.
echo "curl $*" >> "$STUB_LOG"
if [ "${STUB_UPLOAD_DOWN:-0}" = "1" ]; then
  exit 22
fi
for arg in "$@"; do
  case "$arg" in
    @*)
      file="${arg#@}"
      echo "sent $(sha256sum "$file" 2>/dev/null | cut -d' ' -f1) from $(basename "$file")" >> "$STUB_LOG"
      echo "--- the content of $(basename "$file") ---" >> "$STUB_LOG"
      cat "$file" >> "$STUB_LOG" 2>/dev/null
      ;;
  esac
done
exit 0
STUB

  # A stub of sleep: the test does not have to wait for the real
  # backoff of a failing upload to know what happens.
  cat > "$bin/sleep" <<'STUB'
#!/usr/bin/env bash
exit 0
STUB

  chmod +x "$bin/gh" "$bin/curl" "$bin/sleep"
}

# ---- The fixture -------------------------------------------------------------
WORK="$(mktemp -d)"
if [ "${MRSAD_KEEP_TEST_DIR:-0}" = "1" ]; then
  echo "the work directory is $WORK"
else
  trap 'rm -rf "$WORK"' EXIT
fi
BIN="$WORK/bin"
make_stub "$BIN"

FIXTURE="$WORK/app"
mkdir -p "$FIXTURE/src/main/kotlin" "$FIXTURE/build/generated"
echo "package dev.mrsad.app" > "$FIXTURE/src/main/kotlin/A.kt"
echo "// the build" > "$FIXTURE/build.gradle.kts"
echo "// generated, and not part of the source" > "$FIXTURE/build/generated/R.kt"
# The output of the build is not part of the source, so it is kept out of
# the repository the same way the real project keeps it out.
# The fixture is a repository of its own, with two commits and two tags:
# the old one holds an older source, the new one holds the source that is
# on the disk. A release that points at the old tag carries another build.
(
  cd "$FIXTURE" || exit 1
  git init --quiet
  git config user.email "test@example.com"
  git config user.name "the test"
  # The rule that keeps the generated files out lives in the repository
  # itself and not in a file of the source, so the tracked files are
  # exactly the ones the test counts.
  printf 'build/\n' > .git/info/exclude
  git add -A
  git commit --quiet -m "the first source"
  git tag v9.8.9
  echo "package dev.mrsad.app // the second source" > src/main/kotlin/A.kt
  git add -A
  git commit --quiet -m "the second source"
  git tag v9.9.9
)

# The digest the script is expected to produce, worked out on its own:
# the hash of every source file, with the path next to it, sorted by
# path. The generated file under build/ is not part of it.
EXPECTED_DIGEST="$(
  cd "$FIXTURE" || exit 1
  {
    printf '%s  %s\n' "$(sha256sum build.gradle.kts | cut -d' ' -f1)" "build.gradle.kts"
    find src -type f | sort | while read -r file; do
      printf '%s  %s\n' "$(sha256sum "$file" | cut -d' ' -f1)" "$file"
    done
  } | sha256sum | cut -d' ' -f1
)"

echo "the digest of the fixture is $EXPECTED_DIGEST"

APK="$WORK/app.apk"
echo "an apk" > "$APK"
NOTES="$WORK/notes.md"
echo "the notes" > "$NOTES"

LOG=""
STATUS=0

run_case() {
  local name="$1" tag="$2"; shift 2
  LOG="$WORK/$name.log"
  : > "$LOG"
  (
    cd "$WORK" || exit 1
    export PATH="$BIN:$PATH"
    export STUB_LOG="$LOG"
    export GH_TOKEN="token"
    export GITHUB_REPOSITORY="owner/name"
    export GITHUB_SERVER_URL="https://github.com"
    export GITHUB_RUN_ID="1"
    local pair
    for pair in "$@"; do export "$pair"; done
    bash "$SCRIPT" "$tag" "$APK" "$NOTES" "0123456789abcdef" "app"
  ) >> "$LOG" 2>&1
  STATUS=$?
  return 0
}

echo "== a release that does not exist yet =="
run_case new v9.9.9 STUB_EXISTS=0
[ "$STATUS" -eq 0 ] && ok "the publish succeeded" || bad "the publish failed with $STATUS"
grep -q "release create v9.9.9 --draft" "$LOG" && ok "the release was created as a draft" \
  || bad "the release was not created as a draft"
grep -q "patched" "$LOG" && ok "the draft was published afterwards" \
  || bad "the draft was never published"
grep -q "sent .* from mrsad.apk" "$LOG" && ok "the APK was uploaded" || bad "the APK was not uploaded"
grep -q "sent .* from SHA256SUMS.txt" "$LOG" && ok "the checksum was uploaded" || bad "the checksum was not uploaded"
grep -q "sent .* from BUILD.txt" "$LOG" && ok "the build record was uploaded" || bad "the build record was not uploaded"
grep -q "source digest: $EXPECTED_DIGEST" "$LOG" && ok "the digest of the source is right" \
  || bad "the digest of the source is wrong"

echo "== a release that exists and can still be changed =="
run_case mutable v9.9.9 STUB_EXISTS=1 STUB_IMMUTABLE=false
[ "$STATUS" -eq 0 ] && ok "the publish succeeded" || bad "the publish failed with $STATUS"
grep -q "sent .* from mrsad.apk" "$LOG" && ok "the APK was uploaded" || bad "the APK was not uploaded"
grep -q "patched" "$LOG" && ok "the notes were written" || bad "the notes were not written"

echo "== an immutable release that carries the same build =="
run_case immutable_same v9.9.9 \
  STUB_EXISTS=1 STUB_IMMUTABLE=true \
  STUB_ASSETS="mrsad.apk SHA256SUMS.txt BUILD.txt" \
  STUB_PUBLISHED_BUILD="version: 9.9.9
source digest: $EXPECTED_DIGEST
apk sha256: something"
[ "$STATUS" -eq 0 ] && ok "the publish succeeded" || bad "the publish failed with $STATUS"
grep -q "sent .* from" "$LOG" && bad "something was uploaded to an immutable release" \
  || ok "nothing was uploaded to an immutable release"

echo "== an immutable release that carries another build =="
run_case immutable_other v9.9.9 \
  STUB_EXISTS=1 STUB_IMMUTABLE=true \
  STUB_ASSETS="mrsad.apk SHA256SUMS.txt BUILD.txt" \
  STUB_PUBLISHED_BUILD="version: 9.9.9
source digest: 0000000000000000000000000000000000000000000000000000000000000000
apk sha256: something"
[ "$STATUS" -ne 0 ] && ok "the publish failed, as it should" || bad "the publish succeeded over another build"
grep -q "raise the version number" "$LOG" && ok "the reason was said" || bad "the reason was not said"

echo "== an immutable release that points at an older source =="
run_case immutable_older v9.8.9 \
  STUB_EXISTS=1 STUB_IMMUTABLE=true \
  STUB_ASSETS="mrsad.apk SHA256SUMS.txt BUILD.txt" \
  STUB_PUBLISHED_BUILD="version: 9.8.9
source digest: $EXPECTED_DIGEST
apk sha256: something"
[ "$STATUS" -ne 0 ] && ok "the publish failed, as it should" || bad "the publish succeeded over an older source"
grep -q "raise the version number" "$LOG" && ok "the reason was said" || bad "the reason was not said"

echo "== a new release, with the upload host down =="
run_case upload_down v9.9.9 STUB_EXISTS=0 STUB_UPLOAD_DOWN=1
[ "$STATUS" -ne 0 ] && ok "the publish failed, as it should" || bad "the publish succeeded with the upload host down"
grep -q "stays a draft" "$LOG" && ok "the release was left as a draft, ready to be tried again" \
  || bad "the release was published without its files"
grep -q "patched" "$LOG" && bad "the draft was published without its files" \
  || ok "the draft was not published"

echo "== an immutable release without the files =="
run_case immutable_missing v9.9.9 \
  STUB_EXISTS=1 STUB_IMMUTABLE=true \
  STUB_ASSETS="mrsad.apk"
[ "$STATUS" -ne 0 ] && ok "the publish failed, as it should" || bad "the publish succeeded without the files"

if [ "$FAILED" -eq 0 ]; then
  echo "the test of the release publish passed"
  exit 0
fi
echo "the test of the release publish failed in $FAILED places"
exit 1
