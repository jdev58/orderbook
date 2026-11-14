# Docs assets

| File | Purpose |
|------|---------|
| [performance-baseline.json](performance-baseline.json) | Frozen JMH scores used for charts |
| [jmh-throughput.svg](jmh-throughput.svg) | Absolute throughput bar chart |
| [jmh-relative.svg](jmh-relative.svg) | Speedup vs `restingSubmit` |
| [architecture-hot-path.svg](architecture-hot-path.svg) | Simplified matching / book diagram |

Charts are SVG so they stay sharp on GitHub and in the README. Regenerate with:

```powershell
powershell -File ..\scripts\generate-performance-charts.ps1
```