
# 项目主题

## ServiceFlow：高并发电商售后智能客服系统

核心场景限定在：

> **订单查询 + 物流咨询 + 退换货政策 + 商品售后 + 投诉转人工**

不要做商品推荐、支付、营销、库存预测等，范围会失控。

用户可以问：

* “我的订单怎么还没发货？”
* “这个商品拆封后还能退吗？”
* “退款一般多久能到账？”
* “订单 12345 现在在哪里？”
* “你们一直不给退款，我要投诉。”
* “刚才说的那个订单能不能取消？”

这样天然覆盖三种能力：

```text
知识问题 → RAG

业务问题 → Tool / MySQL

复杂问题 → LangGraph4j 工作流
```

---

# 一、最终技术栈

我建议直接锁死：

```text
Java 21
Spring Boot 3
Spring MVC + Virtual Threads
SSE

LangGraph4j
Spring AI / LangChain4j

MySQL 8
Redis 7
Milvus
RabbitMQ

Embedding Model
BGE-M3 / 云 Embedding

Reranker
BGE-Reranker / 云 Rerank

LLM
Qwen / OpenAI-compatible API
```

Spring Boot 目前原生支持 Java 21 Virtual Threads，通过 `spring.threads.virtual.enabled=true` 开启。这个特别适合你的场景：大量请求都在等待 LLM、Redis、MySQL、Milvus 等 I/O，而不是做 CPU 密集计算。([Home][1])

**不建议第一版上 WebFlux。**

因为 WebFlux + 各种阻塞 SDK 混用反而增加复杂度。你的目的应该是能解释：

> 在大量长时间 LLM/SSE 请求下，用 Virtual Threads 降低平台线程占用并提升并发连接承载能力。

---

# 二、不要从零开发

我会采用：

### 主底座：`spring-al-alibaba-customer`

它已经是真正的 Java 智能客服项目，并且已有：

* 意图识别
* `CHAT / RAG / DB_QUERY` 路由
* Query Rewrite
* Vector + BM25
* RRF
* Reranker
* Redis 短期记忆
* 用户画像长期记忆
* Function Calling
* SSE
* 数据库查询
* Parent-Child Chunk

所以你能省掉大量最基础的 RAG 工作。([GitHub][2])

你不是直接换 Logo，而是在它基础上进行几个核心改造。

### 辅助参考：`general-rag-system`

不要以它为主仓库，因为它的 AI Agent 部分主要是 Python/FastAPI + LangChain。

但是它特别适合你参考：

* MySQL
* Redis
* RabbitMQ
* Milvus
* MinIO
* 异步文档入库
* SSE
* 多用户
* 文档状态管理

它当前已经有完整的 `Spring Boot → RabbitMQ → Embedding → Milvus` 文档处理链路。([GitHub][3])

因此最省事的策略是：

> **Fork `spring-al-alibaba-customer`，参考 `general-rag-system` 的 Milvus/MQ 数据链路。**

---

# 三、整体系统架构

最终做成：

```text
                         ┌──────────────┐
                         │    Web UI    │
                         └──────┬───────┘
                                │
                           SSE / REST
                                │
                     ┌──────────▼─────────┐
                     │   Spring Boot 3    │
                     │ Java 21 + VThread  │
                     └──────────┬─────────┘
                                │
                         LangGraph4j
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
              ▼                 ▼                 ▼
           RAG Node         Order Tool       Escalation
              │                 │                 │
     ┌────────┴──────┐          │              工单系统
     │               │          ▼                 │
     ▼               ▼        MySQL               ▼
  Milvus          Sparse                       MySQL
Dense Search      Retrieval
     │               │
     └───────┬───────┘
             ▼
            RRF
             ▼
         Reranker
             ▼
            LLM


Redis
├── Session Memory
├── LangGraph Checkpoint
├── Rate Limiter
├── Hot Data Cache
└── Retrieval Cache

RabbitMQ
├── 文档 Embedding
├── 对话日志异步落库
└── Evaluation / Dataset Pipeline
```

---

# 四、LangGraph4j 到底负责什么

不要做七八个 Agent。

你只需要一个客服 Graph。

