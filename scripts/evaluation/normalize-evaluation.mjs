import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const datasetPath = join(
  scriptDirectory,
  '..',
  '..',
  'quality',
  'evaluation',
  'datasets',
  'serviceflow-eval-200.json',
);
const cases = JSON.parse(await readFile(datasetPath, 'utf8'));

const products = [
  { id: 1, name: 'Pura 80', source: 'huawei-pura80-usage.md' },
  { id: 2, name: 'Pura 80 Pro', source: 'huawei-pura80-pro-usage.md' },
  { id: 3, name: 'Pura 80 Ultra', source: 'huawei-pura80-ultra-usage.md' },
];
const manualTopics = {
  1: [
    { question: '充电和电池使用时有哪些安全注意事项？', facts: ['停止充电', '授权服务中心'] },
    { question: '使用北斗卫星消息前需要满足哪些环境和激活条件？', facts: ['空旷无遮挡', '激活'] },
    { question: '连接 Type-C 耳机或转接器时要注意什么？', facts: ['兼容性', 'Type-C'] },
    { question: '送修或恢复出厂设置前，用户需要怎样保护数据？', facts: ['备份', '数据'] },
  ],
  2: [
    { question: '防尘抗水能力有哪些限制和注意事项？', facts: ['IP68', '潮湿状态'] },
    { question: '有线、无线和反向充电要满足哪些条件？', facts: ['超级快充', '兼容性'] },
    { question: '使用北斗卫星消息前需要满足哪些环境和激活条件？', facts: ['空旷无遮挡', '激活'] },
    { question: '拍摄高分辨率照片或 4K 视频时要注意什么？', facts: ['备份', '存储'] },
  ],
  3: [
    { question: '使用卫星通信和北斗卫星消息有哪些限制？', facts: ['空旷无遮挡', '运营商'] },
    { question: '防尘抗水能力有哪些限制和注意事项？', facts: ['IP68', '潮湿状态'] },
    { question: '长时间录像或高负载拍摄时要注意什么？', facts: ['散热', '备份'] },
    { question: '充电时出现接口进液或异常发热应该怎样处理？', facts: ['停止使用', '授权服务中心'] },
  ],
};
const policyTopics = [
  { question: '华为中国大陆手机主机的标准保修期是多久？', facts: ['一年'] },
  { question: '购买后第八日至第十五日出现非人为性能故障，可以怎样处理？', facts: ['换货', '保修'] },
  { question: '手机进液造成的损坏通常属于免费保修范围吗？', facts: ['不属于', '进液'] },
  { question: '未经授权拆机、跌落或挤压造成的损坏是否属于免费保修？', facts: ['不属于', '未经授权'] },
  { question: '手机送修前需要对个人数据、账号和 SIM 卡做什么准备？', facts: ['备份', 'SIM'] },
];

for (const item of cases) {
  if (item.id.startsWith('product-doc-')) {
    const sequence = Number(item.id.at(-2) + item.id.at(-1));
    const product = products[(sequence - 1) % products.length];
    const topicIndex = Math.floor((sequence - 1) / products.length) % 4;
    const topic = manualTopics[product.id][topicIndex];
    item.question = `${product.name}${topic.question}`;
    item.pageContext = { productId: product.id };
    item.expectedProducts = [product.id];
    item.expectedCitations = [product.source];
    item.expectedEvents = ['token', 'done'];
    item.requiredFacts = topic.facts;
  }

  if (item.id.startsWith('order-')) {
    const cancellation = item.question.includes('取消');
    item.expectedEvents = cancellation ? ['action_required', 'done'] : ['order', 'done'];
    item.requiredFacts = cancellation ? ['可以取消', '确认'] : ['当前状态', '退款状态'];
  }

  if (item.id.startsWith('policy-')) {
    const sequence = Number(item.id.at(-2) + item.id.at(-1));
    const topic = policyTopics[(sequence - 1) % policyTopics.length];
    item.question = `${topic.question}（措辞变体 ${sequence}）`;
    item.expectedCitations = ['huawei-mainland-phone-warranty.md'];
    item.expectedEvents = ['token', 'done'];
    item.requiredFacts = topic.facts;
  }

  if (item.id.startsWith('support-')) {
    item.expectedProducts = [];
    if (item.expectedIntent === 'COMPLAINT') {
      if (item.principalType === 'GUEST') {
        item.expectedEvents = ['token', 'done'];
        item.requiredFacts = ['登录'];
      } else {
        item.expectedEvents = ['ticket', 'done'];
        item.requiredFacts = ['工单'];
      }
    } else {
      item.expectedEvents = ['token', 'done'];
      item.requiredFacts = ['保修'];
    }
  }

  if (item.id.startsWith('compare-')) {
    item.requiredFacts = ['结构化规格'];
    item.forbiddenClaims = ['最值得购买', '性价比最高', '建议购买'];
  }
}

await writeFile(datasetPath, `${JSON.stringify(cases, null, 2)}\n`, 'utf8');
