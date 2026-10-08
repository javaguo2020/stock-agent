import request from '@/utils/request'

// 提交多Agent分析（异步，返回 analysisSessionId 供轮询）
export function submitAnalysis(data) {
  return request({
    url: '/agent/analysis/submit',
    method: 'post',
    data: data
  })
}

// 查询会话状态与结果（可反复轮询）
export function getStatus(sessionId) {
  return request({
    url: '/agent/analysis/status/' + sessionId,
    method: 'get'
  })
}

// 一键恢复中断的分析
export function resumeAnalysis(sessionId) {
  return request({
    url: '/agent/analysis/resume/' + sessionId,
    method: 'post'
  })
}

// 方法调用模式（同步阻塞+熔断降级，调试用，耗时较长）
export function quickAnalysis(data) {
  return request({
    url: '/agent/analysis/quick',
    method: 'post',
    data: data,
    timeout: 360000
  })
}
