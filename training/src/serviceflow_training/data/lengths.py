"""Measure draft input AND reference lengths; never approve or export data."""
from copy import deepcopy
import argparse
import json
from pathlib import Path
from serviceflow_training.core.contracts import ROOT, read_rows, validate, training_runtime, sha, write_json, tokenizer_hashes, length_summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=ROOT/"local-datasets/serviceflow/grader-specialist-exp-01/all.jsonl")
    parser.add_argument("--output", type=Path, default=ROOT/"runtime-data/training/grader-lengths-check.json")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Cannot overwrite an existing length report; choose a new output")
    from transformers import AutoTokenizer
    from llamafactory.data.template import TEMPLATES
    path=args.input
    rows=read_rows(path)
    summary=validate(rows,require_review=False)
    model=ROOT/"runtime-data/training/models/Qwen3-8B"
    tokenizer=AutoTokenizer.from_pretrained(model,local_files_only=True)
    template=deepcopy(TEMPLATES["qwen3"])
    template.enable_thinking=False
    template.fix_special_tokens(tokenizer)
    lengths=[]
    for row in rows:
        system,user,answer=[x["content"] for x in row["messages"]]
        source,target=template.encode_oneturn(tokenizer,[{"role":"user","content":user},{"role":"assistant","content":answer}],system=system)
        serving=tokenizer.apply_chat_template(row["messages"][:2],tokenize=True,add_generation_prompt=True,enable_thinking=False)
        assert source==serving,row["id"]
        assert tokenizer.decode(target)==answer+tokenizer.eos_token+"\n",row["id"]
        lengths.append({"id":row["id"],"split":row["split"],"input_tokens":len(source),"output_tokens":len(target),"total_tokens":len(source)+len(target)})
    report={"scope":"DRAFT_INSPECTION_ONLY_NOT_TRAINING_READY","input_sha256":sha(path),"contract":summary["contract"],"training_runtime":training_runtime(),"serving_token_parity":True,"tokenizer_files_sha256":tokenizer_hashes(model),**length_summary(lengths,4096),"lengths":lengths}
    write_json(args.output,report)
    assert not report["over_limit"]
    print(json.dumps({k:v for k,v in report.items() if k not in {"lengths","contract"}},ensure_ascii=False,indent=2))


if __name__=="__main__": main()