```text
                   START
                     │
                     ▼
               Intent Router
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
     Knowledge     Order       Complaint
        │           Tool            │
        ▼            │              ▼
     Retrieve        │        Human Escalation
        │            │              │
        ▼            │              │
      Grade          │              │
     /     \         │              │
 relevant irrelevant │              │
    │         │      │              │
    │       Rewrite  │              │
    │         │      │              │
    │      Retrieve  │              │
    │         │      │              │
    └────┬────┘      │              │
         │           │              │
         └───────────┴──────────────┘
                     │
                  Generate
                     │
                Confidence
                 /       \
              enough      low
                │          │
               END      Human
```

LangGraph4j 官方 Adaptive RAG 示例本身就采用了 retrieve、document grading、query transform、generate 这样的工作流，所以这里属于框架很自然的使用方式。([GitHub][4])

---

# 五、定义 4 种用户意图就够

建议：

```java
CHAT
KNOWLEDGE_QUERY
ORDER_QUERY
COMPLAINT
```

例如：

### KNOWLEDGE_QUERY

> “退款到账要多久？”

走：

```text
RAG
```

### ORDER_QUERY

> “订单 123123 到哪了？”

走：

```text
Tool → MySQL
```

### COMPLAINT

> “我要投诉你们。”

走：

```text
Agent → 保存工单 → 转人工
```

### CHAT

> “谢谢。”

直接：

```text
LLM
```

这样不会过度设计。

---

# 六、RAG 怎么设计

第一版不要 GraphRAG。

就做一个扎实的 Advanced RAG。

## 入库

```text
售后政策
退换货规则
物流规则
退款规则
商品 FAQ
客服 SOP
        │
        ▼
    Document Parser
        │
        ▼
      Chunk
        │
        ▼
     Embedding
        │
        ▼
      Milvus
```

元数据至少：

```text
document_id
chunk_id
title
category
content
source
version
embedding
```

---

# 七、检索链路

最终：

```text
Query
 │
 ├─────────────► Dense Search
 │                   │
 │                 Milvus
 │
 └─────────────► Keyword/Sparse
                     │
              BM25 / Sparse
                     │
        ┌────────────┘
        ▼
       RRF
        │
      Top 20
        │
        ▼
     Reranker
        │
      Top 5
        │
        ▼
       LLM
```

Milvus 当前 Java SDK 本身支持 `hybridSearch()` 和 reranking，多向量/混合查询不需要自己造一个向量引擎。([米尔vus][5])

HNSW 很适合你的规模，因为其特点就是高召回、低延迟，但代价是更高内存占用；你还可以通过 `M`、`efConstruction`、`ef` 做检索质量/性能实验。([米尔vus][6])

---

# 八、MySQL 的角色

这里绝对不要只拿 MySQL 保存用户名密码。

## 业务数据

```text
user
customer

product
order
order_item
payment

ticket

chat_session
chat_message

feedback

training_sample
```

---

# 九、MySQL 可以做到的高级点

### 1. 联合索引

订单典型查询：

```sql
SELECT *
FROM orders
WHERE customer_id = ?
  AND status = ?
ORDER BY create_time DESC
LIMIT 20;
```

设计：

```text
(customer_id, status, create_time)
```

然后真正跑：

```text
EXPLAIN ANALYZE
```

做优化前后对比。

---

### 2. 幂等订单操作

比如：

> “取消订单。”

用户重复发送两次不能取消两次。

使用：

```text
request_id
UNIQUE(request_id)
```

或者业务幂等表。

---

### 3. 乐观锁

订单：

```text
version
```

执行取消：

```sql
UPDATE orders
SET status = 'CANCELLED',
    version = version + 1
WHERE id = ?
  AND status = 'PENDING'
  AND version = ?;
```

避免：

```text
Agent A：退款
Agent B：发货
```

发生状态覆盖。

---

### 4. 对话数据沉淀

这对以后微调非常重要：

```text
chat_message

id
session_id
user_id
role
content
intent
model
latency
created_at
```

额外存：

```text
retrieved_document_ids

tool_call

resolved

human_escalated

user_feedback
```

这些就是你以后的训练资产。

---

# 十、Redis 不只是 Cache

