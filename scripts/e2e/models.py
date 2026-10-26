"""Data models and strict typing definitions for LumeraX E2E Playback & Telemetry."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any, Dict, List, Optional


class StreamStatus(str, Enum):
    SUCCESS = "SUCCESS"
    TIMEOUT = "TIMEOUT"
    CODEC_ERROR = "CODEC_ERROR"
    NETWORK_ERROR = "NETWORK_ERROR"
    PLAYBACK_ABORTED = "PLAYBACK_ABORTED"


@dataclass
class SourceResult:
    index: int
    source_name: str
    protocol: str  # "HTTP", "TORRENT", "HLS"
    status: StreamStatus
    latency_to_first_frame_ms: int
    duration_played_sec: float
    error_detail: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data["status"] = self.status.value
        return data


@dataclass
class MemoryMetrics:
    baseline_pss_kb: int
    peak_pss_kb: int
    teardown_pss_kb: int
    leak_detected: bool
    leak_delta_kb: int
    native_heap_delta_kb: int

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class RenderMetrics:
    total_frames: int
    janky_frames: int
    jank_percentage: float
    p90_frame_time_ms: float
    p99_frame_time_ms: float

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class CategoryTestResult:
    category: str  # "MOVIES", "SERIES", "ANIMES_CARTOONS"
    media_title: str
    sources_tested: List[SourceResult] = field(default_factory=list)
    memory_metrics: Optional[MemoryMetrics] = None
    render_metrics: Optional[RenderMetrics] = None
    overall_passed: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return {
            "category": self.category,
            "media_title": self.media_title,
            "sources_tested": [s.to_dict() for s in self.sources_tested],
            "memory_metrics": self.memory_metrics.to_dict() if self.memory_metrics else None,
            "render_metrics": self.render_metrics.to_dict() if self.render_metrics else None,
            "overall_passed": self.overall_passed,
        }


@dataclass
class E2EReport:
    timestamp_start: str
    timestamp_end: str
    environment: Dict[str, Any]
    categories: List[CategoryTestResult]
    total_sources_tested: int
    successful_sources: int
    overall_status: str  # "PASS" | "FAIL" | "WARN"

    def to_dict(self) -> Dict[str, Any]:
        return {
            "timestamp_start": self.timestamp_start,
            "timestamp_end": self.timestamp_end,
            "environment": self.environment,
            "categories": [c.to_dict() for c in self.categories],
            "total_sources_tested": self.total_sources_tested,
            "successful_sources": self.successful_sources,
            "overall_status": self.overall_status,
        }

    def to_json(self, indent: int = 2) -> str:
        return json.dumps(self.to_dict(), indent=indent)
