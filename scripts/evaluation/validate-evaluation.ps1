param(
    [string]$Dataset = "$PSScriptRoot\..\..\quality\evaluation\datasets\serviceflow-eval-200.json",
    [int]$SmokeCount = 20
)

$ErrorActionPreference = 'Stop'
$cases = Get-Content -LiteralPath $Dataset -Raw -Encoding utf8 | ConvertFrom-Json
$required = @('id', 'question', 'pageContext', 'principalType', 'expectedIntent', 'expectedProducts', 'expectedCitations', 'expectedEvents', 'requiredFacts', 'forbiddenClaims')
if ($cases.Count -ne 200) { throw "评测集必须包含 200 条，当前为 $($cases.Count) 条" }

$ids = @($cases | ForEach-Object id)
if (($ids | Sort-Object -Unique).Count -ne $cases.Count) { throw '评测集 ID 必须唯一' }
foreach ($case in $cases) {
    foreach ($field in $required) {
        if ($null -eq $case.PSObject.Properties[$field]) { throw "$($case.id) 缺少字段 $field" }
    }
    if ($case.principalType -notin @('GUEST', 'CUSTOMER', 'ADMIN')) { throw "$($case.id) 主体类型无效" }
    if ($case.expectedIntent -notin @('CHAT', 'PRODUCT_QUERY', 'KNOWLEDGE_QUERY', 'ORDER_QUERY', 'COMPLAINT')) {
        throw "$($case.id) 意图无效"
    }
}

$smoke = @($cases | Select-Object -First $SmokeCount)
"Dataset valid: $($cases.Count) cases"
"Smoke selection: $($smoke.Count) cases"
($cases | Group-Object expectedIntent | ForEach-Object { "- $($_.Name): $($_.Count)" })
