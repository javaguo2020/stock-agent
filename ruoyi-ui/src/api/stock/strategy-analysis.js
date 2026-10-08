import request from '@/utils/request'

// =============== 战法分析 ===============

// 计算理论胜率/理论收益率/实际成功率/实际收益率
export function getAnalysis(params) {
  return request({
    url: '/stock/strategy-analysis/analysis',
    method: 'get',
    params: params
  })
}

// 查询当天交易复盘状态（只读，可轮询；status = NONE/GENERATING/DONE）
export function getTradeReviewStatus() {
  return request({
    url: '/stock/strategy-analysis/trade-review/status',
    method: 'get'
  })
}

// 触发当天交易复盘异步生成（后台执行，立即返回）
export function generateTradeReview() {
  return request({
    url: '/stock/strategy-analysis/trade-review/generate',
    method: 'post'
  })
}
