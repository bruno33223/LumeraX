"""Comprehensive unit test suite for LumeraX E2E Telemetry & Reporting Engine."""

import json
import os
import shutil
import tempfile
import unittest
from unittest.mock import MagicMock

from scripts.e2e.adb_bridge import AdbBridge, AdbKeyCodes
from scripts.e2e.models import (
    CategoryTestResult,
    E2EReport,
    MemoryMetrics,
    RenderMetrics,
    SourceResult,
    StreamStatus,
)
from scripts.e2e.report_generator import ReportGenerator
from scripts.e2e.telemetry_collector import TelemetryCollector

SAMPLE_MEMINFO_OUTPUT = """
Applications Memory Usage (in Kilobytes):
Uptime: 123456 Realtime: 123456

** MEMINFO in pid 2415 [com.lumera.app] **
                   Pss  Private  Private  SwapPss     Heap     Heap     Heap
                 Total    Dirty    Clean    Dirty     Size    Alloc     Free
                ------   ------   ------   ------   ------   ------   ------
  Native Heap    34200    34150        0        0    48000    32100    15900
  Dalvik Heap    22100    22050        0        0    36000    21500    14500
        Stack      840      840        0        0
       Ashmem      120        0        0        0
      Gfx dev    14500    14500        0        0
    Other dev       40        0       40        0
     .so mmap     5200      320     3100        0
    .apk mmap     2100        0     1200        0
    .ttf mmap      150        0      150        0
    .dex mmap     8400       12     8100        0
    .oat mmap     1100        0      400        0
    .art mmap     3200     2800      200        0
   Other mmap      600        8      500        0
      Unknown     1800     1800        0        0
        TOTAL    94250    76480    13690        0    84000    53600    30400

 App Summary
                       Pss(KB)
                        ------
           Java Heap:    25300
         Native Heap:    34200
                Code:    17070
               Stack:      840
            Graphics:    14500
       Private Other:     4570
              System:     1770

               TOTAL:    98250       TOTAL SWAP PSS:        0

 Objects
               Views:       24         ViewRootImpl:        1
         AppContexts:        3           Activities:        1
              Assets:        4        AssetManagers:        0
        Local Binders:       18        Proxy Binders:       28
        Parcel memory:        6         Parcel count:       24
     Death Recipients:        2      OpenSSL Sockets:        0
            WebViews:        0
"""

SAMPLE_GFXINFO_OUTPUT = """
Applications Graphics Acceleration Info:
Uptime: 54321 Realtime: 54321

** GRAPHICS INFO DURING Playback [com.lumera.app] **

Total frames rendered: 600
Janky frames: 18 (3.00%)
50th percentile: 8ms
90th percentile: 14ms
95th percentile: 16ms
99th percentile: 22ms
Number Missed Vsync: 2
Number High input latency: 0
Number Slow UI thread: 6
Number Slow bitmap uploads: 0
Number Slow issue draw commands: 10
Number Frame deadline missed: 18
"""


class TestTelemetryCollector(unittest.TestCase):
    def setUp(self) -> None:
        self.mock_bridge = MagicMock(spec=AdbBridge)
        self.collector = TelemetryCollector(self.mock_bridge)

    def test_parse_meminfo(self) -> None:
        self.mock_bridge.execute_shell.return_value = SAMPLE_MEMINFO_OUTPUT
        metrics = self.collector.capture_meminfo("com.lumera.app")

        self.assertEqual(metrics["TOTAL PSS"], 94250)
        self.assertEqual(metrics["Native Heap (Allocated)"], 32100)
        self.assertEqual(metrics["Dalvik Heap (Allocated)"], 21500)
        self.assertEqual(metrics["Views"], 24)
        self.assertEqual(metrics["AppContexts"], 3)
        self.assertEqual(metrics["Activities"], 1)

    def test_parse_gfxinfo(self) -> None:
        self.mock_bridge.execute_shell.return_value = SAMPLE_GFXINFO_OUTPUT
        render_metrics = self.collector.capture_gfxinfo("com.lumera.app")

        self.assertEqual(render_metrics.total_frames, 600)
        self.assertEqual(render_metrics.janky_frames, 18)
        self.assertEqual(render_metrics.jank_percentage, 3.0)
        self.assertEqual(render_metrics.p90_frame_time_ms, 14.0)
        self.assertEqual(render_metrics.p99_frame_time_ms, 22.0)

    def test_evaluate_memory_leak_no_leak(self) -> None:
        baseline = {"TOTAL PSS": 100000, "Native Heap (Allocated)": 40000}
        teardown = {"TOTAL PSS": 110000, "Native Heap (Allocated)": 42000}

        metrics = self.collector.evaluate_memory_leak(baseline, teardown, peak_pss=150000, threshold_kb=30720)
        self.assertFalse(metrics.leak_detected)
        self.assertEqual(metrics.leak_delta_kb, 10000)
        self.assertEqual(metrics.native_heap_delta_kb, 2000)
        self.assertEqual(metrics.peak_pss_kb, 150000)

    def test_evaluate_memory_leak_detected(self) -> None:
        baseline = {"TOTAL PSS": 100000, "Native Heap (Allocated)": 40000}
        teardown = {"TOTAL PSS": 145000, "Native Heap (Allocated)": 65000}

        # Delta PSS is 45000 KB > 30720 KB
        metrics = self.collector.evaluate_memory_leak(baseline, teardown, peak_pss=180000, threshold_kb=30720)
        self.assertTrue(metrics.leak_detected)
        self.assertEqual(metrics.leak_delta_kb, 45000)
        self.assertEqual(metrics.native_heap_delta_kb, 25000)


