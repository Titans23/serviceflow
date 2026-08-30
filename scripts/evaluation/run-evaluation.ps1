param(
    [string]$BaseUrl = 'http://localhost:8080/api',
    [string]$Dataset = "$PSScriptRoot\..\..\quality\evaluation\datasets\serviceflow-eval-200.json",
    [string]$OutputDirectory = "$PSScriptRoot\..\..\quality\evaluation\reports",
    [string]$CustomerUsername = 'customer',
    [string]$CustomerPassword = $(if ($env:SERVICEFLOW_EVAL_CUSTOMER_PASSWORD) {
        $env:SERVICEFLOW_EVAL_CUSTOMER_PASSWORD
    } else {
        'Customer123!'
    }),
    [string]$AdminUsername = 'admin',
    [string]$AdminPassword = $(if ($env:SERVICEFLOW_EVAL_ADMIN_PASSWORD) {
        $env:SERVICEFLOW_EVAL_ADMIN_PASSWORD
    } else {
        'Admin123!'
    }),
    [ValidateRange(0, 200)]
    [int]$Limit = 0,
    [switch]$Smoke
)

$ErrorActionPreference = 'Stop'
$cases = Get-Content -LiteralPath $Dataset -Raw -Encoding utf8 | ConvertFrom-Json
if ($Smoke) {
    $smokeCases = [System.Collections.Generic.List[object]]::new()
    @('PRODUCT_QUERY', 'KNOWLEDGE_QUERY', 'ORDER_QUERY', 'COMPLAINT') | ForEach-Object {
        $intent = $_
        @($cases | Where-Object expectedIntent -eq $intent | Select-Object -First 5) |
            ForEach-Object { $smokeCases.Add($_) }
    }
    $cases = @($smokeCases)
} elseif ($Limit -gt 0) {
    $cases = @($cases | Select-Object -First $Limit)
}
$results = [System.Collections.Generic.List[object]]::new()

function New-LoginHeaders([string]$Username, [string]$Password) {
    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
    $token = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" `
        -ContentType 'application/json; charset=utf-8' -Body $loginBody
    return @{ Authorization = "Bearer $($token.accessToken)" }
}

$headersByPrincipal = @{}
if (@($cases | Where-Object principalType -eq 'GUEST').Count -gt 0) {
    $guest = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/guest"
    $headersByPrincipal.GUEST = @{ Authorization = "Bearer $($guest.accessToken)" }
}
if (@($cases | Where-Object principalType -eq 'CUSTOMER').Count -gt 0) {
    $headersByPrincipal.CUSTOMER = New-LoginHeaders $CustomerUsername $CustomerPassword
}
if (@($cases | Where-Object principalType -eq 'ADMIN').Count -gt 0) {
    $headersByPrincipal.ADMIN = New-LoginHeaders $AdminUsername $AdminPassword
}

function Normalize-AnswerText([string]$Text) {
    if ($null -eq $Text) { return '' }
    return [regex]::Replace($Text, '[\s*_`#]+', '')
}

