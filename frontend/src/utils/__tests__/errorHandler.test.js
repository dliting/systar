import { describe, it, expect, vi, beforeEach } from 'vitest'
import { showSystarError, showSystarSuccess } from '../../utils/errorHandler'

// Mock ElMessage
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
  },
}))

import { ElMessage } from 'element-plus'

describe('errorHandler', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  describe('showSystarError', () => {
    it('shows unreachable message for Network Error', () => {
      showSystarError(new Error('Network Error'), '加载资产树失败')
      expect(ElMessage.error).toHaveBeenCalledWith(
        expect.objectContaining({
          message: expect.stringContaining('Systar IoT 后端'),
          duration: 8000,
        }),
      )
    })

    it('shows timeout message for timeout errors', () => {
      showSystarError(new Error('timeout of 30000ms exceeded'), '查询数据')
      expect(ElMessage.error).toHaveBeenCalledWith(
        expect.objectContaining({
          message: expect.stringContaining('超时'),
          duration: 6000,
        }),
      )
    })

    it('shows generic error message for other errors', () => {
      showSystarError(new Error('Something went wrong'), '操作')
      expect(ElMessage.error).toHaveBeenCalledWith(
        expect.stringContaining('Something went wrong'),
      )
    })

    it('shows unknown error when err is null', () => {
      showSystarError(null)
      expect(ElMessage.error).toHaveBeenCalledWith(
        expect.stringContaining('请求失败'),
      )
    })

    it('shows error without context prefix when context is empty', () => {
      showSystarError(new Error('test error'), '')
      expect(ElMessage.error).toHaveBeenCalledWith('test error')
    })

    it('shows context prefix when context is provided', () => {
      showSystarError(new Error('fail'), '加载数据失败')
      expect(ElMessage.error).toHaveBeenCalledWith('加载数据失败：fail')
    })
  })

  describe('showSystarSuccess', () => {
    it('calls ElMessage.success', () => {
      showSystarSuccess('操作成功')
      expect(ElMessage.success).toHaveBeenCalledWith('操作成功')
    })
  })
})
