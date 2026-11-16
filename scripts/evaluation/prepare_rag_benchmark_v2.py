"""Versioned label review and 40 never-executed questions; never overwrite v1."""
import argparse
from collections import Counter
import copy
import hashlib
import json
import re

from eval_v2 import ROOT, read_json
from prepare_rag_benchmark import PACKAGE, DESTINATION as V1, source_sections

DESTINATION = ROOT / 'quality/evaluation/datasets/rag-retrieval-v2.json'
MODES = ['BM25', 'DENSE', 'HYBRID', 'HYBRID_RERANK']

# Review is based on the question and source, not on accepting every previous model selection.
QUESTION_EDITS = {
    'knowledge-mate70-camera': 'Mate 70 标注的照片像素和最高 4K 录像，是否表示所有拍摄模式都按相同规格输出？',
    'knowledge-mate70-pro-plus-photo': 'Mate 70 Pro+ 标称的相机像素是否表示每一种拍摄模式都输出相同像素？',
    'knowledge-nova14-pro-camera': 'nova 14 Pro 不同拍摄模式的照片像素和视频输出规格是否一定相同？',
    'knowledge-nova14-ultra-battery': 'nova 14 Ultra 电池的典型容量和额定容量分别是多少，能否自行拆卸电池？',
    'knowledge-nova14-battery': 'nova 14 电池的典型容量与额定容量是多少，电池是否可自行拆卸？',
    'knowledge-nova14-pro-battery': 'nova 14 Pro 电池的典型容量与额定容量有何区别，电池是否内置不可拆卸？',
    'knowledge-nova14-storage': 'nova 14 的机身存储有哪些容量，可用空间是否与标称容量相同？',
    'knowledge-mate-x6-storage': 'Mate X6 标准版和典藏版的 RAM 与机身存储各有哪些组合，支持存储卡吗？',
    'knowledge-mate-x6-satellite': 'Mate X6 标准版与典藏版分别有哪些卫星通信能力，需要满足什么业务条件？',
    'knowledge-nova14-pro-power': 'nova 14 Pro 充电时会持续保持最高功率吗，实际功率是否随使用场景变化？',
    'knowledge-freeclip-triple': 'FreeClip 用哪一种敲击手势切换到下一首歌曲？',
    'knowledge-freeclip-charging': 'FreeClip 充电盒的有线充电接口是什么，无线充电的最高功率是多少？',
    'multi-pura80-0': '请分别回答：Pura 80 的标称屏幕尺寸和实际可视区域有什么区别？机身存储是否全部可用，能否插存储卡扩容？',
}

# These relations permit useful caveats, but do not make them required retrieval targets.
OPTIONAL_PAIRS = {
    'pura80.md': [('water', 'wet')],
    'pura80-pro.md': [('water', 'wet')],
    'mate70.md': [('water', 'wet')],
    'mate70-pro-plus.md': [('system', 'upgrade')],
    'nova14-pro.md': [('wired', 'power')],
    'mate-x6.md': [('satellite', 'activation')],
    'freebuds6.md': [('charging', 'time')],
    'freeclip.md': [('pair', 'reset'), ('codec', 'l2hc')],
}

