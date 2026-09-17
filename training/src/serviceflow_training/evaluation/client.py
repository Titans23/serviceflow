"""Shared streaming evaluation protocol and latency statistics."""
import json
import socket
import time
from urllib.request import Request, urlopen


def percentile(values, p):
    if not values: return None
    values=sorted(values)
    import math
    return values[max(0,math.ceil(len(values)*p)-1)]


def request_case(url, key, r, budget):
    limit=20 if r["task_type"] in {"intent","grader","rewrite"} else 90
    payload={"model":"serviceflow-qwen3-8b-base","messages":r["messages"][:2],"temperature":0.1,"top_p":1.0,"top_k":-1,"max_tokens":budget,"seed":42,"stream":True,"stream_options":{"include_usage":True}}
    started=time.monotonic()
    result={"id":r["id"],"task_type":r["task_type"],"output":"","status":"error","first_token_seconds":None,"elapsed_seconds":None,"budget_seconds":limit,"finish_reason":None,"sse_done":False,"usage":None,"error":None}
    req=Request(url+"/v1/chat/completions",data=json.dumps(payload).encode(),headers={"Authorization":"Bearer "+key,"Content-Type":"application/json"})
    try:
        with urlopen(req,timeout=limit) as response:
            for line in response:
                elapsed=time.monotonic()-started
                if elapsed>limit: raise TimeoutError("Module wall-time budget exceeded")
                if not line.startswith(b"data: "): continue
                data=line[6:].strip()
                if data==b"[DONE]": result["sse_done"]=True; break
                event=json.loads(data)
                if event.get("error"): raise ValueError("Server emitted error event")
                if event.get("usage"): result["usage"]=event["usage"]
                for choice in event.get("choices",[]):
                    content=choice.get("delta",{}).get("content") or ""
                    if content and result["first_token_seconds"] is None: result["first_token_seconds"]=elapsed
                    result["output"]+=content
                    if choice.get("finish_reason"): result["finish_reason"]=choice["finish_reason"]
        if not result["sse_done"]: raise ValueError("Missing SSE DONE")
        result["status"]="ok" if result["finish_reason"]=="stop" and result["output"] else "incomplete"
    except Exception as error:
        result["status"]="timeout" if isinstance(error,(TimeoutError,socket.timeout)) else "error"
        result["error"]=type(error).__name__+": "+str(error)
    result["elapsed_seconds"]=time.monotonic()-started
    return result
