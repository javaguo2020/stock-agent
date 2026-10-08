import request from '@/utils/request'

// 缠论多级别分析（结论 + 各级别中枢列表）
export function analyzeChan(data) {
  return request({
    url: '/stock/chan/analyze',
    method: 'post',
    data: data
  })
}

// K线数据（画图用，复用现有接口）
export function getKlineData(data) {
  return request({
    url: '/stock/kline',
    method: 'post',
    data: data
  })
}
