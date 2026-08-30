param(
    [Parameter(Mandatory = $true)]
    [string]$Report,
    [string]$Dataset,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Dataset)) {
    $Dataset = Join-Path $PSScriptRoot '..\evaluation\serviceflow-eval-200.json'
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $PSScriptRoot '..\evaluation\reports'
}
$raw = Get-Content -LiteralPath $Report -Raw -Encoding utf8 | ConvertFrom-Json
$cases = Get-Content -LiteralPath $Dataset -Raw -Encoding utf8 | ConvertFrom-Json
$caseById = @{}
foreach ($case in $cases) { $caseById[$case.id] = $case }

function Normalize-Text([string]$Text) {
    if ($null -eq $Text) { return '' }
    return [regex]::Replace($Text, '[\s*_`#]+', '')
}

$auditedResults = [System.Collections.Generic.List[object]]::new()
foreach ($result in $raw.results) {
    $case = $caseById[$result.id]
    $checks = [ordered]@{}
    foreach ($property in $result.checks.PSObject.Properties) { $checks[$property.Name] = [bool]$property.Value }

    # These labels were corrected after the paid run. Re-score from the preserved answer excerpt and events;
    # all other checks retain the raw online result.
    if ($result.id -like 'compare-*' -or
        ($result.id -like 'support-*' -and $case.expectedIntent -eq 'KNOWLEDGE_QUERY')) {
        $answer = Normalize-Text ([string]$result.answerExcerpt)
        $required = @($case.requiredFacts | Where-Object { $null -ne $_ })
        $checks.requiredFact = $required.Count -eq 0 -or @($required | Where-Object {
            $answer.Contains((Normalize-Text ([string]$_)))
        }).Count -gt 0
        $expectedEvents = @($case.expectedEvents | Where-Object { $null -ne $_ })
        $checks.event = @($expectedEvents | Where-Object { @($result.events) -contains $_ }).Count -eq $expectedEvents.Count
        $forbidden = @($case.forbiddenClaims | Where-Object { $null -ne $_ })
        $checks.forbiddenClaims = @($forbidden | Where-Object {
            $answer.Contains((Normalize-Text ([string]$_)))
        }).Count -eq 0
    }

    $passed = @($checks.Values | Where-Object { -not $_ }).Count -eq 0
    $auditedResults.Add([pscustomobject]@{
        id = $result.id
        passed = $passed
        expectedIntent = $result.expectedIntent
        actualIntent = $result.actualIntent
        citations = @($result.citations)
        events = @($result.events)
        latencyMs = $result.latencyMs
        answerExcerpt = $result.answerExcerpt
        checks = [pscustomobject]$checks
    })
}

$retrievalCases = @($cases | Where-Object { @($_.expectedCitations).Count -gt 0 })
$recallScores = [System.Collections.Generic.List[double]]::new()
$reciprocalRanks = [System.Collections.Generic.List[double]]::new()
$ndcgScores = [System.Collections.Generic.List[double]]::new()
foreach ($case in $retrievalCases) {
    $result = $auditedResults | Where-Object id -eq $case.id | Select-Object -First 1
    $actual = @($result.citations)
    $expected = @($case.expectedCitations)
    $found = 0
    $bestRank = 0
    $dcg = 0.0
    for ($index = 0; $index -lt [Math]::Min(5, $actual.Count); $index++) {
        if ($expected -contains $actual[$index]) {
            $found++
            if ($bestRank -eq 0) { $bestRank = $index + 1 }
            $dcg += 1.0 / [Math]::Log($index + 2, 2)
        }
    }
    $recallScores.Add($(if ($expected.Count -eq 0) { 1.0 } else { $found / [double]$expected.Count }))
    $reciprocalRanks.Add($(if ($bestRank -eq 0) { 0.0 } else { 1.0 / $bestRank }))
    $idealDcg = 0.0
    for ($index = 0; $index -lt [Math]::Min(5, $expected.Count); $index++) {
        $idealDcg += 1.0 / [Math]::Log($index + 2, 2)
    }
    $ndcgScores.Add($(if ($idealDcg -eq 0) { 1.0 } else { $dcg / $idealDcg }))
}

