import * as echarts from 'echarts'

const darkTooltip = {
  backgroundColor: 'rgba(10,14,23,0.95)',
  borderColor: '#1e3a5f',
  textStyle: { color: '#8892b0', fontSize: 12 }
}

const cyanGradient = (x, y, x2, y2) => new echarts.graphic.LinearGradient(x, y, x2, y2, [
  { offset: 0, color: '#00d4ff' }, { offset: 1, color: '#0066cc' }
])

/** Donut pie chart */
export function pieOption(data, nameField = 'name', valueField = 'value') {
  return {
    tooltip: { ...darkTooltip, trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [{
      type: 'pie', radius: ['45%', '72%'],
      itemStyle: { borderRadius: 6, borderColor: '#0a0e17', borderWidth: 3 },
      label: { color: '#8892b0', fontSize: 11 },
      data: data.map(d => ({ name: d[nameField], value: d[valueField] }))
    }]
  }
}

/** Vertical bar chart with gradient fill */
export function barOption(xData, seriesData, name = '数量') {
  return {
    tooltip: { ...darkTooltip, trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: xData, axisLabel: { color: '#8892b0', rotate: xData.length > 10 ? 45 : 0 } },
    yAxis: { type: 'value', axisLabel: { color: '#8892b0' } },
    series: [{
      name, type: 'bar', barWidth: '60%', data: seriesData,
      itemStyle: { borderRadius: [4, 4, 0, 0], color: cyanGradient(0, 0, 0, 1) }
    }]
  }
}

/** Horizontal bar chart (for rankings) */
export function horizontalBarOption(yData, seriesData, name = '数量') {
  const max = Math.max(...seriesData, 1)
  return {
    tooltip: { ...darkTooltip, trigger: 'axis' },
    grid: { left: 100, right: 40, top: 10, bottom: 20 },
    yAxis: { type: 'category', data: yData, inverse: true, axisLabel: { color: '#8892b0', width: 90, overflow: 'truncate' } },
    xAxis: { type: 'value', max: Math.ceil(max * 1.2), axisLabel: { color: '#8892b0' } },
    series: [{
      name, type: 'bar', barWidth: '50%', data: seriesData,
      label: { show: true, position: 'right', color: '#8892b0', fontSize: 11 },
      itemStyle: { borderRadius: [0, 4, 4, 0], color: cyanGradient(0, 0, 1, 0) }
    }]
  }
}

/** Smooth line chart with area fill */
export function lineOption(xData, seriesData, name = '趋势') {
  return {
    tooltip: { ...darkTooltip, trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: xData, axisLabel: { color: '#8892b0', rotate: xData.length > 10 ? 45 : 0 } },
    yAxis: { type: 'value', axisLabel: { color: '#8892b0' } },
    series: [{
      name, type: 'line', data: seriesData, smooth: true, symbol: 'circle', symbolSize: 6,
      lineStyle: { color: '#00d4ff', width: 2 },
      itemStyle: { color: '#00d4ff' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: 'rgba(0,212,255,0.25)' }, { offset: 1, color: 'rgba(0,212,255,0)' }
      ])}
    }]
  }
}

/** Funnel chart (for aging distribution) */
export function funnelOption(data) {
  const colors = ['#ff4444', '#ff8800', '#ffcc00', '#00ff88']
  return {
    tooltip: { ...darkTooltip, trigger: 'item' },
    series: [{
      type: 'funnel', left: '15%', top: 20, bottom: 20, width: '70%',
      sort: 'descending', gap: 2,
      label: { show: true, position: 'inside', color: '#fff', fontSize: 12, formatter: '{b}\n{c}单' },
      itemStyle: { borderColor: '#0a0e17', borderWidth: 2 },
      data: data.map((d, i) => ({ name: d.name, value: d.value, itemStyle: { color: colors[i] || '#00d4ff' } }))
    }]
  }
}

/** Gauge chart */
export function gaugeOption(value, title, max = 100) {
  return {
    series: [{
      type: 'gauge', radius: '90%',
      startAngle: 210, endAngle: -30,
      min: 0, max,
      axisLine: { lineStyle: { width: 12, color: [[0.6, '#ff4444'], [0.8, '#ff8800'], [1, '#00ff88']] } },
      pointer: { length: '60%', width: 4, itemStyle: { color: '#00d4ff' } },
      axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false },
      title: { offsetCenter: [0, '85%'], color: '#8892b0', fontSize: 11 },
      detail: { valueAnimation: true, formatter: '{value}%', color: '#00d4ff', fontSize: 18, offsetCenter: [0, '55%'] },
      data: [{ value: Math.min(value, max), name: title }]
    }]
  }
}

/** Dual bar comparison chart (period-over-period) */
export function dualBarOption(xData, curData, prevData, curName = '本期', prevName = '上期') {
  return {
    tooltip: { ...darkTooltip, trigger: 'axis' },
    legend: { data: [curName, prevName], textStyle: { color: '#8892b0', fontSize: 11 }, top: 0 },
    grid: { left: 50, right: 20, top: 30, bottom: 20 },
    xAxis: { type: 'category', data: xData, axisLabel: { color: '#8892b0' } },
    yAxis: { type: 'value', axisLabel: { color: '#8892b0' } },
    series: [
      { name: curName, type: 'bar', barWidth: '35%', data: curData, itemStyle: { borderRadius: [4, 4, 0, 0], color: '#00d4ff' } },
      { name: prevName, type: 'bar', barWidth: '35%', data: prevData, itemStyle: { borderRadius: [4, 4, 0, 0], color: 'rgba(0,212,255,0.3)' } }
    ]
  }
}

/** Mixed bar + line chart */
export function mixedBarLineOption(xData, barData, lineData, barName = '数量', lineName = '趋势') {
  return {
    tooltip: { ...darkTooltip, trigger: 'axis' },
    legend: { data: [barName, lineName], textStyle: { color: '#8892b0', fontSize: 11 }, top: 0 },
    grid: { left: 50, right: 55, top: 30, bottom: 20 },
    xAxis: { type: 'category', data: xData, axisLabel: { color: '#8892b0' } },
    yAxis: [
      { type: 'value', name: barName, nameTextStyle: { color: '#8892b0' }, axisLabel: { color: '#8892b0' } },
      { type: 'value', name: lineName, nameTextStyle: { color: '#8892b0' }, axisLabel: { color: '#8892b0' } }
    ],
    series: [
      { name: barName, type: 'bar', barWidth: '60%', data: barData, itemStyle: { borderRadius: [4, 4, 0, 0], color: cyanGradient(0, 0, 0, 1) } },
      { name: lineName, type: 'line', yAxisIndex: 1, data: lineData, smooth: true, lineStyle: { color: '#ff8800', width: 2 }, itemStyle: { color: '#ff8800' } }
    ]
  }
}

export function statCardData(d) {
  return { ...d }
}
