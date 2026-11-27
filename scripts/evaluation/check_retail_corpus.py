"""Read back the two imported catalogs, their active sections, and real local vector hits."""
import json
import re
import subprocess
from collections import defaultdict
from pathlib import Path

from eval_v2 import ROOT, read_json

COMPOSE = ["docker", "compose", "-p", "serviceflow-retail-corpus", "-f",
           str(ROOT / "deploy/retail-corpus-compose.yml"), "exec", "-T"]
COLLECTION = "retail_manuals_bge_zh_v15"


def run(args, body):
    proc = subprocess.run(COMPOSE + args, input=body.encode(), capture_output=True,
                          timeout=120, check=True)
    return proc.stdout.decode("utf-8")


def mysql(sql):
    command = ('exec env MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" '
               '--default-character-set=utf8mb4 --batch --raw --skip-column-names '
               'serviceflow_eval_retail_corpus')
    return [json.loads(line) for line in run(["mysql", "sh", "-c", command], sql).splitlines()]


def post(url, payload):
    raw = run(["milvus", "curl", "--fail", "--silent", "--show-error", "-H",
               "Content-Type: application/json", "--data-binary", "@-", url], json.dumps(payload))
    response = json.loads(raw)
    if response.get("code", 0) != 0:
        raise ValueError(f"Local service returned code {response['code']}")
    return response["data"]


def check():
    products = {p["sku"]: p for p in mysql("""
        SELECT JSON_OBJECT('id',id,'sku',sku,'name',name,'brand',brand,'model',model,
          'category',category,'specs',specs,'listPrice',list_price,'saleStatus',sale_status)
        FROM product""")}
    versions = {v["title"]: v for v in mysql("""
        SELECT JSON_OBJECT('title',d.title,'version',d.active_version_id,'product',d.product_id,
          'status',v.status) FROM knowledge_document d
        LEFT JOIN knowledge_document_version v ON v.id=d.active_version_id""")}
    expected = {}
    bindings = {}
    catalog_skus = set()
    for name in ["retail-v1.1", "retail-official-v1"]:
        package = ROOT / "data" / name
        for row in read_json(package / "catalog.json")["products"]:
            actual = products[row["sku"]]
            if any(actual[k] != v for k, v in row.items()):
                raise ValueError(f"Catalog mismatch: {row['sku']}")
            catalog_skus.add(row["sku"])
        for doc in read_json(package / "manifest.json")["documents"]:
            for sku in doc["skus"] or [None]:
                title = doc["title"] + (f"/{sku}" if sku else "")
                v = versions[title]
                product_id = products[sku]["id"] if sku else None
                if v["status"] != "READY" or v["product"] != product_id:
                    raise ValueError(f"Inactive or misbound document: {title}")
                expected[v["version"]] = {sec["id"] for sec in doc["sections"]}
                bindings[v["version"]] = (product_id if sku else -1, Path(doc["file"]).name)

    milvus = "http://localhost:19530/v2/vectordb/entities/"
    scope = "documentVersionId in " + json.dumps(sorted(expected))
    rows = []
    while True:
        page = post(milvus + "query", {"collectionName": COLLECTION, "filter": scope,
                    "limit": 500, "offset": len(rows), "consistencyLevel": "Strong",
                    "outputFields": ["documentVersionId", "productId", "source", "content"]})
        rows.extend(page)
        if len(page) < 500:
            break
    found = defaultdict(set)
    for row in rows:
        version = int(row["documentVersionId"])
        if (int(row["productId"]), row["source"]) != bindings[version]:
            raise ValueError(f"Wrong SKU or source file in Milvus for version {version}")
        found[version].update(re.findall(r"^##\s+([^：\s]+)：", row["content"], re.M))
    if dict(found) != expected:
        raise ValueError("Stored sections differ from the active package sections")

    probes = [
        ("PINE64-PINEPHONE-BETA-2-16", "micro-SIM 和 microSD 卡分别插在上层还是下层？", "cards"),
        ("S76-PANG15", "Pangolin pang15 的内置屏幕分辨率和刷新率是多少？", "display"),
        ("HUAWEI-FREEBUDS-6", "FreeBuds 6 怎么用手势调节音量？", "volume"),
        ("S76-LEMP13", "How do I replace the CMOS battery in a Lemur Pro lemp13?",
         "repairs-replacing-the-cmos-battery"),
    ]
    hits = {}
    for sku, question, section in probes:
        vector = post("http://embedding:80/v1/embeddings",
                      {"model": "BAAI/bge-small-zh-v1.5", "input": [question]})[0]["embedding"]
        if len(vector) != 512:
            raise ValueError("Unexpected embedding dimension")
        result = post(milvus + "search", {"collectionName": COLLECTION, "annsField": "denseVector",
                      "data": [vector], "limit": 5, "filter": scope + f" and productId == {products[sku]['id']}",
                      "searchParams": {"metricType": "COSINE", "params": {"ef": 64}},
                      "outputFields": ["content"]})
        rank = next((i for i, hit in enumerate(result, 1) if f"## {section}：" in hit["content"]), None)
        if rank is None:
            raise ValueError(f"{sku}/{section} missing from top five")
        hits[sku] = rank
    return {"catalogSkus": len(catalog_skus), "activeDocuments": len(expected),
            "storedChunks": len(rows), "denseTop5Ranks": hits}


if __name__ == "__main__":
    print(json.dumps(check(), ensure_ascii=False, indent=2))
