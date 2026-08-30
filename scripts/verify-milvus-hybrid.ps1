param(
    [string]$MilvusUrl = 'http://localhost:19530',
    [string]$Collection = 'serviceflow_chunks',
    [string]$Output = "$PSScriptRoot\..\evaluation\reports\milvus-hybrid-verification.json"
)

$ErrorActionPreference = 'Stop'
function Invoke-Milvus([string]$Path, [object]$Body) {
    $response = Invoke-RestMethod -Uri "$MilvusUrl$Path" -Method Post -ContentType 'application/json' `
        -Body ($Body | ConvertTo-Json -Depth 12 -Compress) -TimeoutSec 20
    if ($response.code -ne 0) { throw "Milvus $Path failed with code $($response.code)" }
    return $response
}

$collections = Invoke-Milvus '/v2/vectordb/collections/list' @{ dbName = 'default' }
if (@($collections.data) -notcontains $Collection) { throw "Collection $Collection does not exist" }

$seed = Invoke-Milvus '/v2/vectordb/entities/query' @{
    collectionName = $Collection
    filter = 'chunkId != ""'
    limit = 1
    outputFields = @('chunkId', 'documentVersionId', 'documentType', 'productId', 'content', 'denseVector')
}
$row = @($seed.data)[0]
if ($null -eq $row -or @($row.denseVector).Count -ne 1024) { throw 'Expected one 1024-dimensional seed vector' }

$query = ([string]$row.content).Substring(0, [Math]::Min(80, ([string]$row.content).Length))
$activeFilter = "documentVersionId == $($row.documentVersionId)"
$hybrid = Invoke-Milvus '/v2/vectordb/entities/hybrid_search' @{
    collectionName = $Collection
    search = @(
        @{
            data = @(,@($row.denseVector))
            annsField = 'denseVector'
            limit = 20
            filter = $activeFilter
            searchParams = @{ metricType = 'COSINE'; params = @{ ef = 64 } }
        },
        @{
            data = @($query)
            annsField = 'sparseVector'
            limit = 20
            filter = $activeFilter
        }
    )
    rerank = @{ strategy = 'rrf'; params = @{ k = 60 } }
    limit = 20
    outputFields = @('chunkId', 'documentVersionId', 'documentType', 'productId')
}
if (@($hybrid.data).Count -eq 0) { throw 'Hybrid search returned no active-version rows' }
if (@($hybrid.data | Where-Object documentVersionId -ne $row.documentVersionId).Count -gt 0) {
    throw 'Hybrid search escaped the active-version filter'
}

$missing = Invoke-Milvus '/v2/vectordb/entities/hybrid_search' @{
    collectionName = $Collection
    search = @(
        @{
            data = @(,@($row.denseVector))
            annsField = 'denseVector'
            limit = 5
            filter = 'documentVersionId == -999'
            searchParams = @{ metricType = 'COSINE'; params = @{ ef = 64 } }
        },
        @{
            data = @($query)
            annsField = 'sparseVector'
            limit = 5
            filter = 'documentVersionId == -999'
        }
    )
    rerank = @{ strategy = 'rrf'; params = @{ k = 60 } }
    limit = 5
    outputFields = @('chunkId')
}
if (@($missing.data).Count -ne 0) { throw 'Inactive-version filter should return no rows' }

$report = [ordered]@{
    generatedAt = (Get-Date).ToString('o')
    collection = $Collection
    vectorDimensions = @($row.denseVector).Count
    seedChunkId = $row.chunkId
    activeVersionId = $row.documentVersionId
    hybridResultCount = @($hybrid.data).Count
    topChunkId = @($hybrid.data)[0].chunkId
    inactiveVersionResultCount = @($missing.data).Count
    checks = [ordered]@{
        collectionExists = $true
        denseSearch = $true
        sparseBm25Search = $true
        rrfFusion = $true
        activeVersionFilter = $true
    }
}
$directory = Split-Path -Parent $Output
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $Output -Encoding utf8
$report | ConvertTo-Json -Depth 6
