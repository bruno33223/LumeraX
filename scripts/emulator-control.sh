#!/usr/bin/env bash
# ==============================================================================
# LumeraX: Android Headless Emulator Daemon & Controller
# ==============================================================================
# Manages lifecycle (start, wait-ready, stop, status) of the headless TV AVD.
# Runs with swiftshader_indirect / KVM acceleration and isolated storage paths.
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ -f "${PROJECT_ROOT}/.env.test" ]; then
    # shellcheck disable=SC1091
    set -a
    source "${PROJECT_ROOT}/.env.test"
    set +a
fi

# Configuration Defaults
TARGET_BASE="${STEAM_ANDROID_BASE:-${STEAM_PARTITION:-/media/bruno/SteamGames/android}}"
AVD_NAME="${AVD_NAME:-lumerax_e2e_tv}"
EMULATOR_GPU_MODE="${EMULATOR_GPU_MODE:-swiftshader_indirect}"
BOOT_TIMEOUT_SECONDS="${BOOT_TIMEOUT_SECONDS:-120}"
PID_FILE="${TARGET_BASE}/emulator.pid"
LOG_FILE="${TARGET_BASE}/emulator.log"

export ANDROID_HOME="${TARGET_BASE}/sdk"
export ANDROID_USER_HOME="${TARGET_BASE}/.android"
export GRADLE_USER_HOME="${TARGET_BASE}/.gradle"
export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

log_info() {
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - $*"
}

log_warn() {
    echo "[WARN] $(date '+%Y-%m-%d %H:%M:%S') - $*" >&2
}

log_error() {
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') - $*" >&2
}

find_emulator_bin() {
    if [ -x "${ANDROID_HOME}/emulator/emulator" ]; then
        echo "${ANDROID_HOME}/emulator/emulator"
    elif command -v emulator >/dev/null 2>&1; then
        command -v emulator
    else
        log_error "Android emulator binary not found in ${ANDROID_HOME}/emulator or system PATH."
        exit 1
    fi
}

find_adb_bin() {
    if [ -x "${ANDROID_HOME}/platform-tools/adb" ]; then
        echo "${ANDROID_HOME}/platform-tools/adb"
    elif command -v adb >/dev/null 2>&1; then
        command -v adb
    else
        log_error "Android adb binary not found in ${ANDROID_HOME}/platform-tools or system PATH."
        exit 1
    fi
}

is_pid_running() {
    if [ -f "$PID_FILE" ]; then
        local pid
        pid="$(cat "$PID_FILE" 2>/dev/null || echo "")"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            return 0
        fi
    fi
    return 1
}

# ------------------------------------------------------------------------------
# start: Launches the headless emulator daemon in background
# ------------------------------------------------------------------------------
cmd_start() {
    log_info "Initiating emulator startup for AVD: ${AVD_NAME}"

    if is_pid_running; then
        local existing_pid
        existing_pid="$(cat "$PID_FILE")"
        log_info "Emulator already running with PID ${existing_pid}."
        return 0
    fi

    mkdir -p "${TARGET_BASE}"
    local emulator_bin
    emulator_bin="$(find_emulator_bin)"

    log_info "Launching headless emulator: ${emulator_bin} -avd ${AVD_NAME} -gpu ${EMULATOR_GPU_MODE}"
    nohup "$emulator_bin" \
        -avd "$AVD_NAME" \
        -no-window \
        -no-audio \
        -no-boot-anim \
        -no-snapshot-save \
        -gpu "$EMULATOR_GPU_MODE" \
        -wipe-data \
        > "$LOG_FILE" 2>&1 &

    local new_pid=$!
    echo "$new_pid" > "$PID_FILE"
    log_info "Emulator daemon process spawned with PID ${new_pid}. Logs: ${LOG_FILE}"
}

# ------------------------------------------------------------------------------
# wait_ready: Polls adb and sys.boot_completed until emulator is fully booted
# ------------------------------------------------------------------------------
cmd_wait_ready() {
    local adb_bin
    adb_bin="$(find_adb_bin)"
    local timeout="${BOOT_TIMEOUT_SECONDS}"
    local elapsed=0
    local interval=2

    log_info "Waiting for emulator to complete boot (timeout: ${timeout}s)..."

    "$adb_bin" wait-for-device || true

    while [ "$elapsed" -lt "$timeout" ]; do
        local boot_completed
        boot_completed="$("$adb_bin" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || echo "")"

        if [ "$boot_completed" = "1" ]; then
            log_info "Emulator successfully booted in ${elapsed}s!"
            # Additional stability settling
            sleep 3
            "$adb_bin" shell input keyevent 82 >/dev/null 2>&1 || true
            return 0
        fi

        sleep "$interval"
        elapsed=$((elapsed + interval))
        if [ $((elapsed % 10)) -eq 0 ]; then
            log_info "Still waiting for sys.boot_completed=1... (${elapsed}s/${timeout}s)"
        fi
    done

    log_error "Timed out waiting for emulator boot completion after ${timeout}s."
    return 1
}

# ------------------------------------------------------------------------------
# stop: Gracefully shuts down emulator via adb, falling back to SIGTERM/SIGKILL
# ------------------------------------------------------------------------------
cmd_stop() {
    log_info "Stopping emulator daemon..."
    local adb_bin
    adb_bin="$(find_adb_bin)"

    # Try adb emu kill first
    "$adb_bin" emu kill >/dev/null 2>&1 || true

    if [ -f "$PID_FILE" ]; then
        local pid
        pid="$(cat "$PID_FILE" 2>/dev/null || echo "")"
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            log_info "Sending SIGTERM to emulator process ${pid}..."
            kill -15 "$pid" 2>/dev/null || true
            
            # Wait up to 10 seconds for clean shutdown
            local count=0
            while kill -0 "$pid" 2>/dev/null && [ "$count" -lt 10 ]; do
                sleep 1
                count=$((count + 1))
            done

            if kill -0 "$pid" 2>/dev/null; then
                log_warn "Process ${pid} did not terminate; sending SIGKILL..."
                kill -9 "$pid" 2>/dev/null || true
            fi
        fi
        rm -f "$PID_FILE"
    fi

    log_info "Emulator stopped."
}

# ------------------------------------------------------------------------------
# status: Queries running state and boot status
# ------------------------------------------------------------------------------
cmd_status() {
    local adb_bin
    adb_bin="$(find_adb_bin)"

    if is_pid_running; then
        local pid
        pid="$(cat "$PID_FILE")"
        local boot_completed
        boot_completed="$("$adb_bin" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || echo "0")"

        if [ "$boot_completed" = "1" ]; then
            log_info "Emulator Status: RUNNING (PID ${pid}) - Boot Completed"
            return 0
        else
            log_info "Emulator Status: BOOTING (PID ${pid}) - sys.boot_completed=${boot_completed}"
            return 0
        fi
    else
        log_info "Emulator Status: STOPPED"
        return 1
    fi
}

# ------------------------------------------------------------------------------
# Entrypoint Dispatcher
# ------------------------------------------------------------------------------
usage() {
    echo "Usage: $0 {start|wait-ready|stop|status}"
    exit 1
}

case "${1:-}" in
    start)
        cmd_start
        ;;
    wait-ready)
        cmd_wait_ready
        ;;
    stop)
        cmd_stop
        ;;
    status)
        cmd_status
        ;;
    *)
        usage
        ;;
esac
