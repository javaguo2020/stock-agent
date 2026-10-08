<template>
  <div class="app-container">
    <!-- 查询/输入区 -->
    <el-card class="box-card" shadow="hover">
      <div slot="header" class="clearfix">
        <span>多 Agent 智能分析（全量建议）</span>
        <el-tag v-if="session" size="small" style="margin-left: 8px">{{ session.stockName }}（{{ session.stockCode }}）· {{ session.operateTypeName }}</el-tag>
      </div>
      <el-form :inline="true" label-width="80px">
        <el-form-item label="股票代码">
          <el-input v-model="query.stockCode" placeholder="如 000001 或 600519.SH" style="width: 200px" clearable />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-radio-group v-model="query.operateType">
            <el-radio-button :label="1">短期</el-radio-button>
            <el-radio-button :label="2">中长期</el-radio-button>
            <el-radio-button :label="3">长期</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="风险偏好">
          <el-radio-group v-model="query.riskPreference">
            <el-radio-button :label="1">保守</el-radio-button>
            <el-radio-button :label="2">稳健</el-radio-button>
            <el-radio-button :label="3">激进</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-video-play" :loading="polling" @click="handleStart">开始分析</el-button>
          <el-button v-if="session && session.status === 'INTERRUPTED'" type="warning" icon="el-icon-refresh-right" @click="handleResume">一键恢复</el-button>
        </el-form-item>
      </el-form>

      <!-- 流程进度（状态机） -->
      <el-steps v-if="session" :active="activeStep" finish-status="success" :process-status="stepProcessStatus" align-center style="margin: 10px 0 5px">
        <el-step v-for="node in nodes" :key="node.key" :title="node.label" :status="stepStatus(node.key)" />
      </el-steps>
      <div v-if="session" class="progress-text">
        进度 {{ session.progress }}
        <span v-if="session.status === 'RUNNING'">· 当前节点：{{ session.currentNodeLabel || '-' }}，正在分析，请稍候…</span>
        <span v-else-if="session.status === 'DONE'">· 分析完成</span>
      </div>

      <!-- 中断提示 -->
      <el-alert v-if="session && session.status === 'INTERRUPTED'" type="warning" :closable="false" style="margin-top: 10px">
        <template slot="title">
          分析中断：{{ session.failReason || '节点超时或执行异常' }}
          <el-button type="warning" size="mini" icon="el-icon-refresh-right" style="margin-left: 12px" @click="handleResume">一键恢复（跳过已完成节点继续）</el-button>
        </template>
      </el-alert>
      <el-alert v-if="session && session.status === 'FAILED'" type="error" :closable="false" style="margin-top: 10px" :title="'分析失败：' + (session.failReason || '未知原因')" />
    </el-card>

    <!-- 法官终裁 -->
    <el-card v-if="verdict" class="box-card" shadow="hover" style="margin-top: 12px">
      <div slot="header" class="clearfix">
        <span>法官终裁（证据可信度概率）</span>
        <span :class="['decision-tag', decisionClass(verdict.decision)]" style="float: right">{{ verdict.decisionLabel }}</span>
      </div>
      <el-row :gutter="20" class="verdict-metrics">
        <el-col :span="6"><div class="metric"><div class="metric-label">向上概率</div><div class="metric-value">{{ verdict.upProbability !== undefined && verdict.upProbability !== null ? verdict.upProbability + '%' : '-' }}</div></div></el-col>
        <el-col :span="6"><div class="metric"><div class="metric-label">辩论胜方</div><div class="metric-value">{{ winnerLabel(verdict.winner) }}</div></div></el-col>
        <el-col :span="6"><div class="metric"><div class="metric-label">风险回报比</div><div class="metric-value">1 : {{ verdict.riskReward || '-' }}</div></div></el-col>
        <el-col :span="6"><div class="metric"><div class="metric-label">建议仓位</div><div class="metric-value">{{ verdict.positionPct }}%</div></div></el-col>
      </el-row>
      <el-row :gutter="20" class="verdict-metrics">
        <el-col :span="6"><div class="metric"><div class="metric-label">建议买入价</div><div class="metric-value">{{ verdict.entryPrice || '-' }}</div></div></el-col>
        <el-col :span="6"><div class="metric"><div class="metric-label">止损价</div><div class="metric-value">{{ verdict.stopLoss > 0 ? verdict.stopLoss : '-' }}</div></div></el-col>
        <el-col :span="6"><div class="metric"><div class="metric-label">目标价</div><div class="metric-value">{{ verdict.targetPrice > 0 ? verdict.targetPrice : '-' }}</div></div></el-col>
      </el-row>
      <div class="verdict-reason">{{ verdict.reasoning }}</div>
      <div v-if="verdict.keyFactors && verdict.keyFactors.length" style="margin-top: 8px">
        <el-tag v-for="(f, i) in verdict.keyFactors" :key="i" size="small" type="info" style="margin: 0 8px 4px 0">{{ f }}</el-tag>
      </div>
    </el-card>

    <!-- 仓位建议 -->
    <el-card v-if="position" class="box-card" shadow="hover" style="margin-top: 12px">
      <div slot="header"><span>仓位管理委员建议</span><el-tag style="float: right" type="warning">建议仓位 {{ position.positionPct }}%</el-tag></div>
      <div style="color: #909399; font-size: 13px">{{ position.volatilityNote }}</div>
      <div style="margin-top: 6px">{{ position.reason }}</div>
    </el-card>

    <!-- 辩论纪要 -->
    <el-card v-if="session && session.debateTranscript && session.debateTranscript.length" class="box-card" shadow="hover" style="margin-top: 12px">
      <div slot="header"><span>多空辩论纪要（最多 {{ debateRounds }} 轮攻防）</span></div>
      <el-timeline>
        <el-timeline-item v-for="(r, i) in session.debateTranscript" :key="i"
                          :type="r.role === 'BULL' ? 'danger' : 'success'"
                          :timestamp="'第 ' + r.round + ' 轮 · ' + r.roleName" placement="top">
          <div class="debate-arg">{{ r.argument }}</div>
          <div v-if="r.citedEvidence && r.citedEvidence.length" style="margin-top: 4px">
            <el-tag v-for="(c, j) in r.citedEvidence" :key="j" size="mini" :type="r.role === 'BULL' ? 'danger' : 'success'" style="margin: 0 6px 4px 0">{{ c }}</el-tag>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 证据卡 -->
    <el-card v-if="session && session.evidenceCards && session.evidenceCards.length" class="box-card" shadow="hover" style="margin-top: 12px">
      <div slot="header"><span>子 Agent 证据卡（客观事实，无多空结论）</span></div>
      <el-collapse v-model="activeCards">
        <el-collapse-item v-for="(card, i) in session.evidenceCards" :key="i" :name="String(i)">
          <template slot="title">
            <el-tag size="small" effect="dark" :type="cardTagType(card.agent)" style="margin-right: 8px">{{ card.agentName }}</el-tag>
            <span class="card-summary">{{ card.summary }}</span>
          </template>
          <el-table :data="card.evidence" size="mini" border>
            <el-table-column label="证据" prop="item" min-width="180" show-overflow-tooltip />
            <el-table-column label="详情" prop="detail" min-width="320" show-overflow-tooltip />
            <el-table-column label="强度" prop="strength" width="70" align="center">
              <template slot-scope="scope">
                <el-tag size="mini" :type="strengthType(scope.row.strength)">{{ scope.row.strength }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="110" align="center">
              <template slot-scope="scope">
                <el-progress :percentage="scope.row.confidence || 0" :show-text="false" :stroke-width="8"
                             :color="confidenceColor(scope.row.confidence)" style="width: 70px; margin-right: 6px" />
                <span style="font-size: 12px">{{ scope.row.confidence || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="出处" prop="source" width="110" show-overflow-tooltip />
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script>
import { submitAnalysis, getStatus, resumeAnalysis } from '@/api/agent/analysis'

const NODE_LIST = [
  { key: 'DATA_FETCH', label: '数据拉取' },
  { key: 'CHAN', label: '缠论' },
  { key: 'TECH', label: '技术' },
  { key: 'PATTERN', label: '形态' },
  { key: 'FUNDAMENTAL', label: '基本面' },
  { key: 'DEBATE', label: '多空辩论' },
  { key: 'JUDGE', label: '法官裁决' }
]

export default {
  name: 'MultiAgentAnalysis',
  data() {
    return {
      query: { stockCode: '', operateType: 1, riskPreference: 2 },
      nodes: NODE_LIST,
      session: null,
      sessionId: null,
      polling: false,
      timer: null,
      activeCards: ['0', '1', '2', '3'],
      debateRounds: 3
    }
  },
  computed: {
    verdict() {
      return this.session && this.session.verdict
    },
    position() {
      return this.session && this.session.positionAdvice
    },
    activeStep() {
      if (!this.session) return 0
      const map = this.session.nodeStatus || {}
      let done = 0
      NODE_LIST.forEach(n => { if (map[n.key] === 'SUCCESS') done++ })
      return done
    },
    stepProcessStatus() {
      return this.session && this.session.status === 'INTERRUPTED' ? 'error' : 'process'
    }
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    handleStart() {
      if (!this.query.stockCode || !this.query.stockCode.trim()) {
        this.$message.warning('请输入股票代码')
        return
      }
      submitAnalysis({ stockCode: this.query.stockCode.trim(), operateType: this.query.operateType, riskPreference: this.query.riskPreference }).then(res => {
        this.sessionId = res.data.sessionId
        this.session = null
        this.$message.success('分析已提交，正在执行…')
        this.startPolling()
      })
    },
    handleResume() {
      if (!this.sessionId) return
      resumeAnalysis(this.sessionId).then(() => {
        this.$message.success('已从中断节点恢复执行…')
        this.startPolling()
      })
    },
    startPolling() {
      this.stopPolling()
      this.polling = true
      this.fetchStatus()
      this.timer = setInterval(this.fetchStatus, 3000)
    },
    stopPolling() {
      this.polling = false
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    fetchStatus() {
      if (!this.sessionId) return
      getStatus(this.sessionId).then(res => {
        this.session = res.data
        if (this.session.status !== 'RUNNING') {
          this.stopPolling()
          if (this.session.status === 'DONE') {
            this.$message.success('分析完成')
            if (this.session.debateTranscript && this.session.debateTranscript.length) {
              this.debateRounds = Math.max(...this.session.debateTranscript.map(r => r.round))
            }
          }
        }
      }).catch(() => {
        this.stopPolling()
      })
    },
    stepStatus(key) {
      if (!this.session) return 'wait'
      const st = (this.session.nodeStatus || {})[key]
      if (st === 'SUCCESS') return 'success'
      if (st === 'RUNNING') return this.session.status === 'INTERRUPTED' ? 'error' : 'process'
      return 'wait'
    },
    decisionClass(decision) {
      return { BUY: 'decision-buy', SELL: 'decision-sell', HOLD: 'decision-hold', WATCH: 'decision-watch' }[decision] || 'decision-watch'
    },
    winnerLabel(winner) {
      return { BULL: '多头', BEAR: '空头', TIE: '平局' }[winner] || winner || '-'
    },
    cardTagType(agent) {
      return { '缠论': 'danger', '技术': 'primary', '形态': 'warning', '基本面': 'success' }[agent] || 'info'
    },
    strengthType(strength) {
      return { '强': 'danger', '中': 'warning', '弱': 'info' }[strength] || 'info'
    },
    confidenceColor(conf) {
      if (!conf) return '#909399'
      if (conf >= 80) return '#f56c6c'
      if (conf >= 60) return '#e6a23c'
      return '#909399'
    }
  }
}
</script>

<style scoped>
.progress-text {
  color: #909399;
  font-size: 13px;
  text-align: center;
}
.decision-tag {
  color: #fff;
  padding: 4px 14px;
  border-radius: 4px;
  font-weight: bold;
}
.decision-buy { background: #f56c6c; }
.decision-sell { background: #67c23a; }
.decision-hold { background: #409eff; }
.decision-watch { background: #909399; }
.verdict-metrics { text-align: center; margin-bottom: 10px; }
.metric-label { color: #909399; font-size: 12px; }
.metric-value { font-size: 17px; font-weight: bold; margin-top: 4px; }
.verdict-reason {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 10px;
  color: #606266;
  line-height: 1.7;
  white-space: pre-wrap;
}
.debate-arg { line-height: 1.7; color: #303133; }
.card-summary { color: #606266; font-size: 13px; }
</style>
