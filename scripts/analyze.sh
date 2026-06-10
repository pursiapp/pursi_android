#!/usr/bin/env bash
set -euo pipefail

# Pursi Project Analyzer
# Launches OpenCode with pre-configured agents for comprehensive code analysis.
# Usage: ./scripts/analyze.sh [step|all|en]
#   (no args)  → interactive TUI with all agents loaded
#   step       → run all 5 analyses sequentially (non-interactive)
#   en         → use English prompts instead of Finnish
#   list       → show available agents

DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR"

if ! command -v opencode &>/dev/null; then
  echo "❌ opencode not found. Install it from https://opencode.ai"
  exit 1
fi

MODE="${1:-}"

if [ "$MODE" = "en" ]; then
  # Switch prompts to English versions
  echo "🔤 Switching to English prompts..."
  sed -i 's|architecture.txt|architecture.en.txt|g' opencode.json
  sed -i 's|usability.txt|usability.en.txt|g' opencode.json
  sed -i 's|dependencies.txt|dependencies.en.txt|g' opencode.json
  sed -i 's|bugs.txt|bugs.en.txt|g' opencode.json
  sed -i 's|recommendations.txt|recommendations.en.txt|g' opencode.json
  echo "✅ Prompts set to English. Run ./scripts/analyze.sh to start."
  exit 0
fi

if [ "$MODE" = "list" ]; then
  echo ""
  echo "╔══════════════════════════════════════════════╗"
  echo "║         Pursi — Available Agents             ║"
  echo "╠══════════════════════════════════════════════╣"
  echo "║  @architect       Architecture analysis     ║"
  echo "║  @ux-auditor      Usability audit           ║"
  echo "║  @bug-hunter      Bug hunting               ║"
  echo "║  @dep-scanner     Dependency & security     ║"
  echo "║  @tech-lead       Summary & recommendations ║"
  echo "╚══════════════════════════════════════════════╝"
  echo ""
  echo "Usage:"
  echo "  ./scripts/analyze.sh          Interactive TUI"
  echo "  ./scripts/analyze.sh step     Run all steps"
  echo "  ./scripts/analyze.sh en       Use English prompts"
  echo "  ./scripts/analyze.sh list     Show this help"
  exit 0
fi

if [ "$MODE" = "step" ]; then
  echo ""
  echo "═══════════════════════════════════════════════"
  echo "  Pursi — Running full analysis (5 steps)"
  echo "═══════════════════════════════════════════════"
  echo ""

  echo "▸ [1/5] Architecture analysis (@architect)..."
  opencode run "Suorita arkkitehtuurianalyysi @architect" --agent architect 2>/dev/null || \
  opencode run "Run architecture analysis @architect" --agent architect 2>/dev/null || true

  echo ""
  echo "▸ [2/5] Usability audit (@ux-auditor)..."
  opencode run "Analysoi käytettävyys @ux-auditor" --agent ux-auditor 2>/dev/null || \
  opencode run "Run usability audit @ux-auditor" --agent ux-auditor 2>/dev/null || true

  echo ""
  echo "▸ [3/5] Bug hunting (@bug-hunter)..."
  opencode run "Etsi bugeja koko projektista @bug-hunter" --agent bug-hunter 2>/dev/null || \
  opencode run "Find bugs across the project @bug-hunter" --agent bug-hunter 2>/dev/null || true

  echo ""
  echo "▸ [4/5] Dependency scan (@dep-scanner)..."
  opencode run "Skannaa riippuvuudet @dep-scanner" --agent dep-scanner 2>/dev/null || \
  opencode run "Scan dependencies @dep-scanner" --agent dep-scanner 2>/dev/null || true

  echo ""
  echo "▸ [5/5] Summary & recommendations (@tech-lead)..."
  opencode run "Tee yhteenveto ja kehitysehdotukset @tech-lead" --agent tech-lead 2>/dev/null || \
  opencode run "Summarize and recommend @tech-lead" --agent tech-lead 2>/dev/null || true

  echo ""
  echo "═══════════════════════════════════════════════"
  echo "  All 5 analyses completed!"
  echo "═══════════════════════════════════════════════"
  exit 0
fi

# Default: interactive TUI
echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║     Pursi — OpenCode Analysis Toolkit         ║"
echo "╠══════════════════════════════════════════════╣"
echo "║  @architect       Architecture               ║"
echo "║  @ux-auditor      Usability                  ║"
echo "║  @bug-hunter      Bug hunting                ║"
echo "║  @dep-scanner     Dependencies               ║"
echo "║  @tech-lead       Recommendations            ║"
echo "╚══════════════════════════════════════════════╝"
echo ""

exec opencode
