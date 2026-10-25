import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

// The work-order status distribution chart must translate every backend
// WorkOrderStatus enum (CREATED/ASSIGNED/PROCESSING/CLOSED/CANCELLED) to a
// Chinese label — its maps once carried invented keys (PENDING/IN_PROGRESS/
// COMPLETED) so PROCESSING and CREATED rendered as raw enums — and must let
// ECharts size the y-axis gutter (containLabel) so long labels are not
// clipped to "ROCESSING".

const setOption = vi.fn()
vi.mock('echarts', () => ({
  init: vi.fn(() => ({ setOption, dispose: vi.fn(), resize: vi.fn() })),
  graphic: { LinearGradient: class {} },
}))

import WorkOrderChart from '../WorkOrderChart.vue'

function mountChart(byStatus) {
  return mount(WorkOrderChart, {
    props: { byStatus, workOrders: { open: 6, mttrHours: 8.5, slaCompliance: 90 } },
    global: { stubs: { PanelCard: { template: '<div><slot name="title"/><slot/></div>' } } },
  })
}

function lastOption() {
  const calls = setOption.mock.calls.filter(c => c[0] && c[0].yAxis)
  return calls.at(-1)[0]
}

describe('WorkOrderChart status distribution', () => {
  beforeEach(() => { setOption.mockClear() })

  it('labels every backend status enum in Chinese', () => {
    mountChart({ CREATED: 2, ASSIGNED: 1, PROCESSING: 3, CLOSED: 5, CANCELLED: 1 })
    expect(lastOption().yAxis.data).toEqual(['已创建', '已派发', '处理中', '已关闭', '已取消'])
  })

  it('reserves the axis gutter so long labels are not clipped', () => {
    mountChart({ CREATED: 2 })
    expect(lastOption().grid.containLabel).toBe(true)
  })
})
