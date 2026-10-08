<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="股票代码" prop="stockCode">
        <el-input v-model="queryParams.stockCode" placeholder="请输入股票代码" clearable style="width: 160px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="股票名称" prop="stockName">
        <el-input v-model="queryParams.stockName" placeholder="请输入股票名称" clearable style="width: 160px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="交易类型" prop="tradeType">
        <el-select v-model="queryParams.tradeType" placeholder="请选择交易类型" clearable style="width: 120px">
          <el-option label="买入" value="BUY" />
          <el-option label="卖出" value="SELL" />
        </el-select>
      </el-form-item>
      <el-form-item label="交易状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择交易状态" clearable style="width: 140px" @change="handleQuery">
          <el-option label="待确认" value="待确认" />
          <el-option label="已买入" value="已买入" />
          <el-option label="已重复" value="已重复" />
          <el-option label="已卖出" value="已卖出" />
          <el-option label="卖出失败" value="卖出失败" />
          <el-option label="买入失败" value="买入失败" />
        </el-select>
      </el-form-item>
      <el-form-item label="交易日期">
        <el-date-picker
          v-model="dateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 工具栏 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete">删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 表格 -->
    <el-table v-loading="loading" :data="statList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="股票编码" align="center" prop="stockCode" width="130" />
      <el-table-column label="股票名称" align="center" prop="stockName" width="130" show-overflow-tooltip />
      <el-table-column label="交易类型" align="center" width="90">
        <template slot-scope="scope">
          <el-tag :type="scope.row.tradeType === 'BUY' ? 'danger' : 'warning'">{{ scope.row.tradeType === 'BUY' ? '买入' : '卖出' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前价格" align="center" prop="price" width="100" />
      <el-table-column label="交易日期" align="center" prop="tradeDate" width="110" />
      <el-table-column label="交易状态" align="center" width="100">
        <template slot-scope="scope">
          <el-tag :type="statusType(scope.row.status)">{{ scope.row.status || '待确认' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="策略号" align="center" prop="strategyNo" width="90" />
      <el-table-column label="触发理由" align="center" prop="reasoning" min-width="220" show-overflow-tooltip />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="80">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script>
import { listAiTradeStat, delAiTradeStat } from "@/api/stock/ai-trade-stat"

export default {
  name: 'AiTradeStat',
  data() {
    return {
      loading: true,
      ids: [],
      multiple: true,
      showSearch: true,
      total: 0,
      statList: [],
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 20,
        stockCode: null,
        stockName: null,
        tradeType: null,
        status: null,
        beginDate: null,
        endDate: null
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      this.queryParams.beginDate = this.dateRange && this.dateRange.length > 0 ? this.dateRange[0] : null
      this.queryParams.endDate = this.dateRange && this.dateRange.length > 0 ? this.dateRange[1] : null
      listAiTradeStat(this.queryParams).then(response => {
        this.statList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.multiple = !selection.length
    },
    handleDelete(row) {
      const ids = (row && row.id) || this.ids
      this.$modal.confirm('是否确认删除选中的AI交易统计数据？').then(() => delAiTradeStat(ids)).then(() => {
        this.getList()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    statusType(status) {
      switch (status) {
        case '已买入': return 'success'
        case '已卖出': return 'warning'
        case '已重复': return 'info'
        case '卖出失败':
        case '买入失败': return 'danger'
        default: return 'info'
      }
    }
  }
}
</script>