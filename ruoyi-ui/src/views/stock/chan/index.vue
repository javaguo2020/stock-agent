<template>
  <div class="app-container chan-page">
    <!-- 查询区 -->
    <el-card shadow="never">
      <el-form :inline="true" :model="query" @submit.native.prevent>
        <el-form-item label="股票代码">
          <el-input v-model="query.stockCode" placeholder="如 002281 / 000001.SZ" style="width: 180px" clearable
            @keyup.enter.native="handleAnalyze" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="query.name" placeholder="股票名称(可选)" style="width: 140px" clearable />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="query.levels" multiple collapse-tags placeholder="不选=全部级别" style="width: 320px">
            <el-option v-for="lv in levelOptions" :key="lv.value" :label="lv.label" :value="lv.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识库">
          <el-switch v-model="query.kb" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" icon="el-icon-data-analysis" @click="handleAnalyze">开始分析</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <template v-if="result">
      <!-- 多级别结论 -->
      <el-card shadow="never" class="mt-card">
        <div slot="header">
          <span>{{ result.symbol }}{{ result.name ? ' ' + result.name : '' }} 缠论多级别结论</span>
        </div>
        <el-row :gutter="12">
          <el-col v-for="lv in result.levels" :key="lv.level" :xs="12" :sm="8" :md="6" :lg="4" class="mb-8">
            <el-card :class="['level-card', 'state-' + stateClass(lv)]" shadow="hover" body-style="padding:12px">
              <div class="level-name">{{ lv.name }}</div>
              <div class="level-state">
                <el-tag :type="stateTagType(lv)" size="mini">{{ lv.state }}<span v-if="lv.trendDirection"> · {{ lv.trendDirection }}</span></el-tag>
              </div>
              <template v-if="lv.error">
                <div class="level-err">{{ lv.error }}</div>
              </template>
              <template v-else>
                <div class="level-line">走势类型：{{ lv.trendType || '-' }}</div>
                <div class="level-line">中枢ZG：<b>{{ lv.pivotTopZg }}</b></div>
                <div class="level-line">中枢ZD：<b>{{ lv.pivotBottomZd }}</b></div>
                <div class="level-line">状态级别：{{ lv.pivotLevel || '-' }}</div>
                <div class="level-line">背驰：{{ beichiLabel(lv) }}</div>
                <div class="level-line">结构：笔{{ lv.biCount }} / 线段{{ lv.segmentCount }}</div>
                <div class="level-line">中枢数：笔{{ lv.biPivotsCount }} + 线段{{ lv.segmentPivotsCount }}</div>
                <div class="level-line">MACD：{{ lv.dif }} / {{ lv.dea }} / {{ lv.hist }}</div>
              </template>
            </el-card>
          </el-col>
        </el-row>

        <el-divider content-position="left">操作建议</el-divider>
        <div v-if="result.advice" class="advice-wrap">
          <div class="advice-tag-row">
            <el-tag :type="adviceTagType(result.advice.tag)" size="medium">{{ result.advice.tag }}</el-tag>
            <span class="advice-tip">（价格位均来自代码计算）</span>
          </div>
          <div class="advice-cols">
            <div class="advice-col">
              <div class="advice-title">🟢 持有者</div>
              <ol class="advice-list">
                <li v-for="(t, i) in result.advice.holder" :key="i">{{ t }}</li>
              </ol>
            </div>
            <div class="advice-col">
              <div class="advice-title">🔴 空仓/想买入者</div>
              <ol class="advice-list">
                <li v-for="(t, i) in result.advice.buyer" :key="i">{{ t }}</li>
              </ol>
            </div>
            <div class="advice-col">
              <div class="advice-title">综合评估</div>
              <ul class="advice-list">
                <li>{{ result.advice.assessment.shortTerm }}</li>
                <li>{{ result.advice.assessment.midTerm }}</li>
                <li>{{ result.advice.assessment.longTerm }}</li>
                <li><b>{{ result.advice.assessment.overall }}</b></li>
              </ul>
            </div>
          </div>
        </div>
        <el-alert v-else title="已关闭操作建议" type="info" :closable="false" />
      </el-card>

      <!-- 各级别最近中枢 -->
      <el-card shadow="never" class="mt-card">
        <div slot="header">
          <span>各级别最近中枢</span>
          <el-radio-group v-model="pivotFilter" size="mini" style="margin-left: 16px">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button v-for="lv in result.levels" :key="lv.level" :label="lv.level">{{ lv.name }}</el-radio-button>
          </el-radio-group>
        </div>
        <el-table :data="pivotRows" size="small" stripe :max-height="380" empty-text="该级别暂无有效中枢">
          <el-table-column prop="levelLabel" label="级别" width="90" />
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="startDate" label="起始" min-width="140" />
          <el-table-column prop="endDate" label="结束" min-width="140" />
          <el-table-column prop="zg" label="中枢顶 ZG" width="110" align="right" />
          <el-table-column prop="zd" label="中枢底 ZD" width="110" align="right" />
          <el-table-column prop="segments" label="段数" width="70" align="center" />
        </el-table>
      </el-card>

      <!-- 三类买卖点 -->
      <el-card shadow="never" class="mt-card">
        <div slot="header">
          <span>三类买卖点</span>
          <el-radio-group v-model="pointFilter" size="mini" style="margin-left: 16px">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button v-for="lv in result.levels" :key="lv.level" :label="lv.level">{{ lv.name }}</el-radio-button>
          </el-radio-group>
        </div>
        <el-table :data="buySellRows" size="small" stripe :max-height="380" empty-text="该级别暂无买卖点信号">
          <el-table-column prop="levelLabel" label="级别" width="90" />
          <el-table-column label="类型" width="110">
            <template slot-scope="{ row }">
              <el-tag :type="row.direction === 'buy' ? 'success' : 'danger'" size="mini">{{ row.typeLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="date" label="日期" min-width="140" />
          <el-table-column prop="price" label="价格" width="100" align="right" />
          <el-table-column prop="description" label="说明" min-width="260" />
        </el-table>
      </el-card>

      <!-- K线走势与中枢 -->
      <el-card shadow="never" class="mt-card">
        <div slot="header">
          <span>K线走势与中枢变化</span>
          <span class="chart-tip">（点击级别切换；色块=中枢区间，虚线=当前中枢 ZG/ZD）</span>
        </div>
        <el-tabs v-model="chartLevel" @tab-click="loadChart">
          <el-tab-pane v-for="lv in chartLevelOptions" :key="lv.value" :label="lv.label" :name="lv.value" lazy />
        </el-tabs>
        <div v-loading="chartLoading" ref="chartBox" class="chart-box"></div>
        <div class="chart-legend">
          <span class="legend-item"><i class="legend-color legend-bi"></i>笔中枢区间</span>
          <span class="legend-item"><i class="legend-color legend-seg"></i>线段中枢区间</span>
          <span class="legend-item"><i class="legend-color legend-cur"></i>当前中枢 ZG/ZD</span>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { analyzeChan, getKlineData } from '@/api/stock/chan'

const LEVEL_OPTIONS = [
  { value: '5', label: '5分钟' },
  { value: '15', label: '15分钟' },
  { value: '30', label: '30分钟' },
  { value: '60', label: '60分钟' },
  { value: 'd', label: '日线' },
  { value: 'w', label: '周线' },
  { value: 'm', label: '月线' },
  { value: 'y', label: '年线' }
]

const LEVEL_MAP = {}
LEVEL_OPTIONS.forEach(l => { LEVEL_MAP[l.value] = l.label })

// 画图时每个级别拉取的K线条数
const CHART_LIMITS = {
  '5': 480, '15': 300, '30': 300, '60': 240,
  'd': 300, 'w': 200, 'm': 160, 'y': 120
}

export default {
  name: 'StockChan',
  data() {
    return {
      loading: false,
      chartLoading: false,
      query: {
        stockCode: '',
        name: '',
        levels: [],
        adjustType: 'n',
        kb: false
      },
      levelOptions: LEVEL_OPTIONS,
      result: null,
      pivotFilter: '',
      pointFilter: '',
      chartLevel: 'd',
      chartInstance: null,
      klineCache: {}
    }
  },
  computed: {
    chartLevelOptions() {
      if (!this.result) return []
      return this.result.levels
        .filter(l => l.error == null && l.state !== '数据不足' && l.state !== '取数失败')
        .map(l => ({ value: l.level, label: l.name }))
    },
    pivotRows() {
      if (!this.result) return []
      const rows = []
      const levelMap = {}
      this.result.levels.forEach(l => { levelMap[l.level] = l })
      this.result.levels.forEach(lv => {
        if (this.pivotFilter && lv.level !== this.pivotFilter) return
        const pushPivots = (pivots, type) => {
          if (!pivots || !pivots.length) return
          // 列表按时间升序，取最近几个
          const recent = pivots.slice(-6)
          recent.forEach(p => {
            rows.push({
              level: lv.level,
              levelLabel: lv.name,
              type,
              startDate: p.startDate,
              endDate: p.endDate,
              zg: p.zg,
              zd: p.zd,
              segments: p.segments
            })
          })
        }
        pushPivots(lv.segmentPivots, '线段中枢')
        pushPivots(lv.biPivots, '笔中枢')
      })
      // 按级别顺序排序
      rows.sort((a, b) => {
        const ia = LEVEL_OPTIONS.findIndex(o => o.value === a.level)
        const ib = LEVEL_OPTIONS.findIndex(o => o.value === b.level)
        return ia - ib
      })
      return rows
    },
    buySellRows() {
      if (!this.result) return []
      const rows = []
      this.result.levels.forEach(lv => {
        if (this.pointFilter && lv.level !== this.pointFilter) return
        if (!lv.buySellPoints || !lv.buySellPoints.length) return
        lv.buySellPoints.forEach(p => {
          rows.push({
            level: lv.level,
            levelLabel: lv.name,
            typeLabel: p.typeLabel,
            type: p.type,
            direction: p.direction,
            date: p.date,
            price: p.price,
            description: p.description
          })
        })
      })
      rows.sort((a, b) => {
        const ia = LEVEL_OPTIONS.findIndex(o => o.value === a.level)
        const ib = LEVEL_OPTIONS.findIndex(o => o.value === b.level)
        if (ia !== ib) return ia - ib
        return (a.date || '').localeCompare(b.date || '')
      })
      return rows
    }
  },
  mounted() {
    this.chartLevel = 'd'
  },
  beforeDestroy() {
    this.disposeChart()
  },
  methods: {
    handleAnalyze() {
      const code = (this.query.stockCode || '').trim()
      if (!code) {
        this.$message.warning('请输入股票代码')
        return
      }
      const params = {
        stockCode: code,
        name: this.query.name || '',
        adjustType: this.query.adjustType || 'n',
        kb: !!this.query.kb
      }
      if (this.query.levels && this.query.levels.length) {
        params.levels = this.query.levels.join(',')
      }
      this.loading = true
      analyzeChan(params).then(res => {
        if (res.code === 200) {
          this.result = res.data
          this.klineCache = {}
          this.pivotFilter = ''
          this.pointFilter = ''
          // 默认选择日线（或第一个有数据的级别）
          const opts = this.chartLevelOptions
          this.chartLevel = opts.some(o => o.value === 'd') ? 'd' : (opts.length ? opts[0].value : 'd')
          this.$nextTick(() => this.loadChart())
        } else {
          this.$message.error(res.msg || '分析失败')
        }
      }).catch(err => {
        this.$message.error('分析失败：' + (err.msg || err.message || '未知错误'))
      }).finally(() => {
        this.loading = false
      })
    },

    stateClass(lv) {
      if (lv.state === '趋势' && lv.trendDirection === '向上') return 'up'
      if (lv.state === '趋势' && lv.trendDirection === '向下') return 'down'
      if (lv.state === '中枢震荡') return 'osc'
      return 'na'
    },

    stateTagType(lv) {
      const c = this.stateClass(lv)
      return c === 'up' ? 'success' : (c === 'down' ? 'danger' : (c === 'osc' ? 'warning' : 'info'))
    },

    beichiLabel(lv) {
      if (lv.divergenceTop) return '顶背驰'
      if (lv.divergenceBottom) return '底背驰'
      return '无'
    },

    adviceTagType(tag) {
      if (!tag) return 'info'
      if (tag.indexOf('多') >= 0 && tag.indexOf('空') < 0) return 'success'
      if (tag.indexOf('空') >= 0 && tag.indexOf('多') < 0) return 'danger'
      return 'warning'
    },

    levelResult(level) {
      if (!this.result) return null
      return this.result.levels.find(l => l.level === level) || null
    },

    loadChart() {
      if (!this.result || !this.chartLevel) return
      const level = this.chartLevel
      const code = this.result.symbol
      if (this.klineCache[level]) {
        this.renderChart(level, this.klineCache[level])
        return
      }
      this.chartLoading = true
      getKlineData({
        stockCode: code,
        period: level,
        adjustType: 'n',
        limit: CHART_LIMITS[level] || 300
      }).then(res => {
        if (res.code === 200) {
          this.klineCache[level] = res.data || []
          this.$nextTick(() => this.renderChart(level, res.data || []))
        } else {
          this.$message.error('获取K线失败：' + (res.msg || ''))
        }
      }).catch(err => {
        this.$message.error('获取K线失败：' + (err.msg || err.message || '未知错误'))
      }).finally(() => {
        this.chartLoading = false
      })
    },

    renderChart(level, klineData) {
      if (!klineData || !klineData.length) {
        this.disposeChart()
        return
      }
      const categories = klineData.map(d => d.t)
      const values = klineData.map(d => [d.o, d.c, d.l, d.h])
      const volumes = klineData.map(d => d.v)
      const lv = this.levelResult(level)

      const biAreas = this.buildMarkAreas(lv ? lv.biPivots : null, 'rgba(64,158,255,0.12)')
      const segAreas = this.buildMarkAreas(lv ? lv.segmentPivots : null, 'rgba(255,153,0,0.16)')

      const markLineData = []
      if (lv && lv.pivotTopZg != null) {
        markLineData.push({
          yAxis: lv.pivotTopZg,
          name: '当前ZG',
          lineStyle: { color: '#f56c6c', type: 'dashed' },
          label: { formatter: 'ZG ' + lv.pivotTopZg, position: 'insideEndTop', color: '#f56c6c' }
        })
      }
      if (lv && lv.pivotBottomZd != null) {
        markLineData.push({
          yAxis: lv.pivotBottomZd,
          name: '当前ZD',
          lineStyle: { color: '#67c23a', type: 'dashed' },
          label: { formatter: 'ZD ' + lv.pivotBottomZd, position: 'insideEndBottom', color: '#67c23a' }
        })
      }

      const option = {
        animation: false,
        title: {
          text: LEVEL_MAP[level] + ' K线 · 缠论中枢',
          left: 0,
          textStyle: { fontSize: 14 }
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'cross' },
          formatter: params => {
            if (!params || !params.length) return ''
            const idx = params[0].dataIndex
            const d = klineData[idx]
            let html = '<div style="font-size:13px;line-height:1.8">' + d.t
            html += '<br/>开 ' + d.o + '　收 <b>' + d.c + '</b>'
            html += '<br/>高 ' + d.h + '　低 ' + d.l
            html += '<br/>量 ' + (d.v / 10000).toFixed(2) + ' 万'
            html += '</div>'
            return html
          }
        },
        legend: { data: [LEVEL_MAP[level], '成交量'], top: 22 },
        grid: [
          { left: '3%', right: '3%', top: 50, height: '62%' },
          { left: '3%', right: '3%', top: '78%', height: '14%' }
        ],
        xAxis: [
          {
            type: 'category',
            data: categories,
            boundaryGap: false,
            axisLine: { onZero: false },
            splitLine: { show: false },
            axisLabel: { hideOverlap: true }
          },
          {
            type: 'category',
            gridIndex: 1,
            data: categories,
            boundaryGap: false,
            axisLine: { onZero: false },
            splitLine: { show: false },
            axisLabel: { show: false }
          }
        ],
        yAxis: [
          { scale: true, splitArea: { show: true } },
          { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { show: false } }
        ],
        dataZoom: [
          { type: 'inside', start: 40, end: 100, xAxisIndex: [0, 1] },
          { type: 'slider', top: '93%', start: 40, end: 100, xAxisIndex: [0, 1] }
        ],
        series: [
          {
            name: LEVEL_MAP[level],
            type: 'candlestick',
            data: values,
            itemStyle: {
              color: '#ef232a',
              color0: '#14b143',
              borderColor: '#ef232a',
              borderColor0: '#14b143'
            },
            markArea: {
              silent: true,
              data: segAreas.concat(biAreas)
            },
            markLine: {
              symbol: ['none', 'none'],
              data: markLineData
            }
          },
          {
            name: '成交量',
            type: 'bar',
            xAxisIndex: 1,
            yAxisIndex: 1,
            data: volumes,
            itemStyle: {
              color: p => {
                const d = klineData[p.dataIndex]
                return d && d.c >= d.o ? '#ef232a' : '#14b143'
              }
            }
          }
        ]
      }

      this.disposeChart()
      this.chartInstance = echarts.init(this.$refs.chartBox)
      this.chartInstance.setOption(option)
      window.addEventListener('resize', this.handleResize)
    },

    buildMarkAreas(pivots, color) {
      const areas = []
      if (!pivots || !pivots.length) return areas
      // 当前K线日期集合（用于过滤超出可视范围的旧中枢）
      const level = this.chartLevel
      const kdata = this.klineCache[level] || []
      const dates = kdata.map(d => d.t)
      pivots.forEach(p => {
        if (!p.startDate || !p.endDate) return
        if (dates.indexOf(p.startDate) < 0 || dates.indexOf(p.endDate) < 0) return
        areas.push([
          {
            name: '中枢',
            xAxis: p.startDate,
            yAxis: p.zd,
            itemStyle: { color },
            label: {
              show: true,
              position: 'insideTop',
              formatter: '中枢 ' + p.zd + '~' + p.zg,
              fontSize: 10,
              color: '#666'
            }
          },
          { xAxis: p.endDate, yAxis: p.zg }
        ])
      })
      return areas
    },

    handleResize() {
      if (this.chartInstance) {
        this.chartInstance.resize()
      }
    },

    disposeChart() {
      if (this.chartInstance) {
        this.chartInstance.dispose()
        this.chartInstance = null
      }
      window.removeEventListener('resize', this.handleResize)
    }
  }
}
</script>

<style scoped>
.chan-page .mt-card {
  margin-top: 16px;
}
.mb-8 {
  margin-bottom: 8px;
}
.level-card .level-name {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
}
.level-card .level-state {
  margin-bottom: 6px;
}
.level-card .level-line {
  font-size: 12px;
  line-height: 1.9;
  color: #606266;
}
.level-card .level-err {
  font-size: 12px;
  color: #f56c6c;
}
.level-card.state-up {
  border-top: 3px solid #67c23a;
}
.level-card.state-down {
  border-top: 3px solid #f56c6c;
}
.level-card.state-osc {
  border-top: 3px solid #e6a23c;
}
.advice-wrap {
  margin-top: 4px;
}
.advice-tag-row {
  margin-bottom: 10px;
}
.advice-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.advice-cols {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
.advice-col {
  flex: 1;
  min-width: 260px;
}
.advice-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.advice-list {
  margin: 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 2;
  color: #303133;
}
.chart-box {
  width: 100%;
  height: 520px;
}
.chart-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
.chart-legend {
  margin-top: 8px;
  font-size: 12px;
  color: #606266;
}
.legend-item {
  margin-right: 18px;
}
.legend-color {
  display: inline-block;
  width: 14px;
  height: 10px;
  margin-right: 4px;
  vertical-align: middle;
  border-radius: 2px;
}
.legend-bi {
  background: rgba(64, 158, 255, 0.35);
}
.legend-seg {
  background: rgba(255, 153, 0, 0.4);
}
.legend-cur {
  border-top: 2px dashed #f56c6c;
  height: 0;
}
</style>
