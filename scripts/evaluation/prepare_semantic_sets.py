"""Reproduce authored semantic regression and source-disjoint holdout inputs (no API calls)."""
from eval_v2 import ROOT, SUITE, read_json, write_json


def turn(message, sku, filename=None, page=False, phrases=None):
    expected = {'events': ['token', 'done'], 'intent': 'PRODUCT_QUERY', 'products': ['{{products.' + sku + '}}']}
    if filename:
        expected['citations'] = [filename]
    if phrases:
        expected['textAll'] = phrases
    result = {'type': 'chat', 'message': message, 'expect': expected}
    if page:
        result['productId'] = '{{products.' + sku + '}}'
    return result


def main():
    old = read_json(SUITE)
    selected = ['knowledge-pura80-wireless', 'knowledge-pura80-system', 'knowledge-pura80-pro-system',
                'knowledge-pura80-pro-wireless', 'knowledge-mate70-wireless', 'knowledge-mate70-pro-plus-upgrade',
                'knowledge-nova14-headset', 'knowledge-nova14-pro-selfie', 'knowledge-nova14-ultra-usb',
                'knowledge-nova14-ultra-headset', 'knowledge-nova14-ultra-wifi']
    known = {c['id']: c for c in old['cases']}
    missing = set(selected) - known.keys()
    if missing:
        raise ValueError('Correct development IDs before generating: ' + str(sorted(missing)))
    dev = {k: v for k, v in old.items() if k != 'cases'}
    dev['cases'] = [known[i] for i in selected]
    dev['cases'].append({'id': 'semantic-explicit-product-switch', 'principal': 'customer', 'split': 'development',
        'sourceGroups': ['pura80', 'nova14'], 'referenceAnswer': '先回答 Pura 80 最高66W；明确切到 nova 14 Pro 后为最高100W；后续代词继续 nova；显式商品页仍优先。',
        'steps': [turn('这款有线快充最高多少？', 'HUAWEI-PURA80-12-256', 'pura80.md', True, [['66']]),
                  turn('改问 HUAWEI-NOVA14PRO-256，有线快充最高多少？', 'HUAWEI-NOVA14PRO-256', 'nova14-pro.md', phrases=[['100']]),
                  turn('它的前置摄像头变焦呢？', 'HUAWEI-NOVA14PRO-256', 'nova14-pro.md'),
                  turn('HUAWEI-NOVA14PRO-256 我稍后再看，先说当前商品页这款的快充。', 'HUAWEI-PURA80-12-256', 'pura80.md', True, [['66']])]})
    write_json(ROOT / 'quality/evaluation/datasets/semantic-regression-v1.json', dev)

    package = ROOT / 'data/retail-holdout-v2'
    apple = 'https://support.apple.com/en-us/121029'
    samsung = 'https://images.samsung.com/is/content/samsung/assets/us/2401/business/mobile/phones/galaxy/Galaxy_S24_Series_B2B_Spec_Sheet.pdf'
    specs = [
        ('iphone16-us', 'APPLE-IP16-US-128', 'Apple', 'iPhone 16', apple, [
            ('display', 'Display', '6.1英寸为标称 OLED 屏幕尺寸；标准矩形对角线为6.12英寸，实际可视面积更小。'),
            ('brightness', 'Display', '亮度：典型最高1000尼特，HDR峰值1600尼特，户外峰值2000尼特。不能把户外峰值当成所有场景的持续亮度。'),
            ('usb', 'Charging and Expansion', 'USB-C接口支持USB 2，传输速率最高480Mb/s。接口外形不能作为USB 3速度的依据。'),
            ('charging', 'Power and Battery; footnotes', '使用20W或以上适配器及USB-C充电线，约30分钟最高可充至50%。适配器另售；实际充电时间受设置、使用方式和环境影响，不能保证任意充电器都达到该结果。'),
            ('box', 'In the Box', '包装包含手机、1米USB-C充电线及资料，不含电源适配器或EarPods。'),
            ('sim', 'SIM Card', '本包仅指美国规格：双eSIM，不兼容实体SIM卡。不要把美国规格推广为中国大陆版本结论。'),
        ]),
        ('galaxy-s24-us', 'SAMSUNG-S24-US-128', 'Samsung', 'Galaxy S24', samsung, [
            ('display', 'Page 2: base Galaxy S24 column, Display; footnote 7', '基础款S24采用6.2英寸Dynamic AMOLED 2X、FHD+、120Hz自适应刷新率。实际可视面积因圆角和摄像头开孔而小于标称矩形。不要混用S24+或Ultra列。'),
            ('battery', 'Page 2: Battery; footnote 11', '基础款S24电池容量4000mAh为第三方实验条件下的典型值，额定容量更低；本资料没有列出额定容量具体数值。实际续航取决于网络和使用方式等。'),
            ('storage', 'Page 2: Memory and Storage; footnote 10', '基础款S24为8GB内存，存储选项128GB或256GB；系统已有内容会占用空间，并非全部标称容量都可用。'),
            ('notes', 'Page 2: footnote 5', 'Note Assist仅适用于Samsung Notes内200至4000字符的文本，需要登录Samsung账号并连接互联网。'),
            ('zoom', 'Page 2: Camera Zoom; footnote 9', '基础款S24列出30倍Space Zoom，其中包含数字变焦，可能降低图像质量；不能描述为30倍纯光学无损变焦。'),
            ('scope', 'Page 2: information date', '这份美国商务规格表的信息日期为2024-01-17，不是实时固件或售价表。资料没有列出基础款S24有线充电功率，不能据此确认25W或45W。'),
        ])]
    manifest = {'version': 'retail-holdout-v2', 'verifiedAt': '2026-10-25', 'origin': 'official-facts-and-project-paraphrases', 'sources': [], 'documents': []}
    catalog = {'products': []}
    for sid, sku, brand, model, url, sections in specs:
        manifest['sources'].append({'id': sid, 'url': url, 'title': model + ' US official specifications',
            'checkedAt': '2026-10-25', 'region': 'United States', 'sourceGroup': sid,
            'rights': '厂商保留原文版权；仅整理事实与项目转述，不分发原页面或PDF。',
            'review': 'AI-assisted primary-source verification; no independent human annotation'})
        manifest['documents'].append({'id': sid, 'title': model + ' 美国规格与使用限制',
            'file': 'knowledge/' + sid + '.md', 'documentType': 'PRODUCT_MANUAL', 'skus': [sku], 'sourceIds': [sid],
            'sections': [{'id': key, 'sourceSection': source} for key, source, _ in sections]})
        path = package / 'knowledge' / (sid + '.md')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('# ' + model + ' 美国规格\n\n来源：' + url + '\n核验日期：2026-10-25\n适用：美国规格，非实时销售或固件承诺。\n\n' +
            '\n\n'.join('## ' + key + '：' + source + '\n\n' + fact + '\n\n来源章节：' + source for key, source, fact in sections) + '\n', encoding='utf-8')
        catalog['products'].append({'sku': sku, 'name': model + ' 美国规格 128GB', 'brand': brand, 'model': model,
            'category': 'phone', 'specs': {'storage': '128 GB', 'region': 'United States', 'source_id': sid,
                'source_url': url, 'source_checked_at': '2026-10-25'}, 'listPrice': None, 'saleStatus': 'OFF_SALE'})
    write_json(package / 'manifest.json', manifest)
    write_json(package / 'catalog.json', catalog)
    cases = []
    def case(cid, index, section, messages, answer):
        sid, sku, *_ = specs[index]
        cases.append({'id': cid, 'principal': 'customer', 'split': 'holdout', 'sourceGroups': [sid],
            'expectedEvidence': {'sourceId': sid, 'sectionId': section}, 'referenceAnswer': answer,
            'steps': [turn(q, sku, sid + '.md', i == 0) for i, q in enumerate(messages)]})
    case('fresh-iphone-usb-premise', 0, 'usb', ['线头是USB-C，所以传一段视频就有USB 3的速度吧？'], '否。USB 2，最高480Mb/s，不能从接口外形推导USB 3。')
    case('fresh-iphone-fast-charge', 0, 'charging', ['我只有旧的5W充电头，也一定能半小时到50%吗？'], '不能保证；20W或以上适配器及USB-C线的测试条件，实际时间有差异。')
    case('fresh-iphone-box', 0, 'box', ['收到手机后需要自己准备电源适配器吗？包装里有耳机吗？'], '适配器和EarPods不在包装中；包含USB-C线。')
    case('fresh-iphone-brightness', 0, 'brightness', ['商家说2000尼特就是室内任何时候的亮度，可靠吗？'], '2000是户外峰值，典型最高1000，HDR峰值1600。')
    case('fresh-iphone-display', 0, 'display', ['6.12英寸都能显示内容，连圆角都算可视区，对吗？'], '标准矩形对角线6.12，实际可视面积更小；标称6.1。')
    case('fresh-iphone-sim', 0, 'sim', ['我这张实体SIM能直接装进当前美国规格手机吗？'], '美国规格不兼容实体SIM，双eSIM。')
    case('fresh-s24-battery', 1, 'battery', ['4000mAh是每块电池保证达到的额定值吗？请给出额定数值。'], '4000为典型值，额定更低但本资料没有具体额定值，不应编造。')
    case('fresh-s24-note', 1, 'notes', ['我有100字的笔记，断网且不登录账号，Note Assist照样可以总结吗？'], '不能这样承诺：Samsung Notes 200–4000字符，登录Samsung账号及联网。')
    case('fresh-s24-zoom', 1, 'zoom', ['拍远处招牌时30倍就是纯光学、完全没有画质损失吧？'], '包含数字变焦，可能有画质损失。')
    case('fresh-s24-unknown-watts', 1, 'scope', ['请仅按当前资料确认：这台最高有线功率究竟25W还是45W？'], '资料没有提供，不能确认任一功率。')
    case('fresh-s24-correction', 1, 'display', ['这台的屏幕多大？', '不是Ultra，我说的始终是当前基础款。请再确认分辨率。'], '基础款6.2英寸FHD+，不能混入Ultra规格。')
    case('fresh-iphone-followup', 0, 'charging', ['有线充到一半大约要多久、配什么？', '那这个适配器就在包装里吧？'], '约30分钟最高50%，20W以上及USB-C线、受条件影响；适配器另售。')
    # A new cross-manufacturer conversation tests explicit entity changes and later pronouns.
    cases.append({'id': 'fresh-cross-brand-context', 'principal': 'customer', 'split': 'holdout',
        'sourceGroups': ['iphone16-us', 'galaxy-s24-us'],
        'referenceAnswer': '先谈iPhone USB2 480Mb/s；切到S24后8GB内存128/256GB存储；代词仍指S24，空间有系统占用；再明确切回iPhone，盒内没有适配器。',
        'steps': [turn('当前这款USB传数据能到什么速度？', 'APPLE-IP16-US-128', 'iphone16-us.md', True),
                  turn('换成 SAMSUNG-S24-US-128，它的内存和存储选项有哪些？', 'SAMSUNG-S24-US-128', 'galaxy-s24-us.md'),
                  turn('那它的128GB都能拿来存照片吗？', 'SAMSUNG-S24-US-128', 'galaxy-s24-us.md'),
                  turn('再切回 APPLE-IP16-US-128，包装里有电源适配器吗？', 'APPLE-IP16-US-128', 'iphone16-us.md')]})
    cases.append({'id': 'fresh-price-unknown', 'principal': 'customer', 'split': 'holdout',
        'sourceGroups': ['iphone16-us'], 'referenceAnswer': '暂无报价；不知道准确售价，不从其他地区或历史起售价推断。',
        'steps': [turn('不要说历史起售价，这个SKU现在具体卖多少钱？', 'APPLE-IP16-US-128', page=True, phrases=[['暂无报价']])]})
    write_json(ROOT / 'quality/evaluation/datasets/semantic-holdout-v2.json', {
        'version': 'serviceflow-eval-v2', 'origin': 'project-authored',
        'independence': 'New manufacturer/source families; authored by the project, not third-party or human-independent annotation.',
        'cases': cases})


if __name__ == '__main__':
    main()