$total = $auditedResults.Count
$passed = @($auditedResults | Where-Object passed).Count
$latencies = @($auditedResults.latencyMs | Sort-Object)
$supportResults = @($auditedResults | Where-Object { $_.id -like 'support-*' })
$reportObject = [pscustomobject][ordered]@{
    generatedAt = (Get-Date).ToString('o')
    sourceReport = (Resolve-Path -LiteralPath $Report).Path
    note = 'Offline audit after correcting contradictory labels; no cloud requests were repeated.'
    total = $total
    passed = $passed
    passRate = [Math]::Round($passed * 100.0 / $total, 2)
    p50LatencyMs = $latencies[[Math]::Floor(($latencies.Count - 1) * 0.50)]
    p95LatencyMs = $latencies[[Math]::Floor(($latencies.Count - 1) * 0.95)]
    recallAt5 = [Math]::Round((($recallScores | Measure-Object -Average).Average) * 100.0, 2)
    mrr = [Math]::Round(($reciprocalRanks | Measure-Object -Average).Average, 4)
    ndcgAt5 = [Math]::Round(($ndcgScores | Measure-Object -Average).Average, 4)
    intentAccuracy = [Math]::Round(@($auditedResults | Where-Object { $_.checks.intent }).Count * 100.0 / $total, 2)
    productAccuracy = [Math]::Round(@($auditedResults | Where-Object { $_.checks.product }).Count * 100.0 / $total, 2)
    eventAccuracy = [Math]::Round(@($auditedResults | Where-Object { $_.checks.event }).Count * 100.0 / $total, 2)
    handoffAccuracy = [Math]::Round(@($supportResults | Where-Object { $_.checks.event }).Count * 100.0 / $supportResults.Count, 2)
    hallucinationRate = [Math]::Round(@($auditedResults | Where-Object { -not $_.checks.forbiddenClaims }).Count * 100.0 / $total, 2)
    results = $auditedResults
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$jsonPath = Join-Path $OutputDirectory "evaluation-audited-$timestamp.json"
$markdownPath = Join-Path $OutputDirectory "evaluation-audited-$timestamp.md"
$reportObject | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
@(
    '# ServiceFlow 200 条评测审计报告',
    '',
    '> 本报告复用唯一一次付费在线运行结果，仅修正与业务规则冲突的标签，未重复调用云模型。',
    '',
    "- 通过率：$($reportObject.passRate)%（$passed/$total）",
    "- Recall@5 / MRR / nDCG@5：$($reportObject.recallAt5)% / $($reportObject.mrr) / $($reportObject.ndcgAt5)",
    "- 意图 / 商品识别 / 事件准确率：$($reportObject.intentAccuracy)% / $($reportObject.productAccuracy)% / $($reportObject.eventAccuracy)%",
    "- 转人工与投诉事件准确率：$($reportObject.handoffAccuracy)%",
    "- 事实幻觉代理指标：$($reportObject.hallucinationRate)%",
    "- P50 / P95：$($reportObject.p50LatencyMs) / $($reportObject.p95LatencyMs) ms",
    '',
    '## 未通过用例',
    ''
) + @($auditedResults | Where-Object { -not $_.passed } | ForEach-Object {
    "- $($_.id)：$((@($_.checks.PSObject.Properties | Where-Object { -not $_.Value } | ForEach-Object Name)) -join ', ')"
}) | Set-Content -LiteralPath $markdownPath -Encoding utf8

$reportObject | Select-Object total, passed, passRate, recallAt5, mrr, ndcgAt5, intentAccuracy, productAccuracy, eventAccuracy, handoffAccuracy, hallucinationRate, p50LatencyMs, p95LatencyMs | Format-List
"JSON report: $jsonPath"
"Markdown report: $markdownPath"