class TestReportGenerator(unittest.TestCase):
    def setUp(self) -> None:
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self) -> None:
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_generate_json_and_html_reports(self) -> None:
        source_1 = SourceResult(
            index=1,
            source_name="Cloudflare Fast Stream",
            protocol="HLS",
            status=StreamStatus.SUCCESS,
            latency_to_first_frame_ms=620,
            duration_played_sec=10.2,
        )
        source_2 = SourceResult(
            index=2,
            source_name="Offline Scraper Mirror",
            protocol="HTTP",
            status=StreamStatus.TIMEOUT,
            latency_to_first_frame_ms=0,
            duration_played_sec=0.0,
            error_detail="Connection timed out after 8s",
        )

        mem = MemoryMetrics(
            baseline_pss_kb=95000,
            peak_pss_kb=132000,
            teardown_pss_kb=99000,
            leak_detected=False,
            leak_delta_kb=4000,
            native_heap_delta_kb=1500,
        )

        render = RenderMetrics(
            total_frames=600,
            janky_frames=12,
            jank_percentage=2.0,
            p90_frame_time_ms=13.5,
            p99_frame_time_ms=19.2,
        )

        category = CategoryTestResult(
            category="MOVIES",
            media_title="Big Buck Bunny (Cinema 4K)",
            sources_tested=[source_1, source_2],
            memory_metrics=mem,
            render_metrics=render,
            overall_passed=True,
        )

        report = E2EReport(
            timestamp_start="2026-10-26 14:00:00",
            timestamp_end="2026-10-26 14:02:30",
            environment={
                "device_serial": "emulator-5554",
                "package_name": "com.lumera.app",
                "stremio_account": "brunogrti@gmail.com",
            },
            categories=[category],
            total_sources_tested=2,
            successful_sources=1,
            overall_status="PASS",
        )

        json_path = os.path.join(self.test_dir, "test_report.json")
        html_path = os.path.join(self.test_dir, "test_report.html")

        ReportGenerator.generate_json_report(report, json_path)
        ReportGenerator.generate_html_dashboard(report, html_path)

        # Validate JSON Report
        self.assertTrue(os.path.isfile(json_path))
        with open(json_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            self.assertEqual(data["overall_status"], "PASS")
            self.assertEqual(data["total_sources_tested"], 2)
            self.assertEqual(data["successful_sources"], 1)
            self.assertEqual(len(data["categories"]), 1)
            self.assertEqual(data["categories"][0]["category"], "MOVIES")

        # Validate HTML Dashboard
        self.assertTrue(os.path.isfile(html_path))
        with open(html_path, "r", encoding="utf-8") as f:
            content = f.read()
            self.assertIn("LumeraX - Relatório E2E de Playback & Telemetria", content)
            self.assertIn("Big Buck Bunny", content)
            self.assertIn("Cloudflare Fast Stream", content)
            self.assertIn("Offline Scraper Mirror", content)
            self.assertIn("Telemetria de Memória (PSS / Heap)", content)
            self.assertIn("Telemetria Gráfica (gfxinfo)", content)


class TestAdbBridge(unittest.TestCase):
    def test_keycodes(self) -> None:
        self.assertEqual(AdbKeyCodes.DPAD_UP, 19)
        self.assertEqual(AdbKeyCodes.DPAD_DOWN, 20)
        self.assertEqual(AdbKeyCodes.DPAD_LEFT, 21)
        self.assertEqual(AdbKeyCodes.DPAD_RIGHT, 22)
        self.assertEqual(AdbKeyCodes.DPAD_CENTER, 23)
        self.assertEqual(AdbKeyCodes.BACK, 4)


if __name__ == "__main__":
    unittest.main()