建议 Redis 只做四项。

## ① Session Memory

```text
chat:memory:{sessionId}
```

例如最近：

```text
20 turns
```

TTL：

```text
30 min
```

---

## ② LangGraph Checkpoint

LangGraph4j 官方目前已经有 Redis Saver，并提供：

* Redisson
* checkpoint
* TTL
* atomic batch
* thread management

所以 Graph State 完全可以直接存在 Redis。([GitHub][7])

例如：

```text
customerId
sessionId
intent
orderId
retrievedDocs
retryCount
currentNode
```

服务实例切换后仍然能恢复状态。

---

# 十一、Redis 限流是高并发重点

设计：

```text
用户级：
30 req/min

IP：
100 req/min

LLM 模型：
100 concurrent requests
```

使用：

```text
Redis ZSET
+
Lua
+
Sliding Window
```

Redis 官方文档也明确把 Redis 推荐为跨实例 API Rate Limiter，可以用 String、Sorted Set、Lua 实现 fixed/sliding/token bucket，并通过 Lua 保证 read-decide-update 原子性。([Redis][8])

面试可以讲：

> 本地 RateLimiter 在多实例环境下无法共享状态，因此采用 Redis 实现全局限流。

---

# 十二、Redis Retrieval Cache

不要缓存整个最终答案。

缓存：

```text
retrieval result
```

Key：

```text
rag:retrieval:
{kbVersion}:
{queryHash}
```

例如：

```text
退款多久到账？
```

第一次：

```text
Milvus
→ RRF
→ Rerank
```

第二次：

```text
Redis
```

知识库发生更新：

```text
kbVersion++
```

旧 cache 自动失效。

这样缓存一致性很漂亮。

---

# 十三、高并发才是这个项目最值得展示的部分

重点不是：

> “我用了 Redis，所以高并发。”

而是整个链路：

```text
Client
 │
 ▼
Nginx
 │
 ▼
Spring Boot Cluster
 │
 ▼
Virtual Threads
 │
 ├── Redis
 │
 ├── MySQL
 │
 ├── Milvus
 │
 └── LLM
```

---

# 十四、必须做 Bulkhead

最大的瓶颈通常不是 Java。

而是：

> **LLM API。**

假设 Java 能接：

```text
2000 concurrent SSE
```

但模型 API 只能处理：

```text
100 concurrent requests
```

你必须做：

```text
Semaphore / Bulkhead

MAX_LLM_CONCURRENCY = 100
```

超过：

```text
排队
```

或者：

```text
429 / degrade
```

否则：

```text
1000 个 Virtual Thread
        ↓
1000 个 LLM Request
        ↓
LLM Provider 被打爆
```

这才是真正值得面试讲的高并发设计。

---

# 十五、降级策略

设计三层。

正常：

```text
RAG
+
Reranker
+
LLM
```

Reranker 超时：

```text
RAG
+
RRF
+
LLM
```

Embedding / Milvus 出问题：

```text
FAQ Cache
```

LLM 满载：

```text
客服繁忙
+
创建异步工单
```

这样才叫：

> 高可用客服系统。

---

# 十六、RabbitMQ 做什么

别过度使用。

只处理异步工作。

### 文档入库

```text
管理员上传知识文档
        │
        ▼
      MySQL
        │
        ▼
    RabbitMQ
        │
        ▼
Document Consumer
        │
 Parse → Chunk
        │
 Embedding
        │
      Milvus
```

---

### 对话日志

用户对话主链路：

```text
Answer
 ↓
Return SSE
```

不要等待十几个日志表写完。

异步：

```text
ChatLogEvent
     ↓
RabbitMQ
     ↓
MySQL
```

这样减少主链路延迟。

---

# 十七、最重要的：数据怎么解决

你需要三类数据。

## A. MySQL 业务数据

直接使用：

### Olist Brazilian Ecommerce

公开数据包含约 **10 万真实匿名化电商订单**，有：

* customers
* orders
* order items
* payments
* products
* sellers
* reviews

非常适合直接导入你的 MySQL。([Kaggle][9])

所以你的订单 Tool 不再是假数据。

---

# 十八、客服对话数据

### E-commerce Dialogue Corpus

