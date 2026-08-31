import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

vi.mock('@/api/iot/dashboard', () => ({
  getDashboardStats: vi.fn().mockResolvedValue({ data: { devices: {}, alarms: {}, workOrders: {}, inspections: {} } })
}))

vi.mock('@/api/iot/monitor', () => ({
  getMonitorStats: vi.fn().mockResolvedValue({ data: { assetsByState: {}, pendingAlarms: 0 } })
}))

import Dashboard from '../index.vue'

const childStubs = {
  DashboardTitleBar: { template: '<div class="stub-titlebar" />' },
  KpiStrip: { template: '<div class="stub-kpi" />' },
  AssetStateChart: { template: '<div class="stub-chart" />' },
  AlarmTrendChart: { template: '<div class="stub-chart" />' },
  WorkOrderChart: { template: '<div class="stub-chart" />' },
  TopAlarmDevices: { template: '<div class="stub-chart" />' },
  DeviceOnlineGauge: { template: '<div class="stub-chart" />' },
}

function mountDashboard() {
  return mount(Dashboard, {
    global: { plugins: [ElementPlus], stubs: childStubs }
  })
}

describe('Dashboard', () => {
  it('renders dashboard container', () => {
    const wrapper = mountDashboard()
    expect(wrapper.find('.dashboard').exists()).toBe(true)
  })

  it('renders stubbed title bar component', () => {
    const wrapper = mountDashboard()
    expect(wrapper.find('.stub-titlebar').exists()).toBe(true)
  })

  it('renders KPI strip', () => {
    const wrapper = mountDashboard()
    expect(wrapper.find('.stub-kpi').exists()).toBe(true)
  })

  it('renders chart sections', () => {
    const wrapper = mountDashboard()
    expect(wrapper.find('.charts-row-1').exists()).toBe(true)
    expect(wrapper.find('.charts-row-2').exists()).toBe(true)
  })

  it('renders without errors', () => {
    expect(() => mountDashboard()).not.toThrow()
  })
})
