"""Report generator for LumeraX E2E Playback & Telemetry.

Outputs structured JSON and a modern, standalone HTML Dashboard.
"""

from __future__ import annotations

import json
import logging
import os
from typing import Optional

from scripts.e2e.models import E2EReport, StreamStatus

logger = logging.getLogger("lumera.e2e.report_generator")


class ReportGenerator:
    @staticmethod
    def generate_json_report(report: E2EReport, output_path: str) -> None:
        """Writes the consolidated E2E report as a structured JSON file."""
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
        with open(output_path, "w", encoding="utf-8") as f:
            f.write(report.to_json(indent=2))
        logger.info("JSON report written successfully to %s", output_path)

    @staticmethod
    def generate_html_dashboard(report: E2EReport, output_path: str) -> None:
        """Generates a modern, responsive, standalone HTML dashboard for E2E playback telemetry."""
        os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)

        status_color = "#10b981" if report.overall_status == "PASS" else ("#f59e0b" if report.overall_status == "WARN" else "#ef4444")
        success_rate = (
            round((report.successful_sources / report.total_sources_tested * 100.0), 1)
            if report.total_sources_tested > 0
            else 0.0
        )

        categories_html = []
        for cat in report.categories:
            sources_rows = []
            for src in cat.sources_tested:
                badge_class = "badge-success" if src.status == StreamStatus.SUCCESS else "badge-danger"
                err_text = f"<small class='text-muted'><br>Err: {src.error_detail}</small>" if src.error_detail else ""
                sources_rows.append(
                    f"""
                    <tr>
                        <td><strong>#{src.index}</strong></td>
                        <td>{src.source_name}</td>
                        <td><span class='badge badge-protocol'>{src.protocol}</span></td>
                        <td><span class='badge {badge_class}'>{src.status.value}</span>{err_text}</td>
                        <td>{src.latency_to_first_frame_ms} ms</td>
                        <td>{src.duration_played_sec:.1f} s</td>
                    </tr>
                    """
                )

            # Memory card
            mem_html = ""
            if cat.memory_metrics:
                m = cat.memory_metrics
                leak_badge = "<span class='badge badge-danger'>LEAK DETECTADO</span>" if m.leak_detected else "<span class='badge badge-success'>ESTÁVEL</span>"
                mem_html = f"""
                <div class="metrics-card">
                    <h4>🧠 Telemetria de Memória (PSS / Heap)</h4>
                    <div class="metric-row"><span>Baseline PSS:</span> <strong>{m.baseline_pss_kb // 1024} MB</strong></div>
                    <div class="metric-row"><span>Peak PSS:</span> <strong>{m.peak_pss_kb // 1024} MB</strong></div>
                    <div class="metric-row"><span>Teardown PSS:</span> <strong>{m.teardown_pss_kb // 1024} MB</strong></div>
                    <div class="metric-row"><span>Delta PSS:</span> <strong>{m.leak_delta_kb // 1024} MB</strong></div>
                    <div class="metric-row"><span>Native Heap Delta:</span> <strong>{m.native_heap_delta_kb // 1024} MB</strong></div>
                    <div class="metric-row"><span>Status de Retenção:</span> {leak_badge}</div>
                </div>
                """

            # Render card
            render_html = ""
            if cat.render_metrics:
                r = cat.render_metrics
                jank_badge = "<span class='badge badge-danger'>ALTO</span>" if r.jank_percentage > 10.0 else "<span class='badge badge-success'>FLUIDO</span>"
                render_html = f"""
                <div class="metrics-card">
                    <h4>⚡ Telemetria Gráfica (gfxinfo)</h4>
                    <div class="metric-row"><span>Total de Frames:</span> <strong>{r.total_frames}</strong></div>
                    <div class="metric-row"><span>Janky Frames:</span> <strong>{r.janky_frames} ({r.jank_percentage}%)</strong></div>
                    <div class="metric-row"><span>P90 Frame Latency:</span> <strong>{r.p90_frame_time_ms} ms</strong></div>
                    <div class="metric-row"><span>P99 Frame Latency:</span> <strong>{r.p99_frame_time_ms} ms</strong></div>
                    <div class="metric-row"><span>Classificação:</span> {jank_badge}</div>
                </div>
                """

            cat_html = f"""
            <div class="category-section">
                <div class="category-header">
                    <div>
                        <span class="category-tag">{cat.category}</span>
                        <h3>{cat.media_title}</h3>
                    </div>
                    <div>
                        <span class="badge {'badge-success' if cat.overall_passed else 'badge-danger'}">
                            {'APROVADO' if cat.overall_passed else 'FALHOU'}
                        </span>
                    </div>
                </div>

                <div class="metrics-grid">
                    {mem_html}
                    {render_html}
                </div>

                <div class="table-container">
                    <h4>Fontes Testadas (Até 5 tentativas de playback)</h4>
                    <table>
                        <thead>
                            <tr>
                                <th>#</th>
                                <th>Nome da Fonte</th>
                                <th>Protocolo</th>
                                <th>Status</th>
                                <th>Latência 1º Frame</th>
                                <th>Tempo Reproduzido</th>
                            </tr>
                        </thead>
                        <tbody>
                            {''.join(sources_rows) if sources_rows else '<tr><td colspan="6" class="text-center">Nenhuma fonte testada.</td></tr>'}
                        </tbody>
                    </table>
                </div>
            </div>
            """
            categories_html.append(cat_html)

        html_content = f"""<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>LumeraX - Relatório E2E de Playback & Telemetria</title>
    <style>
        :root {{
            --bg-color: #0f172a;
            --card-bg: #1e293b;
            --card-sub-bg: #0f172a;
            --text-color: #f8fafc;
            --text-muted: #94a3b8;
            --border-color: #334155;
            --accent-color: #38bdf8;
            --success-color: #10b981;
            --warning-color: #f59e0b;
            --danger-color: #ef4444;
            --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
        }}
        * {{ box-sizing: border-box; margin: 0; padding: 0; }}
        body {{
            background-color: var(--bg-color);
            color: var(--text-color);
            font-family: var(--font-family);
            line-height: 1.5;
            padding: 30px;
        }}
        .container {{
            max-width: 1200px;
            margin: 0 auto;
        }}
        header {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-bottom: 24px;
            border-bottom: 1px solid var(--border-color);
            margin-bottom: 30px;
        }}
        h1 {{ font-size: 26px; font-weight: 700; color: #fff; }}
        .header-meta {{ color: var(--text-muted); font-size: 14px; margin-top: 4px; }}
        .overall-badge {{
            padding: 8px 20px;
            border-radius: 9999px;
            font-size: 16px;
            font-weight: 700;
            background-color: {status_color}22;
            color: {status_color};
            border: 1px solid {status_color};
        }}
        .summary-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }}
        .summary-card {{
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 20px;
        }}
        .summary-card .label {{ font-size: 13px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; }}
        .summary-card .value {{ font-size: 28px; font-weight: 700; margin-top: 6px; color: #fff; }}
        .category-section {{
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 30px;
        }}
        .category-header {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 16px;
            margin-bottom: 20px;
        }}
        .category-tag {{
            font-size: 11px;
            text-transform: uppercase;
            font-weight: 700;
            background: #0284c7;
            color: white;
            padding: 3px 8px;
            border-radius: 4px;
            display: inline-block;
            margin-bottom: 6px;
        }}
        .metrics-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
            gap: 20px;
            margin-bottom: 24px;
        }}
        .metrics-card {{
            background: var(--card-sub-bg);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 16px;
        }}
        .metrics-card h4 {{ font-size: 14px; margin-bottom: 12px; color: var(--accent-color); border-bottom: 1px dashed var(--border-color); padding-bottom: 8px; }}
        .metric-row {{ display: flex; justify-content: space-between; align-items: center; font-size: 14px; margin-bottom: 8px; }}
        .table-container {{
            margin-top: 16px;
            overflow-x: auto;
        }}
        .table-container h4 {{ font-size: 14px; margin-bottom: 12px; color: var(--text-muted); }}
        table {{ width: 100%; border-collapse: collapse; text-align: left; font-size: 14px; }}
        th, td {{ padding: 10px 14px; border-bottom: 1px solid var(--border-color); }}
        th {{ background: var(--card-sub-bg); color: var(--text-muted); font-weight: 600; }}
        tr:hover {{ background-color: rgba(255, 255, 255, 0.02); }}
        .badge {{
            padding: 4px 10px;
            border-radius: 6px;
            font-size: 12px;
            font-weight: 600;
            display: inline-block;
        }}
        .badge-success {{ background: #065f46; color: #34d399; }}
        .badge-danger {{ background: #991b1b; color: #f87171; }}
        .badge-protocol {{ background: #1e3a8a; color: #93c5fd; }}
        .text-muted {{ color: var(--text-muted); }}
        .text-center {{ text-align: center; }}
        footer {{
            text-align: center;
            color: var(--text-muted);
            font-size: 13px;
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid var(--border-color);
        }}
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div>
                <h1>🎬 LumeraX: Relatório E2E de Playback & Telemetria</h1>
                <div class="header-meta">Execução iniciada em {report.timestamp_start} &bull; Finalizada em {report.timestamp_end}</div>
            </div>
            <div class="overall-badge">{report.overall_status}</div>
        </header>

        <div class="summary-grid">
            <div class="summary-card">
                <div class="label">Categorias Testadas</div>
                <div class="value">{len(report.categories)} / 3</div>
            </div>
            <div class="summary-card">
                <div class="label">Fontes Analisadas</div>
                <div class="value">{report.total_sources_tested}</div>
            </div>
            <div class="summary-card">
                <div class="label">Fontes com Sucesso (10s+)</div>
                <div class="value">{report.successful_sources}</div>
            </div>
            <div class="summary-card">
                <div class="label">Taxa de Resiliência</div>
                <div class="value" style="color: {status_color};">{success_rate}%</div>
            </div>
        </div>

        {''.join(categories_html)}

        <footer>
            LumeraX CI/E2E Automated Quality Assurance &bull; Secondary Partition Isolated Toolchain (/SteamGames)
        </footer>
    </div>
</body>
</html>
"""
        with open(output_path, "w", encoding="utf-8") as f:
            f.write(html_content)
        logger.info("HTML Dashboard written successfully to %s", output_path)
