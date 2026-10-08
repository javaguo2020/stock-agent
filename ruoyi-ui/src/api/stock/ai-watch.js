import request from '@/utils/request'

// 查询AI实时关注股票列表
export function listAiWatch(query) {
  return request({
    url: '/stock/ai-watch/list',
    method: 'get',
    params: query
  })
}

// 查询AI实时关注股票详细
export function getAiWatch(id) {
  return request({
    url: '/stock/ai-watch/' + id,
    method: 'get'
  })
}

// 新增AI实时关注股票
export function addAiWatch(data) {
  return request({
    url: '/stock/ai-watch',
    method: 'post',
    data: data
  })
}

// 修改AI实时关注股票
export function updateAiWatch(data) {
  return request({
    url: '/stock/ai-watch',
    method: 'put',
    data: data
  })
}

// 删除AI实时关注股票
export function delAiWatch(ids) {
  return request({
    url: '/stock/ai-watch/' + ids,
    method: 'delete'
  })
}