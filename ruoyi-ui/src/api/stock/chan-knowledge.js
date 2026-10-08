import request from '@/utils/request'

// 查询缠论知识库列表
export function listStockChanKnowledge(query) {
  return request({
    url: '/stock/chan/knowledge/list',
    method: 'get',
    params: query
  })
}

// 查询缠论知识库详情
export function getStockChanKnowledge(id) {
  return request({
    url: '/stock/chan/knowledge/' + id,
    method: 'get'
  })
}

// 新增缠论知识库
export function addStockChanKnowledge(data) {
  return request({
    url: '/stock/chan/knowledge',
    method: 'post',
    data: data
  })
}

// 修改缠论知识库
export function updateStockChanKnowledge(data) {
  return request({
    url: '/stock/chan/knowledge',
    method: 'put',
    data: data
  })
}

// 删除缠论知识库
export function delStockChanKnowledge(ids) {
  return request({
    url: '/stock/chan/knowledge/' + ids,
    method: 'delete'
  })
}