foreach ($case in $cases) {
    $principalType = if ($case.principalType) { [string]$case.principalType } else { 'GUEST' }
    $headers = $headersByPrincipal[$principalType]
    if ($null -eq $headers) {
        throw "Unsupported principalType '$principalType' in case '$($case.id)'"
    }
    $session = Invoke-RestMethod -Method Post -Uri "$BaseUrl/chat/sessions" -Headers $headers `
        -ContentType 'application/json; charset=utf-8' -Body (@{ title = "评测-$($case.id)" } | ConvertTo-Json)
    $context = if ($null -ne $case.pageContext) {
        $case.pageContext
    } elseif ($null -ne $case.pageProductId) {
        @{ productId = [long]$case.pageProductId }
    } else {
        $null
    }
    $body = @{
        message = $case.question
        clientRequestId = [guid]::NewGuid().ToString()
        pageContext = $context
    } | ConvertTo-Json -Depth 4
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-WebRequest -Method Post -Uri "$BaseUrl/chat/sessions/$($session.publicId)/messages/stream" `
        -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body -TimeoutSec 120
    $watch.Stop()
    # Spring's SSE response may omit charset; PowerShell then decodes UTF-8 bytes as Latin-1.
    $sseContent = [Text.Encoding]::UTF8.GetString(
        [Text.Encoding]::GetEncoding(28591).GetBytes($response.Content))

    $eventNames = [regex]::Matches($sseContent, '(?m)^event:([^\r\n]+)') |
        ForEach-Object { $_.Groups[1].Value.Trim() }
    $metaMatch = [regex]::Match($sseContent, '(?ms)^event:meta\r?\ndata:(\{.*?\})\r?\n\r?\n')
    $doneMatch = [regex]::Match($sseContent, '(?ms)^event:done\r?\ndata:(\{.*?\})\r?\n\r?\n')
    $errorMatch = [regex]::Match($sseContent, '(?ms)^event:error\r?\ndata:(\{.*?\})\r?\n\r?\n')
    $meta = if ($metaMatch.Success) { $metaMatch.Groups[1].Value | ConvertFrom-Json } else { $null }
    $done = if ($doneMatch.Success) { $doneMatch.Groups[1].Value | ConvertFrom-Json } else { $null }
    $errorEvent = if ($errorMatch.Success) { $errorMatch.Groups[1].Value | ConvertFrom-Json } else { $null }
    $tokenText = ([regex]::Matches($sseContent, '(?m)^data:(?!\{)(.*)$') |
        ForEach-Object { $_.Groups[1].Value }) -join ''

    $intentPass = $null -ne $meta -and $meta.intent -eq $case.expectedIntent
    $expectedCitations = @($case.expectedCitations | Where-Object { $null -ne $_ })
    if ($expectedCitations.Count -eq 0 -and $null -ne $case.expectedCitation) {
        $expectedCitations = @($case.expectedCitation)
    }
    $citationPass = $expectedCitations.Count -eq 0 -or
        ($null -ne $meta -and @($expectedCitations | Where-Object { @($meta.citations) -contains $_ }).Count -eq $expectedCitations.Count)
    $expectedEvents = @($case.expectedEvents | Where-Object { $null -ne $_ })
    if ($expectedEvents.Count -eq 0 -and $null -ne $case.expectedEvent) {
        $expectedEvents = @($case.expectedEvent)
    }
    $eventPass = $expectedEvents.Count -eq 0 -or
        @($expectedEvents | Where-Object { $eventNames -contains $_ }).Count -eq $expectedEvents.Count
    $absentEventPass = $null -eq $case.expectedAbsentEvent -or
        $eventNames -notcontains $case.expectedAbsentEvent
    $requiredFacts = @($case.requiredFacts | Where-Object { $null -ne $_ })
    if ($requiredFacts.Count -eq 0) {
        $requiredFacts = @($case.mustContainAny | Where-Object { $null -ne $_ })
    }
    $normalizedAnswer = Normalize-AnswerText $tokenText
    $factPass = $null -eq $case.mustContain -or
        $normalizedAnswer.Contains((Normalize-AnswerText ([string]$case.mustContain)))
    if ($requiredFacts.Count -gt 0) {
        $factPass = @($requiredFacts | Where-Object {
            $normalizedAnswer.Contains((Normalize-AnswerText ([string]$_)))
        }).Count -gt 0
    }
    $expectedProducts = @($case.expectedProducts | Where-Object { $null -ne $_ })
    $productPass = $expectedProducts.Count -eq 0 -or
        ($null -ne $meta -and $null -ne $meta.productIds -and
            @($expectedProducts | Where-Object { @($meta.productIds) -contains $_ }).Count -eq $expectedProducts.Count)
    $forbiddenClaims = @($case.forbiddenClaims | Where-Object { $null -ne $_ })
    $forbiddenClaimPass = @($forbiddenClaims | Where-Object {
        $normalizedAnswer.Contains((Normalize-AnswerText ([string]$_)))
    }).Count -eq 0
    $expectsError = $expectedEvents -contains 'error'
    $terminalPass = if ($expectsError) { $null -ne $errorEvent } else { $null -ne $done }
    $passed = $intentPass -and $citationPass -and $eventPass -and $absentEventPass -and
        $factPass -and $productPass -and $forbiddenClaimPass -and $terminalPass

    $results.Add([pscustomobject]@{
        id = $case.id
        principalType = $principalType
        passed = $passed
        expectedIntent = $case.expectedIntent
        actualIntent = if ($null -ne $meta) { $meta.intent } else { $null }
        citations = if ($null -ne $meta) { @($meta.citations) } else { @() }
        events = @($eventNames)
        latencyMs = $watch.ElapsedMilliseconds
        answerExcerpt = if ($tokenText.Length -gt 240) { $tokenText.Substring(0, 240) } else { $tokenText }
        checks = [ordered]@{
            intent = $intentPass
            citation = $citationPass
            event = $eventPass
            absentEvent = $absentEventPass
            requiredFact = $factPass
            product = $productPass
            forbiddenClaims = $forbiddenClaimPass
            terminal = $terminalPass
        }
    })
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$passedCount = @($results | Where-Object passed).Count
$total = $results.Count
$report = [pscustomobject][ordered]@{
    generatedAt = (Get-Date).ToString('o')
    baseUrl = $BaseUrl
    total = $total
    passed = $passedCount
    passRate = [math]::Round($passedCount * 100.0 / $total, 2)
    averageLatencyMs = [math]::Round(($results | Measure-Object latencyMs -Average).Average, 0)
    p50LatencyMs = [math]::Round(($results.latencyMs | Sort-Object)[[math]::Floor(($results.Count - 1) * 0.50)], 0)
    p95LatencyMs = [math]::Round(($results.latencyMs | Sort-Object)[[math]::Floor(($results.Count - 1) * 0.95)], 0)
    intentAccuracy = [math]::Round((@($results | Where-Object { $_.checks.intent }).Count * 100.0 / $total), 2)
    citationAccuracy = [math]::Round((@($results | Where-Object { $_.checks.citation }).Count * 100.0 / $total), 2)
    eventAccuracy = [math]::Round((@($results | Where-Object { $_.checks.event }).Count * 100.0 / $total), 2)
    productAccuracy = [math]::Round((@($results | Where-Object { $_.checks.product }).Count * 100.0 / $total), 2)
    hallucinationRate = [math]::Round((@($results | Where-Object { -not $_.checks.forbiddenClaims }).Count * 100.0 / $total), 2)
    results = $results
}
$jsonPath = Join-Path $OutputDirectory "evaluation-$timestamp.json"
$markdownPath = Join-Path $OutputDirectory "evaluation-$timestamp.md"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$lines = @(
    '# ServiceFlow 在线评测报告',
    '',
    "- 生成时间：$($report.generatedAt)",
    "- 用例：$total",
    "- 通过：$passedCount",
    "- 通过率：$($report.passRate)%",
    "- 平均端到端耗时：$($report.averageLatencyMs) ms",
    "- P50/P95：$($report.p50LatencyMs) / $($report.p95LatencyMs) ms",
    "- 意图/引用/事件准确率：$($report.intentAccuracy)% / $($report.citationAccuracy)% / $($report.eventAccuracy)%",
    "- 商品识别准确率：$($report.productAccuracy)%",
    "- 禁止声明命中率（事实幻觉代理指标）：$($report.hallucinationRate)%",
    '',
    '| 用例 | 结果 | 预期意图 | 实际意图 | 耗时(ms) |',
    '| --- | --- | --- | --- | --- |'
)
foreach ($result in $results) {
    $mark = if ($result.passed) { 'PASS' } else { 'FAIL' }
    $lines += "| $($result.id) | $mark | $($result.expectedIntent) | $($result.actualIntent) | $($result.latencyMs) |"
}
$lines | Set-Content -LiteralPath $markdownPath -Encoding utf8

$report | Select-Object total, passed, passRate, averageLatencyMs, p50LatencyMs, p95LatencyMs, intentAccuracy, citationAccuracy, eventAccuracy, productAccuracy, hallucinationRate | Format-List
"JSON report: $jsonPath"
"Markdown report: $markdownPath"
if ($passedCount -ne $total) { exit 1 }
