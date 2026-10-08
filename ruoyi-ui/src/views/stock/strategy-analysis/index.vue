<template>
  <div class="app-container strategy-analysis">
    <!-- ==================== 当天交易复盘 ==================== -->
    <el-card shadow="never" class="review-card">
      <div class="review-header">
        <div class="review-title">
          <i class="el-icon-data-analysis"></i>
          <span>当天交易复盘</span>
          <span class="review-date">{{ review.tradeDate || today }}</span>
        </div>
        <div class="review-actions">
          <span v-if="review.createTime" class="review-time">生成时间：{{ review.createTime }}</span>
          <el-button
            type="primary"
            size="small"
            icon="el-icon-refresh"
            :loading="reviewLoading"
            @click="reloadReview"
          >
            重新生成复盘
          </el-button>
        </div>
      </div>

      <el-divider class="review-divider"></el-divider>

      <div v-if="reviewLoading" class="review-loading">
        <i class="el-icon-loading"></i>
        <span>正在采集当日数据并生成复盘，预计需要 30~60 秒，请稍候…</span>
      </div>

      <template v-else>
        <el-alert
          v-if="review.notice"
          :title="review.notice"
          :type="review.dataInaccurate ? 'warning' : 'info'"
          :closable="false"
          show-icon
          class="review-notice"
        ></el-alert>
        <div v-if="review.content" class="review-content-scroll">
          <div class="review-content markdown-body" v-html="renderMarkdown(review.content)"></div>
        </div>
        <el-empty v-else description="当日未复盘，点击右上角「重新生成复盘」生成" :image-size="80"></el-empty>
      </template>

      <!-- 采集到的市场数据速览 -->
      <div v-if="review.marketData" class="market-snapshot">
        <div v-if="review.marketData.indices && review.marketData.indices.length" class="snapshot-block">
          <div class="snapshot-label">三大指数 / 沪深300</div>
          <div class="index-list">
            <div v-for="(idx, i) in review.marketData.indices" :key="i" class="index-item">
              <span class="index-name">{{ idx.name }}</span>
              <span class="index-price">{{ formatNum(idx.price) }}</span>
              <span class="index-pct" :class="pctClass(idx.pct)">{{ formatPct(idx.pct) }}</span>
            </div>
          </div>
        </div>

        <div v-if="review.marketData.leadingSectors && review.marketData.leadingSectors.length" class="snapshot-block">
          <div class="snapshot-label">领涨板块</div>
          <div class="sector-tags">
            <el-tag v-for="(s, i) in review.marketData.leadingSectors.slice(0, 6)" :key="i" size="small" class="sector-tag">
              {{ s.name }} <b :class="pctClass(s.pct)">{{ formatPct(s.pct) }}</b>
            </el-tag>
          </div>
        </div>

        <div v-if="review.marketData.etfFlows && review.marketData.etfFlows.length" class="snapshot-block">
          <div class="snapshot-label">沪深300及重要ETF资金流</div>
          <div class="sector-tags">
            <el-tag v-for="(e, i) in review.marketData.etfFlows" :key="i" size="small" class="sector-tag">
              {{ e.name }} <b :class="pctClass(e.pct)">{{ formatPct(e.pct) }}</b>
            </el-tag>
          </div>
        </div>

        <div v-if="review.marketData.dataStatus && review.marketData.dataStatus.length" class="snapshot-block">
          <div class="snapshot-label">数据完整性</div>
          <div class="sector-tags">
            <el-tag
              v-for="(s, i) in review.marketData.dataStatus"
              :key="i"
              size="small"
              :type="dataMissing(s) ? 'warning' : 'success'"
              class="sector-tag"
            >{{ s }}</el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- ==================== 战法统计 ==================== -->
    <el-card shadow="never" class="analysis-card">
      <div class="analysis-header">
        <div class="analysis-title">
          <i class="el-icon-trophy"></i>
          <span>战法统计指标</span>
          <el-tooltip v-if="analysis.holdingDataUpdated === false" content="stock_sell_buy 的 update_date 未更新到最新交易日" placement="top">
            <el-tag type="warning" size="small" class="holding-tag">今日持仓数据未更新</el-tag>
          </el-tooltip>
        </div>
        <div class="analysis-filter">
          <el-date-picker
            v-model="query.inputDate"
            type="date"
            size="small"
            value-format="yyyy-MM-dd"
            placeholder="存入日期"
            style="width: 140px;"
          ></el-date-picker>
          <span class="filter-sep">+</span>
          <el-input-number
            v-model="query.intervalDays"
            :min="0"
            :max="365"
            size="small"
            controls-position="right"
            style="width: 100px;"
          ></el-input-number>
          <span class="filter-label">天</span>
          <el-divider direction="vertical"></el-divider>
          <span class="filter-label">实际截止</span>
          <el-date-picker
            v-model="query.actualCutoffDate"
            type="date"
            size="small"
            value-format="yyyy-MM-dd"
            placeholder="实际盈利截止日期"
            style="width: 140px;"
          ></el-date-picker>
          <el-button type="primary" size="small" icon="el-icon-search" :loading="analysisLoading" @click="loadAnalysis">
            计算指标
          </el-button>
        </div>
      </div>

      <el-row :gutter="16" class="kpi-row">
        <el-col :xs="12" :sm="12" :md="6">
          <div class="kpi-card">
            <div class="kpi-label"><i class="el-icon-aim"></i>理论胜率</div>
            <div class="kpi-value" :class="pctClass(analysis.theoreticalWinRate)">{{ formatPct(analysis.theoreticalWinRate) }}</div>
            <div class="kpi-sub">盈利 {{ analysis.theoreticalProfitCount || 0 }} / {{ analysis.theoreticalTotal || 0 }} 只</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <div class="kpi-card">
            <div class="kpi-label"><i class="el-icon-trend-charts"></i>理论收益率</div>
            <div class="kpi-value" :class="pctClass(analysis.theoreticalYield)">{{ formatPct(analysis.theoreticalYield) }}</div>
            <div class="kpi-sub">截止 {{ analysis.theoreticalCutoffDate || '-' }}</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <div class="kpi-card">
            <div class="kpi-label"><i class="el-icon-circle-check"></i>实际成功率</div>
            <div class="kpi-value" :class="pctClass(analysis.actualWinRate)">{{ formatPct(analysis.actualWinRate) }}</div>
            <div class="kpi-sub">盈利 {{ analysis.actualProfitCount || 0 }} / {{ analysis.actualTotal || 0 }} 只</div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="12" :md="6">
          <div class="kpi-card">
            <div class="kpi-label"><i class="el-icon-coin"></i>实际收益率</div>
            <div class="kpi-value" :class="pctClass(analysis.actualYield)">{{ formatPct(analysis.actualYield) }}</div>
            <div class="kpi-sub">截止 {{ analysis.actualCutoffDate || '-' }}</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" class="chart-row">
        <el-col :xs="24" :sm="24" :md="12">
          <div class="chart-box">
            <div class="chart-title">理论胜率 vs 实际成功率</div>
            <div ref="gaugeChart" class="chart"></div>
          </div>
        </el-col>
        <el-col :xs="24" :sm="24" :md="12">
          <div class="chart-box">
            <div class="chart-title">收益率对比</div>
            <div ref="yieldChart" class="chart"></div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
