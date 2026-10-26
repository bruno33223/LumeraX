#!/usr/bin/env bash
# ==============================================================================
# LumeraX: Android Toolchain & Isolated Secondary Partition Setup
# ==============================================================================
# Centralizes Android SDK, AVD images, and Gradle caches in secondary storage
# to prevent root (/) partition exhaustion.
# ==============================================================================
set -euo pipefail

# Determine Project Root and Source .env.test if present
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ -f "${PROJECT_ROOT}/.env.test" ]; then
    # Load test environment variables without exporting everything blindly
    # shellcheck disable=SC1091
    set -a
    source "${PROJECT_ROOT}/.env.test"
    set +a
fi

# Configuration Defaults (Priority: Env Var > .env.test > Default Secondary Partition)
TARGET_BASE="${STEAM_ANDROID_BASE:-${STEAM_PARTITION:-/media/bruno/SteamGames/android}}"
AVD_NAME="${AVD_NAME:-lumerax_e2e_tv}"
REQUIRED_SPACE_KB=$((15 * 1024 * 1024)) # 15 GB in Kilobytes
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

log_info() {
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - $*"
}

log_warn() {
    echo "[WARN] $(date '+%Y-%m-%d %H:%M:%S') - $*" >&2
}

log_error() {
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') - $*" >&2
}

# ------------------------------------------------------------------------------
# validate_partition_storage(target_path: String) -> ExitCode
# Validates that the target path has at least 15GB of free space.
# ------------------------------------------------------------------------------
validate_partition_storage() {
    local target_path="$1"
    mkdir -p "$target_path"
    
    local available_kb
    available_kb=$(df -k "$target_path" | awk 'NR==2 {print $4}')
    
    if [ -z "$available_kb" ]; then
        log_error "Unable to inspect disk capacity for path: ${target_path}"
        return 1
    fi

    local available_gb=$((available_kb / 1024 / 1024))
    log_info "Storage check on ${target_path}: ${available_gb} GB free."

    if [ "$available_kb" -lt "$REQUIRED_SPACE_KB" ]; then
        log_error "Insufficient space on ${target_path}. Required: >= 15GB, Available: ${available_gb}GB."
        return 2
    fi

    log_info "Storage validation passed successfully."
    return 0
}

