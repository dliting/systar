import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// echarts is mocked: pages must only rely on init/setOption/dispose/resize
// plus the graphic.LinearGradient constructor used by chartOptions.
// useChart does `import * as echarts from 'echarts'`, so the factory shape
// must mirror the module namespace.
vi.mock('echarts', () => ({
  init: vi.fn(() => ({ setOption: vi.fn(), dispose: vi.fn(), resize: vi.fn() })),
  graphic: { LinearGradient: class LinearGradient {} },
}))
import * as echarts from 'echarts'

vi.mock('@/api/iot/statistics', () => ({
  getAlarmStats: vi.fn(async () => {}),
  getWorkOrderStats: vi.fn(async () => {}),
  getInspectionStats: vi.fn(async () => {}),
  getMaintenanceStats: vi.fn(async () => {}),
  getDeviceRuntimeStats: vi.fn(async () => {}),
  getDashboardStats: vi.fn(async () => {}),
}))
import {
  getAlarmStats, getWorkOrderStats, getInspectionStats,
  getMaintenanceStats, getDeviceRuntimeStats, getDashboardStats,
} from '@/api/iot/statistics'

import AlarmSummary from '../AlarmSummary.vue'
import WorkOrderSummary from '../WorkOrderSummary.vue'
import InspectionSummary from '../InspectionSummary.vue'
import MaintenanceSummary from '../MaintenanceSummary.vue'
import DeviceSummary from '../DeviceSummary.vue'
import DashboardOverview from '../DashboardOverview.vue'

// Passthrough stubs for the Element Plus components the pages use; the chart
// binding under test only needs the plain <div> children to reach the DOM.
const globalStubs = {
  ElRow: { name: 'ElRow', template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  ElCol: { name: 'ElCol', template: '<div class="el-col"><slot /></div>', props: ['span'] },
  ElEmpty: { name: 'ElEmpty', template: '<div class="el-empty"><slot /></div>', props: ['description'] },
  ElTable: { name: 'ElTable', template: '<div class="el-table"><slot /></div>', props: ['data', 'size', 'maxHeight'] },
  ElTableColumn: { name: 'ElTableColumn', template: '<div class="el-table-col"><slot :row="{}" /></div>', props: ['prop', 'label', 'width'] },
}

const PERIOD_PROPS = { startDate: '2026-08-25', endDate: '2026-09-01', granularity: 'DAY' }

/**
 * Every summary page must wire each useChart() container to its template
 * <div> via the chartRef binding, so echarts.init receives one distinct real
 * DOM element per chart. The pages once used undeclared string refs
 * (ref="pieRef") that never reached the composable — charts stayed blank
 * with no error, which is the regression this file pins down.
 */
const CASES = [
  {
    name: 'AlarmSummary',
    component: AlarmSummary,
    props: PERIOD_PROPS,
    mock: [getAlarmStats, {
      byLevel: { 2: 3, 3: 5, 4: 2 },
      trend: [{ period: '08-31', value: 3 }, { period: '09-01', value: 5 }],
      topDevices: [{ deviceId: 1102, deviceName: 'ups_sim_01', alarmCount: 4 }],
      currentPeriodCount: 10,
      prevPeriodCount: 6,
    }],
    charts: 4,
  },
  {
    name: 'WorkOrderSummary',
    component: WorkOrderSummary,
    props: PERIOD_PROPS,
    mock: [getWorkOrderStats, {
      agingDistribution: { '0-24h': 3, '1-3d': 2 },
      byStatus: { CLOSED: 5, PROCESSING: 1 },
      trend: [{ period: '08-31', value: 2 }],
      slaComplianceRate: 0.9,
    }],
    charts: 4,
  },
  {
    name: 'InspectionSummary',
    component: InspectionSummary,
    props: PERIOD_PROPS,
    mock: [getInspectionStats, {
      completionRate: 0.5,
      anomalyTrend: [{ period: '08-31', value: 1 }],
      completionTrend: [{ period: '08-31', value: 2 }],
      currentPeriodTotal: 4,
      prevPeriodTotal: 9,
    }],
    charts: 4,
  },
  {
    name: 'MaintenanceSummary',
    component: MaintenanceSummary,
    props: PERIOD_PROPS,
    mock: [getMaintenanceStats, {
      frequencyTrend: [{ period: '08-31', value: 2 }],
      byType: { REPAIR: 2, MAINTENANCE: 3 },
      costByType: { REPAIR: 850, MAINTENANCE: 900 },
    }],
    charts: 3,
  },
  {
    name: 'DeviceSummary',
    component: DeviceSummary,
    props: PERIOD_PROPS,
    mock: [getDeviceRuntimeStats, {
      totalDevices: 4,
      onlineDevices: 4,
      availabilityRate: 1,
      details: [
        { deviceName: 'ups_sim_01', onlineDays: 7, totalDays: 7, rate: 1 },
        { deviceName: 'ahu_sim_01', onlineDays: 6, totalDays: 7, rate: 0.857 },
      ],
    }],
    charts: 1,
  },
  {
    name: 'DashboardOverview',
    component: DashboardOverview,
    props: {},
    mock: [getDashboardStats, {
      alarms: { today: 3, handlingRate: 0.9, trend: [{ period: '08-31', value: 3 }] },
      workOrders: { slaCompliance: 0.9, open: 2 },
      inspections: { completionRate: 0.5 },
      devices: { availability: 0.99 },
      topAlarmDevices: [{ deviceName: 'ups_sim_01', alarmCount: 4 }],
    }],
    charts: 8,
  },
]

describe('statistics pages chart binding', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each(CASES)('%s initializes every chart on a distinct DOM element', async ({ component, props, mock, charts }) => {
    const [fn, payload] = mock
    vi.mocked(fn).mockResolvedValue(payload)

    mount(component, {
      props,
      global: { stubs: globalStubs, directives: { loading: {} } },
    })
    await flushPromises()

    expect(echarts.init).toHaveBeenCalledTimes(charts)

    const elements = vi.mocked(echarts.init).mock.calls.map(call => call[0])
    for (const el of elements) {
      expect(el).toBeInstanceOf(HTMLElement)
    }
    expect(new Set(elements).size).toBe(charts)
  })

  /**
   * A failed fetch must be logged (project rule: never swallow runtime errors
   * silently) before falling back to the empty state — a backend 500 used to
   * render as a calm "暂无数据" with nothing in the console.
   */
  it.each(CASES)('%s logs the fetch error and shows the empty state when the API rejects', async ({ component, props, mock }) => {
    const [fn] = mock
    vi.mocked(fn).mockRejectedValueOnce(new Error('network down'))
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      const wrapper = mount(component, {
        props,
        global: { stubs: globalStubs, directives: { loading: {} } },
      })
      await flushPromises()

      expect(errorSpy).toHaveBeenCalledWith(expect.stringContaining('Failed to load'), expect.any(Error))
      expect(wrapper.find('.el-empty').exists()).toBe(true)
      expect(echarts.init).not.toHaveBeenCalled()
    } finally {
      errorSpy.mockRestore()
    }
  })
})
