import { ref, onUnmounted } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts instance lifecycle helper with ResizeObserver + window resize
 * both feeding a shared rAF-debounced resize. rAF alignment avoids the
 * double-trigger that would otherwise occur when a layout change fires
 * both window.resize and the element's ResizeObserver in the same frame.
 */
export function useChart() {
  const chartRef      = ref(null)
  let   chartInstance = null
  let   ro            = null
  let   rafId         = null

  function scheduleResize() {
    if (rafId !== null) return            // already queued for next frame
    rafId = requestAnimationFrame(() => {
      rafId = null
      chartInstance?.resize()
    })
  }

  /**
   * Template binding for pages hosting several charts: `:ref="xxx.bindChart"`.
   * A stable function identity (unlike inline arrows) avoids ref churn on
   * re-render; string `ref="chartRef"` still works for single-chart
   * components that destructure the returned ref.
   */
  function bindChart(el) {
    chartRef.value = el
  }

  function initChart(theme = 'dark') {
    if (!chartRef.value) {
      // Loud by design: a missing binding once made every chart render blank
      // with no error (template ref never reached the composable).
      console.error(
        '[useChart] initChart() called but chartRef is not bound to a DOM element — ' +
        'bind it in the template via :ref="xxx.bindChart"'
      )
      return null
    }
    dispose()
    chartInstance = echarts.init(chartRef.value, theme)
    if (typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(scheduleResize)
      ro.observe(chartRef.value)
    }
    return chartInstance
  }

  function setOption(option) {
    chartInstance?.setOption(option, true)
  }

  function resize() {
    chartInstance?.resize()
  }

  function dispose() {
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
      rafId = null
    }
    ro?.disconnect()
    ro = null
    chartInstance?.dispose()
    chartInstance = null
  }

  // window resize shares the same rAF-debounced path as ResizeObserver
  window.addEventListener('resize', scheduleResize)
  onUnmounted(() => {
    window.removeEventListener('resize', scheduleResize)
    dispose()
  })

  return { chartRef, bindChart, initChart, setOption, resize: scheduleResize, dispose }
}
