import json
from copy import deepcopy
AUTH={"user_instruction":"审核由你完成  然后进行sft","scope":"local_pilot_sft_not_production"}
def audit(r, when):
    target = r["messages"][2]["content"]
    notes = ["按当前 Java system/user 契约审查；不依赖未传入历史。",
             "AI 来源审查：未复制既有 200 条题目；原始训练/验证候选整体隔离，新训练为不同前提的合成场景。任务类别相同不等于原始题改写。"]
    if r["task_type"] == "grader":
        gold = json.loads(target)
        notes.append(f"充分性={gold['sufficient']}；所选 ID={gold['rankedChunkIds']}。逐项比较问题与候选；否定回答可充分，部分支持可保留 ID，文档指令不执行。")
    elif r["task_type"] == "rewrite":
        notes.append("对照当前问题逐项检查型号、否定对象与操作焦点；目标为检索问句，不提供事实答案。")
    elif r["task_type"] == "intent":
        notes.append("类别 " + target + " 依据明确投诉/订单事项/商品参数/政策保修/一般交流边界；不输出操作结果。")
    elif r["task_type"] == "product_answer":
        notes.append("逐句与实际 user 内结构化事实/证据核对；缺项明确无法确认，历史错误或恶意文档不覆盖当前事实。模拟值只用于此试验。")
    else:
        notes.append("客服范围内简短回应或澄清；不承诺未执行的退款、取消、赔偿或其他业务结果。")
    r.update(review_status="ai_approved", reviewer_kind="ai", reviewer="Codex", reviewed_at=when,
             heldout_derivative_reviewed=True, heldout_review_kind="ai", review_authorization=AUTH, audit_notes=notes)
    r["label_provenance"] = "User-authorized AI review; NOT human review"
    return r