# Authored after the implementation choice (built-in chinese analyzer) was fixed.
# No answers from the evaluated system may be used to revise these questions or labels.
# Each row: question, required section IDs, explicitly allowed extra section IDs.
HOLDOUT = {
    'pura80': [
        ('6.6 英寸是不是指屏幕每个角都能完整显示的矩形？这个数字应该怎么理解？', ['screen'], []),
        ('想用充电底座给这台机器补电，底座和电源要怎样搭配，最高能到什么功率？', ['wireless'], []),
        ('掉进水里以后能立即插电吗？请同时说明抗水实验的条件、防护是否永久以及浸液损坏的保修限制。', ['water', 'wet'], []),
        ('这款手机的电池能量密度具体是多少 Wh/kg？我需要厂家给出的数值。', [], []),
    ],
    'pura80-pro': [
        ('用任意写着 100W 的充电器，就能让这台手机全程以 100W 充电，对吗？', ['wired'], []),
        ('慢动作视频的 960 帧是怎么实现的，是否代表每种模式都输出同样的像素和帧率？', ['video'], []),
        ('比较一下插线充电和使用无线底座时的最高功率，以及各自需要购买的配套设备。', ['wired', 'wireless'], []),
        ('这款手机在零下十度连续录像时，官方实测的续航时长是多少分钟？', [], []),
    ],
    'mate70': [
        ('普通版手机显示 4.3，就说明它永远不能用 5.0，对吗？先锋版出厂又是什么系统？', ['system'], []),
        ('买下标称容量后，能不能把全部空间用于文件？以后可以靠存储卡增加空间吗？', ['storage'], []),
        ('请解释屏幕尺寸是怎么量的，以及相机标称像素和最高录像规格是否适用于所有拍摄模式。', ['screen', 'camera'], []),
        ('官方对这款机型的扬声器频率响应曲线给出了哪些具体测量值？', [], []),
    ],
    'mate70-pro-plus': [
        ('宣传里的 100 倍和 4 倍是不是都属于光学变焦，可以直接拿来比较？', ['zoom'], []),
        ('约 226 克包含电池吗？为什么不同配置或测量方式得到的重量可能不一样？', ['weight'], []),
        ('我不清楚手上是普通版还是先锋版。请说明两者列出的系统版本，以及 4.3 设备的升级能力。', ['system', 'upgrade'], []),
        ('原厂屏幕连接器的针脚定义和每个针脚的电压是多少？', [], []),
    ],
    'nova14': [
        ('手机接口长得是 Type-C，就意味着传文件用的是 USB 3 协议，对吗？', ['usb'], []),
        ('我想用 SIM 卡来付款，这张卡应该装在哪个卡槽？', ['nfc'], []),
        ('这个 Type-C 口传文件用什么数据协议，接耳机时又需要什么类型或转换器？', ['usb', 'headset'], []),
        ('这款手机在标准跌落测试中，跌落高度和累计通过次数分别是多少？', [], []),
    ],
    'nova14-pro': [
        ('所有版本都重 207 克，对吗？昆仑版和普通版应怎样区分？', ['weight'], []),
        ('自拍时的 2 倍与 5 倍变焦分别是什么类型，能看成相同能力吗？', ['selfie'], []),
        ('为了使用最高有线快充功率，应准备什么套装？整个充电过程中功率会不会变化？', ['wired', 'power'], []),
        ('前置摄像头图像传感器的完整供应商料号是什么？', [], []),
    ],
    'nova14-ultra': [
        ('我换上更快的路由器，就一定能达到无线协议的理论峰值速度，对吗？', ['wifi'], []),
        ('耳机有线连接时应该选哪种耳机，不匹配时需要怎样的转换器？', ['headset'], []),
        ('请分别列出机身存储规格和电池的典型、额定容量，再说明可用空间及电池拆卸的限制。', ['storage', 'battery'], []),
        ('这款机器内部散热材料的厚度公差范围是多少毫米？', [], []),
    ],
    'mate-x6': [
        ('直接用包装里的数据线，就一定能实现 USB 3.1 GEN1 的数据传输，对吗？', ['usb'], []),
        ('不管买标准版还是典藏版，电池典型容量都是同一个数值吗？', ['battery'], []),
        ('我想买来就直接用卫星通信。请说明版本能力、业务开通、首次激活网络、使用环境和地区条件。', ['satellite', 'activation'], []),
        ('这款设备的铰链在一万次开合后，官方测得的扭矩具体是多少牛米？', [], []),
    ],
    'freebuds6': [
        ('耳机机身和充电盒都具有 IP54 防护，所以潮湿时都能充电，对吗？', ['water'], []),
        ('听歌时想把声音调大或调小，应该在耳机上做什么动作？', ['volume'], []),
        ('靠近设备出现配对弹窗和多设备连接，分别要求什么设备和系统版本？', ['popup', 'multi'], []),
        ('连续使用五百小时后，耳机的声学频响漂移了多少分贝？', [], []),
    ],
    'freeclip': [
        ('连接新手机时按住盒子按键十秒，就是普通配对操作，对吗？两种长按时长如何区分？', ['pair', 'reset'], []),
        ('别套用其他耳机的操作：这款耳机是否支持滑动和按捏控制？', ['gesture'], ['double', 'triple']),
        ('这款耳机支持和不支持哪些蓝牙音频编码？要使用 L2HC 还需要满足什么条件？', ['codec', 'l2hc'], []),
        ('每只耳机使用的蓝牙天线峰值增益是多少 dBi？', [], []),
    ],
}


def label(case, sections, optional_keys):
    required = set(case['goldEvidence'])
    optional = set(optional_keys) - required
    scope = {key for key, section in sections.items() if case['sku'] in section['skus']}
    assert required | optional <= scope
    forbidden = scope - required - optional
    case.update(labelVersion=2, optionalEvidence=sorted(optional), forbiddenEvidence=sorted(forbidden))
    for name, keys in [('requiredFacts', case['goldEvidence']), ('optionalFacts', sorted(optional)),
                       ('forbiddenFacts', sorted(forbidden))]:
        case[name] = [fact for key in keys for fact in sections[key]['facts']]
    case['referenceAnswer'] = '\n'.join(case['requiredFacts']) if required else '现有资料未提供回答该问题所需的信息。'
    return case


