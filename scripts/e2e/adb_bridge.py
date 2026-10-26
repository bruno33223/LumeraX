"""Decoupled ADB communication layer and D-Pad remote control bridge for LumeraX."""

from __future__ import annotations

import logging
import os
import shutil
import subprocess
import time
from typing import List, Optional

logger = logging.getLogger("lumera.e2e.adb_bridge")


class AdbKeyCodes:
    DPAD_UP = 19
    DPAD_DOWN = 20
    DPAD_LEFT = 21
    DPAD_RIGHT = 22
    DPAD_CENTER = 23
    BACK = 4
    HOME = 3
    MENU = 82
    ENTER = 66
    TAB = 61
    SPACE = 62


class AdbBridgeError(Exception):
    """Raised when an ADB command fails."""


class AdbBridge:
    def __init__(self, device_serial: Optional[str] = None, adb_path: Optional[str] = None) -> None:
        self.device_serial = device_serial or os.environ.get("ADB_DEVICE_SERIAL")
        self.adb_path = adb_path or self._resolve_adb_binary()
        logger.info("AdbBridge initialized with binary: %s, target device: %s", self.adb_path, self.device_serial or "default")

    @staticmethod
    def _resolve_adb_binary() -> str:
        # Check ANDROID_HOME / STEAM_ANDROID_BASE first
        android_home = os.environ.get("ANDROID_HOME")
        if android_home:
            candidate = os.path.join(android_home, "platform-tools", "adb")
            if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
                return candidate

        steam_base = os.environ.get("STEAM_ANDROID_BASE", "/media/bruno/SteamGames/android")
        candidate = os.path.join(steam_base, "sdk", "platform-tools", "adb")
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate

        # Fallback to system PATH
        resolved = shutil.which("adb")
        if resolved:
            return resolved

        raise AdbBridgeError("adb binary could not be located in ANDROID_HOME, STEAM_ANDROID_BASE, or PATH.")

    def _build_command(self, args: List[str]) -> List[str]:
        cmd = [self.adb_path]
        if self.device_serial:
            cmd.extend(["-s", self.device_serial])
        cmd.extend(args)
        return cmd

    def execute_command(self, args: List[str], timeout: int = 30) -> str:
        full_cmd = self._build_command(args)
        logger.debug("Executing ADB: %s", " ".join(full_cmd))
        try:
            result = subprocess.run(
                full_cmd,
                capture_output=True,
                text=True,
                timeout=timeout,
                check=False,
            )
            if result.returncode != 0:
                raise AdbBridgeError(
                    f"ADB command failed (code {result.returncode}): {' '.join(full_cmd)}\n"
                    f"stderr: {result.stderr.strip()}\nstdout: {result.stdout.strip()}"
                )
            return result.stdout
        except subprocess.TimeoutExpired as exc:
            raise AdbBridgeError(f"ADB command timed out after {timeout}s: {' '.join(full_cmd)}") from exc
        except Exception as exc:
            if not isinstance(exc, AdbBridgeError):
                raise AdbBridgeError(f"Unexpected error running ADB command {' '.join(full_cmd)}: {exc}") from exc
            raise

    def execute_shell(self, command: str, timeout: int = 30) -> str:
        """Executes a shell command on the target Android device."""
        return self.execute_command(["shell", command], timeout=timeout)

    def is_device_connected(self) -> bool:
        """Checks if the device is reachable and responding."""
        try:
            output = self.execute_command(["devices"])
            lines = [line.strip() for line in output.splitlines() if line.strip()]
            for line in lines[1:]:  # Skip "List of devices attached"
                parts = line.split()
                if len(parts) >= 2:
                    serial, state = parts[0], parts[1]
                    if state == "device":
                        if not self.device_serial or self.device_serial == serial:
                            return True
            return False
        except Exception as exc:
            logger.warning("Error checking device connectivity: %s", exc)
            return False

    def wait_for_boot(self, timeout: int = 120) -> bool:
        """Waits until sys.boot_completed is 1."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                out = self.execute_shell("getprop sys.boot_completed", timeout=5).strip()
                if out == "1":
                    return True
            except AdbBridgeError:
                pass
            time.sleep(2)
        return False

    def send_keyevent(self, key_code: int) -> None:
        """Sends a hardware keyevent to the device (e.g. D-Pad navigation, Back)."""
        logger.debug("Dispatching keyevent: %d", key_code)
        self.execute_shell(f"input keyevent {key_code}")

    def input_text(self, text: str) -> None:
        """Types text into the currently focused input field."""
        escaped_text = text.replace(" ", "%s").replace("&", "\\&")
        logger.debug("Inputting text: %s", text)
        self.execute_shell(f"input text {escaped_text}")

    def start_app(self, package: str = "com.lumera.app", activity: str = ".MainActivity") -> None:
        """Launches the target application."""
        component = f"{package}/{activity}" if not activity.startswith(package) else activity
        logger.info("Starting app component: %s", component)
        self.execute_shell(f"am start -n {component}")

    def stop_app(self, package: str = "com.lumera.app") -> None:
        """Force stops the target application."""
        logger.info("Force stopping app package: %s", package)
        self.execute_shell(f"am force-stop {package}")

    def is_app_running(self, package: str = "com.lumera.app") -> bool:
        """Checks if the target package has active processes."""
        try:
            out = self.execute_shell(f"pidof {package}")
            return bool(out.strip())
        except AdbBridgeError:
            return False

    def install_apk(self, apk_path: str, timeout: int = 120) -> None:
        """Installs an APK on the target device."""
        if not os.path.isfile(apk_path):
            raise AdbBridgeError(f"APK file not found: {apk_path}")
        logger.info("Installing APK: %s", apk_path)
        self.execute_command(["install", "-r", "-d", apk_path], timeout=timeout)

    def take_screenshot(self, host_target_path: str) -> None:
        """Captures a screenshot of the current screen and pulls it to host."""
        remote_tmp = "/sdcard/e2e_screenshot.png"
        os.makedirs(os.path.dirname(os.path.abspath(host_target_path)), exist_ok=True)
        self.execute_shell(f"screencap -p {remote_tmp}")
        self.execute_command(["pull", remote_tmp, host_target_path])
        self.execute_shell(f"rm -f {remote_tmp}")
        logger.info("Captured screenshot saved to %s", host_target_path)
