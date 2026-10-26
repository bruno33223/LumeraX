"""Telemetry collectors for Android dumpsys meminfo, gfxinfo, and SurfaceFlinger."""

from __future__ import annotations

import logging
import math
import re
from typing import Any, Dict, List, Optional

from scripts.e2e.adb_bridge import AdbBridge
from scripts.e2e.models import MemoryMetrics, RenderMetrics

logger = logging.getLogger("lumera.e2e.telemetry")


class TelemetryCollector:
    def __init__(self, bridge: AdbBridge) -> None:
        self.bridge = bridge

    # --------------------------------------------------------------------------
    # dumpsys meminfo parsing
    # --------------------------------------------------------------------------
    def capture_meminfo(self, package: str = "com.lumera.app") -> Dict[str, int]:
        """Captures and parses memory metrics from dumpsys meminfo.

        Returns a dictionary with:
        - TOTAL PSS (KB)
        - Native Heap (Allocated) (KB)
        - Dalvik Heap (Allocated) (KB)
        - Views (count)
        - AppContexts (count)
        - Activities (count)
        """
        raw_output = self.bridge.execute_shell(f"dumpsys meminfo {package}")
        metrics: Dict[str, int] = {
            "TOTAL PSS": 0,
            "Native Heap (Allocated)": 0,
            "Dalvik Heap (Allocated)": 0,
            "Views": 0,
            "AppContexts": 0,
            "Activities": 0,
        }

        for line in raw_output.splitlines():
            line_stripped = line.strip()

            # Look for TOTAL PSS: "TOTAL   123456" or "TOTAL:  123456" or "TOTAL PSS: 123456"
            total_match = re.search(r"^\s*TOTAL\s+(\d+)", line, re.IGNORECASE)
            if not total_match:
                total_match = re.search(r"TOTAL\s+PSS:\s*(\d+)", line, re.IGNORECASE)
            if total_match and metrics["TOTAL PSS"] == 0:
                metrics["TOTAL PSS"] = int(total_match.group(1))

            # Native Heap line: "Native Heap    15420    15380        0        0    25600    18320"
            if "Native Heap" in line:
                tokens = line.split()
                # If table row: Native, Heap, Pss, PrivDirty, PrivClean, Swap, HeapSize, HeapAlloc, HeapFree
                # Tokens usually: ['Native', 'Heap', pss, priv_dirty, ..., heap_size, heap_alloc, heap_free]
                if len(tokens) >= 8:
                    try:
                        metrics["Native Heap (Allocated)"] = int(tokens[-2])
                    except (ValueError, IndexError):
                        pass

            # Dalvik Heap line
            if "Dalvik Heap" in line:
                tokens = line.split()
                if len(tokens) >= 8:
                    try:
                        metrics["Dalvik Heap (Allocated)"] = int(tokens[-2])
                    except (ValueError, IndexError):
                        pass

            # Objects block:
            # Views: 12   ViewRootImpl: 1
            # AppContexts: 3   Activities: 1
            m_views = re.search(r"\bViews:\s*(\d+)", line)
            if m_views:
                metrics["Views"] = int(m_views.group(1))

            m_app_ctx = re.search(r"\bAppContexts:\s*(\d+)", line)
            if m_app_ctx:
                metrics["AppContexts"] = int(m_app_ctx.group(1))

            m_act = re.search(r"\bActivities:\s*(\d+)", line)
            if m_act:
                metrics["Activities"] = int(m_act.group(1))

        logger.info(
            "Captured MemInfo for %s: TOTAL PSS=%d KB, NativeAlloc=%d KB, DalvikAlloc=%d KB, Activities=%d",
            package,
            metrics["TOTAL PSS"],
            metrics["Native Heap (Allocated)"],
            metrics["Dalvik Heap (Allocated)"],
            metrics["Activities"],
        )
        return metrics

    def evaluate_memory_leak(
        self,
        baseline: Dict[str, int],
        teardown: Dict[str, int],
        peak_pss: int = 0,
        threshold_kb: int = 30720,  # 30 MB threshold
    ) -> MemoryMetrics:
        """Evaluates whether playback resources (Codecs, Media3, Surfaces) leaked after teardown."""
        base_pss = baseline.get("TOTAL PSS", 0)
        tear_pss = teardown.get("TOTAL PSS", 0)
        delta_pss = tear_pss - base_pss

        base_native = baseline.get("Native Heap (Allocated)", 0)
        tear_native = teardown.get("Native Heap (Allocated)", 0)
        delta_native = tear_native - base_native

        leak_detected = delta_pss > threshold_kb
        if leak_detected:
            logger.warning(
                "Potential memory leak detected! Delta PSS: %d KB exceeds threshold %d KB (Native delta: %d KB)",
                delta_pss,
                threshold_kb,
                delta_native,
            )
        else:
            logger.info("Memory stability check passed. Delta PSS: %d KB (threshold: %d KB)", delta_pss, threshold_kb)

        return MemoryMetrics(
            baseline_pss_kb=base_pss,
            peak_pss_kb=max(peak_pss, base_pss, tear_pss),
            teardown_pss_kb=tear_pss,
            leak_detected=leak_detected,
            leak_delta_kb=delta_pss,
            native_heap_delta_kb=delta_native,
        )

    # --------------------------------------------------------------------------
    # dumpsys gfxinfo parsing
    # --------------------------------------------------------------------------
    def reset_gfxinfo(self, package: str = "com.lumera.app") -> None:
        """Resets the gfxinfo stats counter."""
        logger.debug("Resetting gfxinfo for %s", package)
        self.bridge.execute_shell(f"dumpsys gfxinfo {package} reset")

    def capture_gfxinfo(self, package: str = "com.lumera.app") -> RenderMetrics:
        """Parses dumpsys gfxinfo framestats, computing frame counts, jank percentage, and p90/p99 latency."""
        raw_output = self.bridge.execute_shell(f"dumpsys gfxinfo {package} framestats")

        total_frames = 0
        janky_frames = 0
        p90_frame_time_ms = 0.0
        p99_frame_time_ms = 0.0

        # Pattern 1: High-level summary printed by Android 8+
        m_total = re.search(r"Total frames rendered:\s*(\d+)", raw_output)
        m_jank = re.search(r"Janky frames:\s*(\d+)", raw_output)
        m_p90 = re.search(r"90th percentile:\s*([\d\.]+)ms", raw_output)
        m_p99 = re.search(r"99th percentile:\s*([\d\.]+)ms", raw_output)

        if m_total:
            total_frames = int(m_total.group(1))
        if m_jank:
            janky_frames = int(m_jank.group(1))
        if m_p90:
            p90_frame_time_ms = float(m_p90.group(1))
        if m_p99:
            p99_frame_time_ms = float(m_p99.group(1))

        # Pattern 2: Raw framestats table parsing (nanoseconds)
        # Columns: Flags,IntendedVsync,Vsync,...,SwapBuffers,FrameCompleted
        if total_frames == 0:
            frame_times_ms: List[float] = []
            in_framestats = False
            for line in raw_output.splitlines():
                if "Flags,IntendedVsync" in line:
                    in_framestats = True
                    continue
                if in_framestats:
                    if not line.strip() or line.startswith("---"):
                        break
                    tokens = line.split(",")
                    if len(tokens) >= 13:
                        try:
                            intended_vsync = int(tokens[1])
                            frame_completed = int(tokens[13]) if len(tokens) > 13 else int(tokens[-1])
                            duration_ns = frame_completed - intended_vsync
                            if duration_ns > 0:
                                dur_ms = duration_ns / 1_000_000.0
                                frame_times_ms.append(dur_ms)
                        except (ValueError, IndexError):
                            continue

            if frame_times_ms:
                total_frames = len(frame_times_ms)
                janky_frames = sum(1 for d in frame_times_ms if d > 16.67)  # 60Hz threshold
                frame_times_ms.sort()
                p90_idx = int(math.ceil(0.90 * total_frames)) - 1
                p99_idx = int(math.ceil(0.99 * total_frames)) - 1
                p90_frame_time_ms = round(frame_times_ms[max(0, p90_idx)], 2)
                p99_frame_time_ms = round(frame_times_ms[max(0, p99_idx)], 2)

        jank_percentage = round((janky_frames / total_frames * 100.0) if total_frames > 0 else 0.0, 2)

        logger.info(
            "Captured GfxInfo for %s: %d total frames, %d janky (%.2f%%), p90=%.1fms, p99=%.1fms",
            package,
            total_frames,
            janky_frames,
            jank_percentage,
            p90_frame_time_ms,
            p99_frame_time_ms,
        )

        return RenderMetrics(
            total_frames=total_frames,
            janky_frames=janky_frames,
            jank_percentage=jank_percentage,
            p90_frame_time_ms=p90_frame_time_ms,
            p99_frame_time_ms=p99_frame_time_ms,
        )

    # --------------------------------------------------------------------------
    # dumpsys SurfaceFlinger verification
    # --------------------------------------------------------------------------
    def is_surface_rendering(self, layer_keyword: str = "com.lumera.app") -> bool:
        """Inspects SurfaceFlinger to confirm an active rendering surface for LumeraX exists."""
        try:
            output = self.bridge.execute_shell("dumpsys SurfaceFlinger --list")
            matching_layers = [l.strip() for l in output.splitlines() if layer_keyword in l]
            return len(matching_layers) > 0
        except Exception as exc:
            logger.warning("Error inspecting SurfaceFlinger: %s", exc)
            return False
