#!/usr/bin/env bash
# ==============================================================================
# LumeraX: Master E2E Playback & Telemetry Pipeline Entrypoint
# ==============================================================================
# Orchestrates environment setup, headless emulator lifecycle, and E2E execution.
# Centralized in secondary storage (/SteamGames) with zero leakage to root (/).
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

log_info() {
    echo "[PIPELINE] $(date '+%Y-%m-%d %H:%M:%S') - $*"
}

log_error() {
    echo "[PIPELINE ERROR] $(date '+%Y-%m-%d %H:%M:%S') - $*" >&2
}

# ------------------------------------------------------------------------------
# 1. Environment & Credential Setup
# ------------------------------------------------------------------------------
log_info "Step 1: Initializing Android isolated environment..."
if [ ! -f "${PROJECT_ROOT}/.env.test" ]; then
    log_info "No .env.test found. Creating from .env.test.example template..."
    cp "${PROJECT_ROOT}/.env.test.example" "${PROJECT_ROOT}/.env.test"
fi

# Source setup environment variables (without re-running package downloads unless explicitly called)
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/setup-android-env.sh"

RESULTS_DIR="${PROJECT_ROOT}/${TEST_RESULTS_DIR:-test-results}"
mkdir -p "$RESULTS_DIR"

# ------------------------------------------------------------------------------
# 2. Emulator Lifecycle Management (Headless Daemon)
# ------------------------------------------------------------------------------
cleanup_emulator() {
    log_info "Teardown trap triggered. Terminating headless emulator daemon..."
    "${SCRIPT_DIR}/emulator-control.sh" stop || true
}

# Register exit trap so emulator is never left running as an orphan process
trap cleanup_emulator EXIT

log_info "Step 2: Starting Android headless TV emulator..."
"${SCRIPT_DIR}/emulator-control.sh" start
"${SCRIPT_DIR}/emulator-control.sh" wait-ready

# ------------------------------------------------------------------------------
# 3. Application Verification / Optional Assembly
# ------------------------------------------------------------------------------
log_info "Step 3: Checking LumeraX target package installation..."
adb_bin="${ANDROID_HOME}/platform-tools/adb"
if ! command -v "$adb_bin" >/dev/null 2>&1; then
    adb_bin="adb"
fi

APP_PKG="${APP_PACKAGE:-com.lumera.app}"
if ! "$adb_bin" shell pm list packages | grep -q "$APP_PKG"; then
    log_info "Package ${APP_PKG} not installed. Checking for APK in app/build/outputs/apk/debug/..."
    local_apk="$(find "${PROJECT_ROOT}/app/build/outputs/apk" -name "*.apk" 2>/dev/null | head -n 1 || true)"
    if [ -n "$local_apk" ] && [ -f "$local_apk" ]; then
        log_info "Installing found APK: ${local_apk}"
        "$adb_bin" install -r -d "$local_apk"
    else
        log_info "No pre-built APK found. Building debug APK via isolated Gradle..."
        "${PROJECT_ROOT}/gradlew" assembleDebug -g "${GRADLE_USER_HOME}"
        local_built_apk="$(find "${PROJECT_ROOT}/app/build/outputs/apk" -name "*.apk" | head -n 1)"
        log_info "Installing built APK: ${local_built_apk}"
        "$adb_bin" install -r -d "$local_built_apk"
    fi
else
    log_info "Package ${APP_PKG} is already installed on the emulator."
fi

# ------------------------------------------------------------------------------
# 4. Execute E2E Playback & Telemetry Runner
# ------------------------------------------------------------------------------
log_info "Step 4: Executing E2E Playback & Telemetry Python Runner..."
export PYTHONPATH="${PROJECT_ROOT}:${PYTHONPATH:-}"

python3 "${SCRIPT_DIR}/e2e/playback_telemetry_runner.py"

EXIT_STATUS=$?

# ------------------------------------------------------------------------------
# 5. Pipeline Summary
# ------------------------------------------------------------------------------
log_info "Step 5: Pipeline execution finished with status code ${EXIT_STATUS}."
log_info "Generated Artifacts:"
log_info "  - JSON Report:  ${RESULTS_DIR}/playback-report.json"
log_info "  - HTML Dashboard: ${RESULTS_DIR}/playback-report.html"

exit "$EXIT_STATUS"
