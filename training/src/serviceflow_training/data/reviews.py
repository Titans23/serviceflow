"""Apply explicit human decisions to a draft file, preserving review lineage.

Pending decisions never become approvals. Does not train, export, or promote locks.
"""
import argparse
from copy import deepcopy
from datetime import datetime
import json
from pathlib import Path
from serviceflow_training.core.contracts import read_rows, require, sha, validate, write_json


def apply(drafts, decisions):
    validate(drafts,require_review=False)
    by_id={r["id"]:r for r in drafts}
    require(len({d["id"] for d in decisions})==len(decisions),"重复审核 ID")
    require({d["id"] for d in decisions}==set(by_id),"审核 ID 必须与该批草稿完整对应")
    output=[]
    exclusions=[]
    for d in decisions:
        original=by_id[d["id"]]
        require(d.get("decision") in {"pending","reject","approve","revise"},"未知审核决定")
        if d["decision"]=="pending":
            exclusions.append({"id":d["id"],"decision":"pending"})
            continue
        reviewer=d.get("reviewer","").strip()
        require(reviewer and reviewer.casefold() not in {"codex","ai","assistant","agent","auto","chatgpt"},"必须填写真实人工审核者，不接受自动审核身份")
        when=d.get("reviewed_at","")
        parsed=datetime.fromisoformat(when)
        require(parsed.tzinfo is not None,"审核时间需包含时区")
        if d["decision"]=="reject":
            require(original["split"]!="test","不能从固定基线删除难例；应修订标签或建立新测试版本")
            require(isinstance(d.get("notes"),str) and d["notes"].strip(),"拒绝时记录原因")
            exclusions.append({"id":d["id"],"decision":"reject","reviewer":reviewer,"reviewed_at":when,"notes":d["notes"]})
            continue
        require(d.get("heldout_derivative_reviewed") is True,"需人工完成同源/派生泄漏审核")
        r=deepcopy(original)
        corrected=d.get("corrected_output")
        if d["decision"]=="revise":
            require(isinstance(corrected,str) and corrected.strip(),"修订需要完整 corrected_output")
            r["messages"][2]["content"]=corrected
        else:
            require(corrected is None,"approve 不可夹带答案修改，请使用 revise")
        r.update(review_status="approved",reviewer=reviewer,reviewed_at=when,heldout_derivative_reviewed=True)
        r["review_lineage"]={"original_target":original["messages"][2]["content"],"decision":d["decision"],"notes":d.get("notes","")}
        if "corrected_checks" in d:
            require(d["decision"]=="revise","修改 checks 需要 revise")
            r["checks"]=d["corrected_checks"]
        output.append(r)
    # A partially reviewed test suite must not silently become an easier metric denominator.
    test_ids={r["id"] for r in drafts if r["split"]=="test"}
    approved_test={r["id"] for r in output if r["split"]=="test"}
    require(not approved_test or approved_test==test_ids,"固定测试集需完整审核，不能只评分已审核的子集")
    if output: validate(output)
    return output,exclusions


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--draft",required=True,type=Path)
    parser.add_argument("--decisions",required=True,type=Path)
    parser.add_argument("--expected-draft-sha256",required=True)
    parser.add_argument("--out",required=True,type=Path)
    args=parser.parse_args()
    require(sha(args.draft)==args.expected_draft_sha256,"草稿在审核后发生变化")
    require(not args.out.exists(),"不能覆盖已有审核记录")
    output,exclusions=apply(read_rows(args.draft),read_rows(args.decisions))
    require(output,"没有任何经人工批准的记录，不产生空训练文件")
    args.out.mkdir(parents=True)
    path=args.out/"reviewed.jsonl"
    path.write_text("".join(json.dumps(r,ensure_ascii=False)+"\n" for r in output),encoding="utf-8")
    write_json(args.out/"review-manifest.json",{"draft_sha256":sha(args.draft),"decisions_sha256":sha(args.decisions),"reviewed_sha256":sha(path),"approved_count":len(output),"excluded":exclusions,"training_exported":False,"baseline_promoted":False})
    print(path)


if __name__=="__main__": main()
