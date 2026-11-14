# Regenerate docs/jmh-*.svg from docs/performance-baseline.json
# Usage: pwsh scripts/generate-performance-charts.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$baselinePath = Join-Path $root "docs\performance-baseline.json"
$docsDir = Join-Path $root "docs"
$data = Get-Content $baselinePath -Raw | ConvertFrom-Json

function Format-Throughput([double]$ops) {
    if ($ops -ge 1e6) { return ("{0:N2}M" -f ($ops / 1e6)) }
    if ($ops -ge 1e3) { return ("{0:N1}k" -f ($ops / 1e3)) }
    return ("{0:N0}" -f $ops)
}

$bench = @($data.benchmarks | Sort-Object -Property score -Descending)
$maxScore = ($bench | Measure-Object -Property score -Maximum).Maximum * 1.08
$colors = @("#2563eb", "#0d9488", "#7c3aed", "#ca8a04", "#dc2626")

$w = 920
$h = 420
$ml = 220
$mr = 40
$mt = 72
$chartW = $w - $ml - $mr
$rowH = 56
$chartH = $rowH * $bench.Count

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('<?xml version="1.0" encoding="UTF-8"?>')
$lines.Add("<svg xmlns=`"http://www.w3.org/2000/svg`" viewBox=`"0 0 $w $h`" width=`"$w`" height=`"$h`" role=`"img`" aria-labelledby=`"title desc`">")
$lines.Add("<title id=`"title`">JMH throughput — HFT order book</title>")
$lines.Add("<desc id=`"desc`">Horizontal bar chart of benchmark operations per second.</desc>")
$lines.Add("<rect width=`"$w`" height=`"$h`" fill=`"#0f172a`"/>")
$lines.Add("<text x=`"24`" y=`"36`" fill=`"#f8fafc`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"20`" font-weight=`"600`">JMH throughput (ops/s)</text>")
$lines.Add("<text x=`"24`" y=`"56`" fill=`"#94a3b8`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"12`">JDK $($data.jdk) · JMH $($data.jmhVersion) · $($data.options)</text>")

for ($t = 0; $t -le 4; $t++) {
    $v = $maxScore * ($t / 4)
    $x = $ml + ($chartW * $t / 4)
    $lines.Add("<line x1=`"$x`" y1=`"$mt`" x2=`"$x`" y2=`"$($mt + $chartH)`" stroke=`"#334155`" stroke-width=`"1`"/>")
    $lines.Add("<text x=`"$x`" y=`"$($mt + $chartH + 22)`" text-anchor=`"middle`" fill=`"#64748b`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"11`">$(Format-Throughput $v)</text>")
}

for ($i = 0; $i -lt $bench.Count; $i++) {
    $b = $bench[$i]
    $y = $mt + $i * $rowH + 12
    $barW = [math]::Max(4, ($b.score / $maxScore) * $chartW)
    $errW = ($b.error99 / $maxScore) * $chartW
    $cx = $ml + $barW
    $color = $colors[$i % $colors.Length]

    $lines.Add("<text x=`"$($ml - 12)`" y=`"$($y + 22)`" text-anchor=`"end`" fill=`"#e2e8f0`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"13`">$($b.label)</text>")
    $lines.Add("<rect x=`"$ml`" y=`"$y`" width=`"$barW`" height=`"32`" rx=`"4`" fill=`"$color`"/>")
    $lines.Add("<line x1=`"$($cx - $errW)`" y1=`"$($y + 16)`" x2=`"$($cx + $errW)`" y2=`"$($y + 16)`" stroke=`"#f8fafc`" stroke-width=`"2`" opacity=`"0.85`"/>")
    $label = "$(Format-Throughput $b.score) ±$(Format-Throughput $b.error99)"
    $lines.Add("<text x=`"$($ml + $barW + 8)`" y=`"$($y + 22)`" fill=`"#cbd5e1`" font-family=`"ui-monospace, Consolas, monospace`" font-size=`"12`">$label</text>")
}

$lines.Add("<text x=`"$ml`" y=`"$($h - 16)`" fill=`"#475569`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"11`">Bars: mean throughput · whiskers: ±99.9% CI · $($data.generated)</text>")
$lines.Add("</svg>")
$outThroughput = Join-Path $docsDir "jmh-throughput.svg"
[System.IO.File]::WriteAllText($outThroughput, ($lines -join "`n"))

$sortedAsc = @($data.benchmarks | Sort-Object -Property score)
$base = [double]$sortedAsc[0].score
$rel = @($data.benchmarks | Sort-Object -Property score -Descending)
$maxRel = ($rel[0].score / $base) * 1.05

$w2 = 920
$h2 = 380
$lines2 = New-Object System.Collections.Generic.List[string]
$lines2.Add('<?xml version="1.0" encoding="UTF-8"?>')
$lines2.Add("<svg xmlns=`"http://www.w3.org/2000/svg`" viewBox=`"0 0 $w2 $h2`" width=`"$w2`" height=`"$h2`" role=`"img`" aria-labelledby=`"title2`">")
$lines2.Add("<title id=`"title2`">Relative benchmark throughput</title>")
$lines2.Add("<rect width=`"$w2`" height=`"$h2`" fill=`"#fafafa`"/>")
$lines2.Add("<text x=`"24`" y=`"36`" fill=`"#0f172a`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"20`" font-weight=`"600`">Relative throughput (× vs resting submit)</text>")
$lines2.Add("<text x=`"24`" y=`"56`" fill=`"#64748b`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"12`">Normalized to slowest scenario in this suite</text>")

$barAreaW = 640
$bx = 240
$by = 80

for ($i = 0; $i -lt $rel.Count; $i++) {
    $b = $rel[$i]
    $ratio = [double]$b.score / $base
    $y = $by + $i * 52
    $bw = ($ratio / $maxRel) * $barAreaW
    $opacity = [math]::Round(1 - ($i * 0.12), 2)
    $ratioText = "{0:N1}×" -f $ratio
    $lines2.Add("<text x=`"$($bx - 12)`" y=`"$($y + 20)`" text-anchor=`"end`" fill=`"#334155`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"13`">$($b.label)</text>")
    $lines2.Add("<rect x=`"$bx`" y=`"$y`" width=`"$bw`" height=`"28`" rx=`"3`" fill=`"#2563eb`" opacity=`"$opacity`"/>")
    $lines2.Add("<text x=`"$($bx + $bw + 8)`" y=`"$($y + 20)`" fill=`"#0f172a`" font-family=`"ui-monospace, Consolas, monospace`" font-size=`"12`">$ratioText</text>")
}

$lines2.Add("<text x=`"24`" y=`"$($h2 - 16)`" fill=`"#94a3b8`" font-family=`"Segoe UI, system-ui, sans-serif`" font-size=`"11`">Baseline: resting limit submit = 1.0× · $($data.generated)</text>")
$lines2.Add("</svg>")
$outRelative = Join-Path $docsDir "jmh-relative.svg"
[System.IO.File]::WriteAllText($outRelative, ($lines2 -join "`n"))

Write-Host "Wrote $outThroughput"
Write-Host "Wrote $outRelative"