这个特别重要。

公开训练集：

> **1,000,000 session-response pairs**

验证：

> 10,000

测试：

> 10,000

平均约：

> 5.51 turns/session

来源于淘宝真实电商客服会话。([GitHub][10])

这已经足够支撑你未来真正做客服领域 LoRA。

---

# 十九、进一步的客服后训练数据

### DianJin-CSC

目前公开约：

> **13,087 rows**

专门研究 Customer Support Conversation。

其中：

* CSConv：真实客服对话经过改写/标注；
* RoleCS：面向客服策略训练的数据；
* 强调问题解决、专业沟通和客服策略。

非常适合后续训练：

> “如何像专业客服一样回答。”

([Hugging Face][11])

---

# 二十、Tool / RAG 后训练数据也有

### Ecom Chatbot Finetuning Dataset

目前：

> **40,098 条**

而且它直接分：

```text
A → Tool Calling

B → RAG

C → Escalation / Edge Cases
```

字段还有：

```text
history
prompt
context
tools
retrieved_docs
response
```

这几乎就是你未来自己要生成的 Agent 训练格式。([Hugging Face][12])

不过它主要是英文，所以：

> 当辅助数据，不要作为中文客服的核心训练集。

---

# 二十一、你的数据体系最后变成

```text
                    Data
                     │
      ┌──────────────┼──────────────┐
      │              │              │
      ▼              ▼              ▼
    Olist          ECD Corpus      DianJin
10万订单           100万对话       1.3万
      │              │              │
      ▼              ▼              ▼
    MySQL           SFT           SFT/Eval
      │
      │
      ├──────────────┐
      │              │
      ▼              ▼
 Order Tool       Customer AI
```

另外：

```text
FAQ / Policy
      ↓
Milvus
      ↓
RAG
```

---

# 二十二、后续微调怎么衔接

第一阶段项目不要微调。

先用：

```text
Qwen Instruct
+
RAG
+
Tools
```

让系统跑起来。

第二阶段：

使用：

```text
E-Commerce Dialogue Corpus
+
DianJin
+
Ecom Chatbot Dataset
```

进行：

```text
Qwen
 ↓
LoRA / QLoRA
 ↓
Customer Service SFT
```

---

# 二十三、训练的不是“知识”

这个理念一定要记住。

不要把：

> “退款需要 3-5 天”

训练进模型。

这是知识：

```text
RAG
```

应该训练：

> 用户情绪激动的时候怎么回答。

> 什么情况应该请求订单号。

> 什么情况下调用 order_status 工具。

> 什么情况下需要转人工。

这些属于：

```text
行为 / Policy / Style
```

才应该 Fine-tune。

---

# 二十四、你自己的数据飞轮

系统从第一天就记录：

```text
user_query

intent

history

retrieved_docs

retrieval_score

rerank_score

tool_call

tool_result

assistant_answer

user_feedback

human_escalated

resolved
```

然后：

```text
线上日志
   ↓
数据清洗
   ↓
脱敏
   ↓
成功解决样本
   ↓
SFT Dataset
```

以后你就可以说：

> 建立从在线客服交互 → 数据采集 → 筛选 → SFT → 模型评测 → 再部署的数据闭环。

这个对 AI 应用岗位特别有价值。

---

# 二十五、SFT 以后还可以继续 DPO

如果你拥有：

```text
用户问题

Answer A 👍
Answer B 👎
```

可以转：

```json
{
  "prompt": "...",
  "chosen": "...",
  "rejected": "..."
}
```

然后做：

```text
DPO
```

所以系统的数据表一开始就加：

```text
feedback

👍
👎
human_revision
resolved
```

为以后铺路。

---

# 二十六、RAG 评测

一定要做。

至少：

```text
Recall@5
Recall@10

MRR@10
nDCG@10
```

比较：

| Pipeline        | Recall@10 | MRR | P95 |
| --------------- | --------: | --: | --: |
| Dense           |        实测 |  实测 |  实测 |
| Sparse          |        实测 |  实测 |  实测 |
| Hybrid RRF      |        实测 |  实测 |  实测 |
| Hybrid + Rerank |        实测 |  实测 |  实测 |