def build():
    parent = read_json(V1)
    sections = source_sections(PACKAGE)
    cases = []
    for old in parent['cases']:
        case = copy.deepcopy(old)
        case['split'] = 'revised_regression'
        case['previousSplit'] = old['split']
        case['previousQuestion'] = old['question']
        case['question'] = QUESTION_EDITS.get(old['id'], old['question'])
        optional = set()
        for key in case['goldEvidence']:
            source, section = key.split('#')
            for a, b in OPTIONAL_PAIRS.get(source, []):
                if section in (a, b):
                    optional.add(source + '#' + (b if section == a else a))
        if case['id'] == 'knowledge-freeclip-triple':
            optional.add('freeclip.md#gesture')
        case['labelReview'] = 'Source-based review of known v1 cases; optional topic caveats permitted, other product sections forbidden.'
        cases.append(label(case, sections, optional))
    manifest = read_json(PACKAGE / 'manifest.json')
    docs = {d['id']: d for d in manifest['documents']}
    for source, rows in HOLDOUT.items():
        doc = docs[source]
        filename = doc['file'].split('/')[-1]
        for i, (question, required, optional) in enumerate(rows, 1):
            case = {'id': f'fresh-{source}-{i}', 'question': question, 'sku': doc['skus'][0],
                    'category': 'unanswerable' if not required else 'multi_evidence' if len(required)>1 else 'single_evidence',
                    'questionStyle': 'missing_information' if not required else 'counterpremise' if i==1 else 'multi_constraint' if i==3 else 'paraphrase',
                    'split': 'frozen_question_holdout', 'sourceGroups': doc['sourceIds'],
                    'answerable': bool(required), 'goldEvidence': [filename+'#'+key for key in required],
                    'labelReview': 'Authored from frozen source; never executed or used for prompt/parameter tuning before freeze.'}
            cases.append(label(case, sections, [filename+'#'+key for key in optional]))
    return {'version':'rag-retrieval-v2', 'package':parent['package'],
            'parentDatasetSha256':hashlib.sha256(V1.read_bytes()).hexdigest(),
            'sourceHashes':parent['sourceHashes'], 'modes':MODES,
            'origin':'AI-assisted source review; no independent human annotation',
            'scope':'104 revised known cases plus 40 question-disjoint cases on known sources; not source-disjoint holdout',
            'answerMetric':'required facts covered; optional facts accepted; other document facts rejected; abstention checked',
            'labelUnit':parent['labelUnit'], 'cases':cases}


def validate(suite):
    sections = source_sections(ROOT / suite['package'])
    assert hashlib.sha256(V1.read_bytes()).hexdigest() == suite['parentDatasetSha256'], 'v1 changed'
    assert all(hashlib.sha256((ROOT/suite['package']/name).read_bytes()).hexdigest()==digest
               for name,digest in suite['sourceHashes'].items()), 'Corpus changed'
    ids, questions = set(), set()
    previous = {re.sub(r'\W','',c['question']).casefold() for c in read_json(V1)['cases']}
    for c in suite['cases']:
        q=re.sub(r'\W','',c['question']).casefold()
        assert c['id'] not in ids and q and q not in questions
        ids.add(c['id']); questions.add(q)
        if c['split']=='frozen_question_holdout': assert q not in previous
        assert c['labelVersion']==2 and len(c['question'])<=4000
        required,optional,forbidden=[set(c[name]) for name in ['goldEvidence','optionalEvidence','forbiddenEvidence']]
        scope={key for key,s in sections.items() if c['sku'] in s['skus']}
        assert required.isdisjoint(optional) and required.isdisjoint(forbidden) and optional.isdisjoint(forbidden)
        assert required|optional|forbidden == scope and scope
        assert bool(required)==c['answerable']
        for facts,keys in [('requiredFacts','goldEvidence'),('optionalFacts','optionalEvidence'),('forbiddenFacts','forbiddenEvidence')]:
            assert c[facts]==[f for key in c[keys] for f in sections[key]['facts']]
        assert set(c['requiredFacts']).isdisjoint(c['optionalFacts'])
        assert set(c['requiredFacts']+c['optionalFacts']).isdisjoint(c['forbiddenFacts'])
    counts=Counter(c['split'] for c in suite['cases'])
    assert counts=={'revised_regression':104,'frozen_question_holdout':40}
    assert suite['modes']==MODES
    return {'cases':len(ids),'splits':dict(counts),'categories':dict(Counter(c['category'] for c in suite['cases'])),
            'questionEdits':sum(c.get('previousQuestion',c['question'])!=c['question'] for c in suite['cases']),
            'casesWithOptionalFacts':sum(bool(c['optionalFacts']) for c in suite['cases']), 'evidenceSections':len(sections)}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write',action='store_true')
    args=parser.parse_args()
    if args.write:
        if DESTINATION.exists(): raise SystemExit('v2 already exists; do not silently replace frozen labels')
        suite=build(); validate(suite)
        DESTINATION.write_text(json.dumps(suite,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(validate(read_json(DESTINATION)),ensure_ascii=False))