# ------------------------------------------------------------------------------
# export_android_variables(base_path: String) -> Void
# Configures and exports isolated environment variables.
# ------------------------------------------------------------------------------
export_android_variables() {
    local base_path="$1"

    export ANDROID_HOME="${base_path}/sdk"
    export ANDROID_USER_HOME="${base_path}/.android"
    export GRADLE_USER_HOME="${base_path}/.gradle"
    export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"

    mkdir -p "${ANDROID_HOME}" "${ANDROID_USER_HOME}" "${GRADLE_USER_HOME}"

    # Re-use existing /media/bruno/SteamGames/android-sdk if present and sdk is empty/missing
    if [ ! -d "${ANDROID_HOME}/build-tools" ] && [ -d "/media/bruno/SteamGames/android-sdk" ]; then
        log_info "Detected existing Android SDK at /media/bruno/SteamGames/android-sdk. Linking components..."
        for item in /media/bruno/SteamGames/android-sdk/*; do
            local base_item
            base_item="$(basename "$item")"
            if [ ! -e "${ANDROID_HOME}/${base_item}" ]; then
                ln -sf "$item" "${ANDROID_HOME}/${base_item}"
            fi
        done
    fi

    log_info "Configured isolated environment:"
    log_info "  ANDROID_HOME     = ${ANDROID_HOME}"
    log_info "  ANDROID_USER_HOME= ${ANDROID_USER_HOME}"
    log_info "  GRADLE_USER_HOME = ${GRADLE_USER_HOME}"
}

# ------------------------------------------------------------------------------
# sync_local_properties(sdk_dir: String) -> Void
# Updates sdk.dir in local.properties preserving existing configuration keys.
# ------------------------------------------------------------------------------
sync_local_properties() {
    local sdk_dir="$1"
    local props_file="${PROJECT_ROOT}/local.properties"

    log_info "Syncing sdk.dir in ${props_file}..."
    if [ ! -f "$props_file" ]; then
        cat <<EOF > "$props_file"
sdk.dir=${sdk_dir}
acra.url=https://dummy.acra.url/
acra.token=dummy_token
tmdb.api_key=dummy_tmdb_key
TRAKT_CLIENT_ID=dummy_trakt_id
TRAKT_CLIENT_SECRET=dummy_trakt_secret
EOF
        log_info "Created new ${props_file}."
    else
        if grep -q "^sdk.dir=" "$props_file"; then
            sed -i "s|^sdk.dir=.*|sdk.dir=${sdk_dir}|" "$props_file"
        else
            echo "sdk.dir=${sdk_dir}" >> "$props_file"
        fi
        log_info "Updated sdk.dir in existing ${props_file}."
    fi
}

# ------------------------------------------------------------------------------
# ensure_cmdline_tools() -> Void
# Downloads and installs cmdline-tools if sdkmanager is not present.
# ------------------------------------------------------------------------------
ensure_cmdline_tools() {
    local sdkmanager_bin="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
    if [ -x "$sdkmanager_bin" ]; then
        log_info "cmdline-tools already installed at ${sdkmanager_bin}."
        return 0
    fi

    log_info "sdkmanager not found. Downloading cmdline-tools..."
    local temp_zip="${TARGET_BASE}/cmdline-tools-temp.zip"
    local temp_extract="${TARGET_BASE}/cmdline-tools-extracted"

    curl -fSL "$CMDLINE_TOOLS_URL" -o "$temp_zip"
    mkdir -p "$temp_extract"
    unzip -q -o "$temp_zip" -d "$temp_extract"

    mkdir -p "${ANDROID_HOME}/cmdline-tools"
    rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
    mv "${temp_extract}/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"

    rm -rf "$temp_zip" "$temp_extract"
    log_info "cmdline-tools installed successfully."
}

# ------------------------------------------------------------------------------
# install_sdk_packages() -> Void
# Executes sdkmanager to install platform, emulator, and system-images.
# ------------------------------------------------------------------------------
install_sdk_packages() {
    ensure_cmdline_tools

    local sdkmanager_bin="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
    log_info "Accepting SDK licenses..."
    yes | "$sdkmanager_bin" --licenses >/dev/null 2>&1 || true

    log_info "Installing Android SDK packages (platform-tools, platforms;android-34, build-tools;34.0.0, system-images;android-34;default;x86_64, emulator)..."
    "$sdkmanager_bin" \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "system-images;android-34;default;x86_64" \
        "emulator"

    log_info "Android SDK packages successfully installed."
}

# ------------------------------------------------------------------------------
# create_or_update_avd(avd_name: String) -> Void
# Creates or updates the TV AVD profile and tunes config.ini for headless runs.
# ------------------------------------------------------------------------------
create_or_update_avd() {
    local avd_name="$1"
    local avdmanager_bin="${ANDROID_HOME}/cmdline-tools/latest/bin/avdmanager"

    log_info "Creating or updating AVD '${avd_name}'..."
    echo "no" | "$avdmanager_bin" create avd \
        -n "$avd_name" \
        -k "system-images;android-34;default;x86_64" \
        --device "tv_1080p" \
        --force

    local avd_config_file="${ANDROID_USER_HOME}/avd/${avd_name}.avd/config.ini"
    if [ ! -f "$avd_config_file" ]; then
        log_warn "AVD config file not found at ${avd_config_file}, attempting alternate path..."
        avd_config_file="$(find "${ANDROID_USER_HOME}" -name "config.ini" | head -n 1)"
    fi

    if [ -f "$avd_config_file" ]; then
        log_info "Tuning AVD hardware config in ${avd_config_file}..."
        # Update or append necessary properties
        grep -q "^hw.ramSize=" "$avd_config_file" && sed -i "s/^hw.ramSize=.*/hw.ramSize=2048/" "$avd_config_file" || echo "hw.ramSize=2048" >> "$avd_config_file"
        grep -q "^vm.heapSize=" "$avd_config_file" && sed -i "s/^vm.heapSize=.*/vm.heapSize=512/" "$avd_config_file" || echo "vm.heapSize=512" >> "$avd_config_file"
        grep -q "^hw.gpu.enabled=" "$avd_config_file" && sed -i "s/^hw.gpu.enabled=.*/hw.gpu.enabled=yes/" "$avd_config_file" || echo "hw.gpu.enabled=yes" >> "$avd_config_file"
        grep -q "^hw.gpu.mode=" "$avd_config_file" && sed -i "s/^hw.gpu.mode=.*/hw.gpu.mode=swiftshader_indirect/" "$avd_config_file" || echo "hw.gpu.mode=swiftshader_indirect"
        log_info "AVD '${avd_name}' successfully configured."
    else
        log_warn "Could not locate config.ini for AVD '${avd_name}'."
    fi
}

# ------------------------------------------------------------------------------
# Main Execution Entrypoint
# ------------------------------------------------------------------------------
main() {
    log_info "=== Starting Android Environment & Storage Isolation Provisioning ==="
    validate_partition_storage "${TARGET_BASE}"
    export_android_variables "${TARGET_BASE}"
    sync_local_properties "${ANDROID_HOME}"

    # Only install SDK packages and AVD if not sourced
    if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
        install_sdk_packages
        create_or_update_avd "${AVD_NAME}"
        log_info "=== Android Environment Setup Completed Successfully ==="
    fi
}

main "$@"