数字全部自己跑。

不要预先编。

---

# 二十七、Agent 评测

例如准备：

```text
500 条 Test Case
```

包括：

```text
知识问题
订单问题
投诉问题
上下文问题
无答案问题
```

指标：

```text
Intent Accuracy

Tool Selection Accuracy

Tool Argument Accuracy

RAG Hit Rate

Answer Accuracy

Escalation Accuracy

Hallucination Rate
```

---

# 二十八、高并发压测

这是项目最重要的一张表。

使用：

```text
Gatling
```

或者：

```text
JMeter
```

模拟：

```text
100
300
500
1000
2000
```

并发 SSE Session。

记录：

```text
QPS

P50
P95
P99

TTFT
Time-to-Final

CPU

Memory

Platform Thread Count

Virtual Thread Count

MySQL Connection Pool

Redis QPS

Milvus P95

LLM Concurrency

Error Rate
```

---

# 二十九、必须做一个对照实验

非常适合简历。

```text
传统 Platform Thread
             VS
Virtual Threads
```

相同：

```text
500 concurrent sessions
```

比较：

```text
Thread Count
Memory
P95
Throughput
```

这样你才有资格在简历写：

> Java 高并发。

而不是因为用了 Redis 就称自己高并发。

---

# 三十、数据库规模建议

没必要故意造千万数据。

第一版：

```text
订单        10 万+
订单详情    20~50 万
对话        100 万+
Knowledge Chunk 5~20 万
```

已经足够跑：

```text
MySQL Index
Redis
Milvus
并发
RAG Benchmark
```

---

# 三十一、Milvus 可以专门做一次 HNSW 实验

测试：

```text
M
efConstruction
ef
```

对：

```text
Recall
Latency
Memory
```

的影响。

Milvus 官方明确说明 `ef` 增大会提高找到近邻的概率，但会增加搜索时间，而 HNSW 的优点是高精度、低延迟，代价是较高内存占用。([米尔vus][6])

简历能多一个非常扎实的向量数据库技术点。

---

# 三十二、第一版千万别做这些

砍掉：

```text
❌ GraphRAG
❌ Knowledge Graph
❌ 多 Agent
❌ MCP
❌ NL2SQL
❌ Voice
❌ OCR
❌ 商品推荐
❌ 支付
❌ Kubernetes
❌ 微服务拆分
❌ Elasticsearch
```

这些以后都可以加。

但第一版一定不要。

---

# 三十三、项目仓库最终结构

建议：

```text
serviceflow/

├── serviceflow-web
│
├── serviceflow-server
│   │
│   ├── auth
│   ├── customer
│   ├── order
│   ├── ticket
│   │
│   ├── chat
│   │
│   ├── agent
│   │   ├── graph
│   │   ├── node
│   │   ├── state
│   │   └── tool
│   │
│   ├── rag
│   │   ├── retrieval
│   │   ├── rerank
│   │   ├── embedding
│   │   └── ingestion
│   │
│   ├── memory
│   ├── limiter
│   └── evaluation
│
├── scripts
│   ├── import-olist
│   ├── import-dialog
│   └── benchmark
│
├── datasets
│
└── docker-compose.yml
```

一开始可以保持**单体 Spring Boot**。

不要拆微服务。

高并发 ≠ 微服务。

---

# 三十四、项目开发顺序

我建议严格这样做：

### V1：客服能跑

```text
Spring Boot
MySQL
Redis
LLM
SSE
```

实现：

```text
聊天
订单查询
```

---

### V2：加入 RAG

```text
Milvus
Embedding
Chunking
Dense Search
Reranker
```

实现：

> 退换货政策问答。

---

### V3：Hybrid RAG

```text
Dense
+
Sparse
+
RRF
+
Reranker
```

开始 Benchmark。

---

### V4：LangGraph4j

加入：

```text
Intent
Retrieve
Grade
Rewrite
Tool
Human
```

---

### V5：Java 高并发

加入：

```text
Virtual Threads
Redis Rate Limiter
Bulkhead
Cache
Timeout
Fallback
```

做 Gatling/JMeter。

---

### V6：训练数据闭环

加入：

