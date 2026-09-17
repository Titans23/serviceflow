"""Reference-based evidence diagnostics; no automatic claim of semantic truth."""
import json
from serviceflow_training.core.contracts import grader_output, require


def diagnose(gold, predictions):
    actual = {p['id']:p['output'] for p in predictions}
    require(len(actual)==len(predictions), 'Duplicate predictions')
    counts = dict(cases=0, invalid=0, correct_sufficiency=0, correct_support_set=0,
        selected_ids=0, supported_selected_ids=0, reference_support_ids=0,
        extraneous_ids=0, missed_support_ids=0, partial_cases=0, partial_exact=0,
        empty_reference_cases=0, empty_reference_with_selected_ids=0)
    for r in gold:
        if r['task_type']!='grader': continue
        require(r['id'] in actual, 'Missing grader prediction')
        truth=grader_output(r['messages'][2]['content'],r['context']['candidates'])
        try:
            value=grader_output(actual[r['id']],r['context']['candidates']); legal=True
        except (ValueError,TypeError,KeyError):
            legal=False
            try: value=json.loads(actual[r['id']])
            except ValueError: value={}
            if not isinstance(value,dict): value={}
        raw_ids=value.get('rankedChunkIds',[])
        selected=set(x for x in raw_ids if isinstance(x,str)) if isinstance(raw_ids,list) else set()
        supported=set(truth['rankedChunkIds'])
        counts['cases']+=1; counts['invalid']+=not legal
        counts['correct_sufficiency']+=legal and value['sufficient']==truth['sufficient']
        counts['correct_support_set']+=legal and selected==supported
        counts['selected_ids']+=len(selected); counts['supported_selected_ids']+=len(selected & supported)
        counts['reference_support_ids']+=len(supported)
        counts['extraneous_ids']+=len(selected-supported); counts['missed_support_ids']+=len(supported-selected)
        if not truth['sufficient'] and supported:
            counts['partial_cases']+=1
            counts['partial_exact']+=legal and value['sufficient'] is False and selected==supported
        if not supported:
            counts['empty_reference_cases']+=1
            counts['empty_reference_with_selected_ids']+=bool(selected)
    counts['reference_id_precision']=counts['supported_selected_ids']/counts['selected_ids'] if counts['selected_ids'] else None
    counts['reference_id_recall']=counts['supported_selected_ids']/counts['reference_support_ids'] if counts['reference_support_ids'] else None
    return counts