import { getAnalysis, getTradeReviewStatus, generateTradeReview } from '@/api/stock/strategy-analysis'
import * as echarts from 'echarts'

export default {
  name: 'StrategyAnalysis',
  data() {
    return {
      today: this.formatDate(new Date()),
      query: {
        inputDate: this.formatDate(new Date()),
        intervalDays: 0,
        actualCutoffDate: this.formatDate(new Date())
      },
      analysis: {
        theoreticalWinRate: null,
        theoreticalYield: null,
        actualWinRate: null,
        actualYield: null,
        theoreticalProfitCount: 0,
        theoreticalTotal: 0,
        actualProfitCount: 0,
        actualTotal: 0,
        theoreticalCutoffDate: '',
        actualCutoffDate: '',
        holdingDataUpdated: null
      },
      review: {
        tradeDate: '',
        content: '',
        createTime: '',
        generated: false,
        dataInaccurate: false,
        dataFinalizeTime: '',
        notice: '',
        marketData: null
      },
      reviewLoading: false,
      analysisLoading: false,
      gaugeChart: null,
      yieldChart: null
    }
  },
  mounted() {
    this.loadReview()
    this.loadAnalysis()
    this.$nextTick(() => this.initCharts())
    window.addEventListener('resize', this.handleResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize)
    if (this.gaugeChart) this.gaugeChart.dispose()
    if (this.yieldChart) this.yieldChart.dispose()
  },
  methods: {
    // 初次加载：查询状态；已有结果直接展示，未生成不自动生成，仅提示可手动生成
    async loadReview() {
      this.reviewLoading = true
      try {
        const status = await this.queryReviewStatus()
        if (status === 'DONE') return
        if (status === 'NO_KEY') {
          this.notifyAiNotReady()
          return
        }
        // 仅当后台已在生成中时保持加载轮询（如另一窗口触发过），NONE 不自动触发生成
        if (status === 'GENERATING') {
          await this.pollReviewUntilDone()
        }
      } catch (e) {
        this.$message.error('获取复盘失败：' + (e.message || e))
      } finally {
        this.reviewLoading = false
      }
    },

    // 点击「重新生成复盘」：强制重新生成并轮询
    async reloadReview() {
      this.reviewLoading = true
      try {
        const status = await this.queryReviewStatus()
        if (status === 'NO_KEY') {
          this.notifyAiNotReady()
          return
        }
        await generateTradeReview()
        await this.pollReviewUntilDone()
      } catch (e) {
        this.$message.error('重新生成复盘失败：' + (e.message || e))
      } finally {
        this.reviewLoading = false
      }
    },

    // 未开通AI功能：前端友好提示，避免无谓触发生成
    notifyAiNotReady() {
      this.$message.warning('尚未开通AI功能，请联系管理员开通后使用')
      this.review.notice = '尚未开通AI功能，请联系管理员开通后使用'
    },

    // 查询一次状态并应用结果；返回当前状态（NONE/GENERATING/DONE）
    async queryReviewStatus() {
      const res = await getTradeReviewStatus()
      if (res.code !== 200 || !res.data) {
        throw new Error((res && res.msg) || '状态查询失败')
      }
      const data = res.data
      this.review = {
        tradeDate: data.tradeDate || this.today,
        content: data.content || '',
        createTime: data.createTime || '',
        generated: data.generated,
        dataInaccurate: !!data.dataInaccurate,
        dataFinalizeTime: data.dataFinalizeTime || '',
        notice: data.notice || '',
        marketData: data.marketData || null
      }
      return data.status
    },

    // 生成中保持加载状态，定时轮询直到 DONE（或超时）
    // 后台生成不阻塞接口返回，轮询到有结果时再停止加载并展示内容
    async pollReviewUntilDone(maxTries = 60) {
      for (let i = 0; i < maxTries; i++) {
        await this.sleep(3000)
        const status = await this.queryReviewStatus()
        if (status === 'DONE') return
      }
      this.$message.error('复盘生成超时，请稍后刷新查看')
    },

    sleep(ms) {
      return new Promise(resolve => setTimeout(resolve, ms))
    },

    async loadAnalysis() {
      this.analysisLoading = true
      try {
        const res = await getAnalysis({
          inputDate: this.query.inputDate,
          intervalDays: this.query.intervalDays,
          actualCutoffDate: this.query.actualCutoffDate
        })
        if (res.code === 200 && res.data) {
          this.analysis = { ...this.analysis, ...res.data }
          this.$nextTick(() => this.updateCharts())
        } else {
          this.$message.error(res.msg || '计算失败')
        }
      } catch (e) {
        this.$message.error('计算指标失败：' + (e.message || e))
      } finally {
        this.analysisLoading = false
      }
    },

    initCharts() {
      this.gaugeChart = echarts.init(this.$refs.gaugeChart)
      this.yieldChart = echarts.init(this.$refs.yieldChart)
      this.updateCharts()
    },

    updateCharts() {
      if (this.gaugeChart) {
        this.gaugeChart.setOption({
          series: [
            this.makeGauge(this.num(this.analysis.theoreticalWinRate), '理论胜率', ['25%', '55%']),
            this.makeGauge(this.num(this.analysis.actualWinRate), '实际成功率', ['75%', '55%'])
          ]
        })
      }
      if (this.yieldChart) {
        this.yieldChart.setOption({
          tooltip: { trigger: 'axis' },
          grid: { left: '3%', right: '4%', bottom: '3%', top: 20, containLabel: true },
          xAxis: { type: 'category', data: ['理论收益率', '实际收益率'] },
          yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
          series: [
            {
              type: 'bar',
              barWidth: 60,
              data: [
                { value: this.num(this.analysis.theoreticalYield), itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] } },
                { value: this.num(this.analysis.actualYield), itemStyle: { color: '#67c23a', borderRadius: [4, 4, 0, 0] } }
              ],
              label: { show: true, position: 'top', formatter: '{c}%' }
            }
          ]
        })
      }
    },

    handleResize() {
      if (this.gaugeChart) this.gaugeChart.resize()
      if (this.yieldChart) this.yieldChart.resize()
    },

    // 生成单个仪表盘 series，理论胜率与实际成功率各占左右半区，避免指针重叠
    makeGauge(value, name, center) {
      return {
        type: 'gauge',
        center: center,
        radius: '95%',
        startAngle: 200,
        endAngle: -20,
        min: 0,
        max: 100,
        splitNumber: 5,
        axisLine: { lineStyle: { width: 14, color: [[0.5, '#f56c6c'], [0.7, '#e6a23c'], [1, '#67c23a']] } },
        pointer: { itemStyle: { color: '#303133' } },
        axisTick: { distance: -14, length: 5, lineStyle: { color: '#fff', width: 1 } },
        splitLine: { distance: -14, length: 14, lineStyle: { color: '#fff', width: 2 } },
        axisLabel: { distance: 22, color: '#909399', fontSize: 10 },
        detail: { valueAnimation: true, fontSize: 20, formatter: '{value}%', offsetCenter: [0, '70%'] },
        title: { offsetCenter: [0, '25%'], fontSize: 13, color: '#606266' },
        data: [{ value: value, name: name }]
      }
    },

    renderMarkdown(text) {
      if (!text) return ''
      const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      const inline = s => s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/`([^`]+)`/g, '<code>$1</code>')
      const lines = String(text).split(/\r?\n/)
      const out = []
      const isTableLine = s => /^\s*\|.*\|\s*$/.test(s) || /^\s*\|.*/.test(s)
      let i = 0
      while (i < lines.length) {
        const raw = lines[i]
        // 表格：连续以 | 开头的行（含表头、分隔行、数据行）
        if (isTableLine(raw)) {
          const block = []
          while (i < lines.length && isTableLine(lines[i])) {
            block.push(lines[i])
            i++
          }
          out.push(this.renderTable(block, esc, inline))
          continue
        }
        const line = esc(raw)
        const h = line.match(/^(#{1,6})\s+(.*)$/)
        if (h) {
          const size = h[1].length <= 2 ? '16px' : h[1].length === 3 ? '15px' : '14px'
          out.push(`<div style="font-weight:700;font-size:${size};margin:10px 0 4px;color:#303133">${inline(h[2])}</div>`)
        } else if (/^\s*[-*]\s+/.test(line)) {
          out.push(`<div class="md-li">${inline(line.replace(/^\s*[-*]\s+/, ''))}</div>`)
        } else if (/^\s*\d+[.、]\s+/.test(line)) {
          out.push(`<div class="md-li">${inline(line.replace(/^\s*\d+[.、]\s+/, ''))}</div>`)
        } else if (line.trim() === '') {
          out.push('<div style="height:6px"></div>')
        } else {
          out.push(`<div class="md-p">${inline(line)}</div>`)
        }
        i++
      }
      return out.join('')
    },

    // 将连续的 markdown 表格行转换为 HTML 表格
    renderTable(tableLines, esc, inline) {
      const rows = tableLines
        .filter(l => l.trim() !== '')
        .map(l => {
          let s = l.trim()
          if (s.startsWith('|')) s = s.slice(1)
          if (s.endsWith('|')) s = s.slice(0, -1)
          return s.split('|').map(c => inline(esc(c.trim())))
        })
      if (rows.length === 0) return ''
      const isSep = row => row.length && row.every(c => /^:?-{1,}:?$/.test(c.replace(/<[^>]+>/g, '').trim()))
      let header = null
      let bodyStart = 0
      if (rows.length >= 2 && isSep(rows[1])) {
        header = rows[0]
        bodyStart = 2
      } else {
        bodyStart = 0
      }
      let html = '<div class="md-table-wrap"><table class="md-table">'
      if (header) {
        html += '<thead><tr>' + header.map(c => `<th>${c}</th>`).join('') + '</tr></thead>'
      }
      html += '<tbody>'
      for (let r = bodyStart; r < rows.length; r++) {
        const cells = rows[r]
        if (cells.length === 0) continue
        html += '<tr>' + cells.map(c => `<td>${c}</td>`).join('') + '</tr>'
      }
      html += '</tbody></table></div>'
      return html
    },

    formatDate(d) {
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    },

    num(v) {
      const n = parseFloat(v)
      return isNaN(n) ? 0 : n
    },

    formatNum(v) {
      if (v === null || v === undefined || v === '') return '-'
      const n = parseFloat(v)
      return isNaN(n) ? v : n.toFixed(2)
    },

    formatPct(v) {
      if (v === null || v === undefined || v === '') return '-'
      const n = parseFloat(v)
      if (isNaN(n)) return v
      return (n >= 0 ? '+' : '') + n.toFixed(2) + '%'
    },

    pctClass(v) {
      const n = parseFloat(v)
      if (isNaN(n) || n === 0) return ''
      return n > 0 ? 'up' : 'down'
    },

    // 判断数据完整性条目是否缺失（条数为 0）
    dataMissing(s) {
      return /：0 (个|条)/.test(s)
    }
  }
}
</script>

<style scoped>
.strategy-analysis {
  padding: 16px;
}

/* 复盘卡片 */
.review-card {
  margin-bottom: 16px;
  border: none;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.review-title {
  display: flex;
  align-items: center;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.review-title i {
  color: #409eff;
  font-size: 22px;
  margin-right: 8px;
}

.review-date {
  margin-left: 12px;
  font-size: 13px;
  font-weight: 400;
  color: #909399;
}

.review-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.review-time {
  font-size: 12px;
  color: #909399;
}

.review-divider {
  margin: 12px 0;
}

.review-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 40px 0;
  justify-content: center;
  color: #606266;
  font-size: 14px;
}

.review-loading i {
  color: #409eff;
  font-size: 20px;
}

.review-notice {
  margin-bottom: 12px;
}

/* 复盘内容固定高度，超出显示滚动条 */
.review-content-scroll {
  max-height: 440px;
  overflow-y: auto;
  border: 1px solid #f0f2f5;
  border-radius: 6px;
  padding: 12px 14px;
  background: #fafbfc;
}

.review-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
  padding: 4px 2px;
}

::v-deep .markdown-body .md-p {
  margin: 2px 0;
}

::v-deep .markdown-body .md-li {
  margin: 3px 0 3px 4px;
  padding-left: 12px;
  position: relative;
}

::v-deep .markdown-body .md-li::before {
  content: '•';
  position: absolute;
  left: 0;
  color: #409eff;
}

::v-deep .markdown-body code {
  background: #f4f4f5;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 13px;
  color: #c7254e;
}

/* markdown 表格样式 */
::v-deep .markdown-body .md-table-wrap {
  overflow-x: auto;
  margin: 8px 0;
}

::v-deep .markdown-body .md-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  line-height: 1.5;
}

::v-deep .markdown-body .md-table th,
::v-deep .markdown-body .md-table td {
  border: 1px solid #e4e7ed;
  padding: 6px 10px;
  text-align: left;
}

::v-deep .markdown-body .md-table th {
  background: #f5f7fa;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

::v-deep .markdown-body .md-table tr:nth-child(even) td {
  background: #fafbfc;
}

/* 市场数据速览 */
.market-snapshot {
  margin-top: 16px;
  border-top: 1px dashed #ebeef5;
  padding-top: 12px;
}

.snapshot-block {
  margin-bottom: 12px;
}

.snapshot-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.index-list {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}

.index-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
}

.index-name {
  color: #303133;
  font-weight: 600;
}

.index-price {
  color: #606266;
}

.sector-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sector-tag {
  margin: 0;
}

/* 统计卡片 */
.analysis-card {
  border: none;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.analysis-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.analysis-title {
  display: flex;
  align-items: center;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.analysis-title i {
  color: #409eff;
  font-size: 22px;
  margin-right: 8px;
}

.holding-tag {
  margin-left: 10px;
}

.analysis-filter {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-sep {
  color: #909399;
}

.filter-label {
  color: #606266;
  font-size: 13px;
}

.kpi-row {
  margin-bottom: 16px;
}

.kpi-card {
  background: linear-gradient(135deg, #f0f6ff 0%, #ffffff 100%);
  border: 1px solid #e8f0fe;
  border-radius: 8px;
  padding: 18px 16px;
  text-align: center;
  margin-bottom: 16px;
  transition: transform 0.2s;
}

.kpi-card:hover {
  transform: translateY(-3px);
}

.kpi-label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 13px;
  color: #909399;
  margin-bottom: 10px;
}

.kpi-value {
  font-size: 30px;
  font-weight: 700;
  color: #303133;
}

.kpi-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}

.up {
  color: #f56c6c !important;
}

.down {
  color: #67c23a !important;
}

.chart-row {
  margin-top: 4px;
}

.chart-box {
  border: 1px solid #f0f2f5;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.chart {
  width: 100%;
  height: 280px;
}

@media screen and (max-width: 768px) {
  .kpi-value {
    font-size: 22px;
  }
}
</style>
