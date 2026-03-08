<template>
  <div class="view-stack">
    <section class="panel">
      <div class="panel-head">
        <h3>一眼演示文档 - {{ roleLabel }}</h3>
      </div>

      <div class="hint-box">
        <p><strong>角色目标：</strong>{{ currentPlaybook.goal }}</p>
        <p><strong>关键产出：</strong>{{ currentPlaybook.output }}</p>
      </div>

      <table class="table">
        <thead>
          <tr>
            <th>步骤</th>
            <th>页面</th>
            <th>操作</th>
            <th>讲解话术</th>
            <th>验收点</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in currentPlaybook.steps" :key="item.step">
            <td>{{ item.step }}</td>
            <td>{{ item.page }}</td>
            <td>{{ item.action }}</td>
            <td>{{ item.script }}</td>
            <td>{{ item.checkpoint }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  role: 'provider' | 'consumer' | 'operator'
}>()

type PlaybookStep = {
  step: string
  page: string
  action: string
  script: string
  checkpoint: string
}

type Playbook = {
  goal: string
  output: string
  steps: PlaybookStep[]
}

const playbooks: Record<'provider' | 'consumer' | 'operator', Playbook> = {
  provider: {
    goal: '把资产从发布到可协商状态讲清楚，并证明供应侧可追踪交付。',
    output: '目录中有可消费资产、协商合同可见、传输链路可追踪。',
    steps: [
      {
        step: 'P1',
        page: '供应方工作台',
        action: '刷新/创建策略后，点击“发布资产（供应方）”',
        script: '供应方先选择运营方策略，再发布资产并写入 Offer。',
        checkpoint: '发布轨迹显示“创建资产->写入Offer->刷新目录”。'
      },
      {
        step: 'P2',
        page: '供应方工作台',
        action: '刷新“协商列表/协商合同”',
        script: '供应方可以查看消费者发起的协商与达成协议。',
        checkpoint: '出现 neg- 与 agr- 记录。'
      },
      {
        step: 'P3',
        page: '供应方工作台',
        action: '查看“数据传输状态与链路追踪”',
        script: '供应方可追踪交付状态，确认流程已进入数据面执行。',
        checkpoint: 'transfer/dataPlane/edr 链路完整显示。'
      }
    ]
  },
  consumer: {
    goal: '把消费者从发现资产到成功取数讲清楚。',
    output: '完成目录发现、协商签约、发起传输、EDR 拉取数据。',
    steps: [
      {
        step: 'C1',
        page: '消费方工作台',
        action: '刷新目录并选中资产',
        script: '消费者先发现并选择目标资产。',
        checkpoint: '资产详情可见（名称/描述/归属）。'
      },
      {
        step: 'C2',
        page: '消费方工作台',
        action: '点击“执行签发并签约”',
        script: '先完成 Issuer/Identity/DCP 流程，再创建协商并形成可执行协议。',
        checkpoint: '签发轨迹全部成功，协商列表出现 neg-，协议列表出现 agr-。'
      },
      {
        step: 'C3',
        page: '消费方工作台',
        action: '“用于传输” -> “发起传输” -> “拉取数据”',
        script: '签约后发起传输，通过 EDR 安全拉取数据。',
        checkpoint: 'payload 返回且传输状态为 STARTED/已完成轨迹。'
      }
    ]
  },
  operator: {
    goal: '把治理、计费与运维可观测能力讲清楚。',
    output: '接口巡检正常、额度校验生效、节点健康可视化。',
    steps: [
      {
        step: 'O1',
        page: '治理模块接口',
        action: '点击“一键巡检”',
        script: '运营方先确认治理服务可用性。',
        checkpoint: '巡检表中状态码为 200，状态为 UP。'
      },
      {
        step: 'O2',
        page: '治理模块接口',
        action: '执行“额度校验”',
        script: '运营方验证按次计费规则可生效。',
        checkpoint: '返回 used/remaining/estimatedAmount。'
      },
      {
        step: 'O3',
        page: '节点与健康',
        action: '刷新健康状态与数据平面摘要',
        script: '运营方确认平台运行稳定与双数据平面承载情况。',
        checkpoint: 'health/governance/dataplane 汇总为 UP。'
      }
    ]
  }
}

const roleLabel = computed(() => {
  if (props.role === 'provider') {
    return '供应方（华东车联）'
  }
  if (props.role === 'consumer') {
    return '消费方（保险风控中心）'
  }
  return '运营方（数据空间运营平台）'
})

const currentPlaybook = computed(() => playbooks[props.role])
</script>