```text
feedback
human_revision
resolved
training_sample
```

然后跑一次：

```text
LoRA SFT
```

即可。

---

# 最后项目真正的核心卖点

不要宣传成：

> 一个智能客服系统。

而应该是：

> **一个面向高并发电商售后场景的 Java Agentic RAG 系统。**

技术故事非常完整：

```text
                ServiceFlow
                     │
       ┌─────────────┼────────────┐
       │             │            │
Java Backend        RAG          Agent
       │             │            │
Virtual Thread     Milvus     LangGraph4j
Redis              Hybrid        Tool
MySQL              RRF           Retry
RabbitMQ           Rerank        Human
       │             │            │
       └─────────────┼────────────┘
                     │
                 Data Flywheel
                     │
                  SFT/DPO
```

而且所有组件都有理由存在。

**我最终建议的实施组合就是：**

> **Fork `spring-al-alibaba-customer` → Java 21 + Virtual Threads → PostgreSQL 改 MySQL → pgvector 改 Milvus → 加 LangGraph4j → Redis 做 Memory/Checkpoint/限流/Cache → Olist 导入业务数据 → E-commerce Dialogue + DianJin 留作 SFT → 最后做 RAG Benchmark + 100~2000 并发压测。**

这套已经足够成为一个完整、可演示、可压测、可继续做模型后训练的核心简历项目。

[1]: https://docs.spring.io/spring-boot/appendix/application-properties/?utm_source=chatgpt.com "Common Application Properties :: Spring Boot"
[2]: https://github.com/yuquanquan/spring-al-alibaba-customer?utm_source=chatgpt.com "GitHub - yuquanquan/spring-al-alibaba-customer: 基于 Spring AI Alibaba 架构的企业级智能客服系统 | RAG + 混合检索（向量+全文）| PostgreSQL pgvector + tsvector | 支持多格式文档处理与意图识别 · GitHub"
[3]: https://github.com/upupmake/general-rag-system?utm_source=chatgpt.com "GitHub - upupmake/general-rag-system: A RAG (Retrieval-Augmented Generation) knowledge base system with Vue.js frontend, Spring Boot backend, and FastAPI LLM service. · GitHub"
[4]: https://github.com/langgraph4j/langgraph4j-examples/blob/main/langchain4j/adaptive-rag/src/main/java/dev/langchain4j/adaptiverag/AdaptiveRag.java?utm_source=chatgpt.com "langgraph4j-examples/langchain4j/adaptive-rag/src/main/java/dev/langchain4j/adaptiverag/AdaptiveRag.java at main · langgraph4j/langgraph4j-examples · GitHub"
[5]: https://milvus.io/api-reference/java/v2.5.x/v2/Vector/hybridSearch.md?utm_source=chatgpt.com "hybridSearch() - Milvus java sdk v2.5.x/v2/Vector"
[6]: https://milvus.io/docs/hnsw.md?utm_source=chatgpt.com "HNSW | Milvus Documentation"
[7]: https://github.com/langgraph4j/langgraph4j/blob/main/langgraph4j-redis-saver/README.md?utm_source=chatgpt.com "langgraph4j/langgraph4j-redis-saver/README.md at main · langgraph4j/langgraph4j · GitHub"
[8]: https://redis.io/docs/latest/develop/use-cases/rate-limiter/?utm_source=chatgpt.com "Redis rate limiter | Docs"
[9]: https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce?utm_source=chatgpt.com "Brazilian E-Commerce Public Dataset by Olist"
[10]: https://github.com/cooelf/DeepUtteranceAggregation?utm_source=chatgpt.com "GitHub - cooelf/DeepUtteranceAggregation: Modeling Multi-turn Conversation with Deep Utterance Aggregation (COLING 2018) · GitHub"
[11]: https://huggingface.co/datasets/DianJin/DianJin-CSC-Data?utm_source=chatgpt.com "DianJin/DianJin-CSC-Data · Datasets at Hugging Face"
[12]: https://huggingface.co/datasets/rescommons/Ecom-Chatbot-Finetuning-Dataset?utm_source=chatgpt.com "rescommons/Ecom-Chatbot-Finetuning-Dataset · Datasets at Hugging Face"
