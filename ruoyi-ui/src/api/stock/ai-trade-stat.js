import request from '@/utils/request'

// 查询AI实时交易统计列表
export function listAiTradeStat(query) {
  return request({
    url: '/stock/ai-trade-stat/list',
    method: 'get',
    params: query
  })
}

// 查询AI实时交易统计详细
export function getAiTradeStat(id) {
  return request({
    url: '/stock/ai-trade-stat/' + id,
    method: 'get'
  })
}

// 新增AI实时交易统计
export function addAiTradeStat(data) {
  return request({
    url: '/stock/ai-trade-stat',
    method: 'post',
    data: data
  })
}

// 修改AI实时交易统计
export function updateAiTradeStat(data) {
  return request({
    url: '/stock/ai-trade-stat',
    method: 'put',
    data: data
  })
}

// 删除AI实时交易统计
export function delAiTradeStat(ids) {
  return request({
    url: '/stock/ai-trade-stat/' + ids,
    method: 'delete'
  })
}