import { describe, it, expect } from 'vitest'
import { stateTagType, kindLabel, kindTagType, formatTimestamp, parseTime, formatMetricValue, splitPathSegments, formatDisplayPath, rankLabel, rankTagType, workOrderTypeLabel, priorityLabel, escalationLabel, notifyTypeLabel, commandLabel, describeCron } from '../../utils/formatters'

describe('formatters', () => {
  describe('stateTagType', () => {
    it('returns correct tag type for known states', () => {
      expect(stateTagType('NORMAL')).toBe('success')
      expect(stateTagType('WARNING')).toBe('warning')
      expect(stateTagType('ERROR')).toBe('danger')
      expect(stateTagType('OFFLINE')).toBe('info')
    })

    it('defaults to info for unknown state', () => {
      expect(stateTagType('UNKNOWN')).toBe('info')
      expect(stateTagType('')).toBe('info')
    })
  })

  describe('kindLabel', () => {
    it('returns Chinese label for known kinds', () => {
      expect(kindLabel('DEVICE')).toBe('设备')
      expect(kindLabel('PROBE')).toBe('监测器')
      expect(kindLabel('CONTROL')).toBe('控制器')
      expect(kindLabel('SERVICE')).toBe('服务')
    })

    it('labels GROUP nodes', () => {
      expect(kindLabel('GROUP')).toBe('分组')
    })

    it('returns raw kind for unknown', () => {
      expect(kindLabel('OTHER')).toBe('OTHER')
    })
  })

  describe('kindTagType', () => {
    it('returns correct tag type for kinds', () => {
      expect(kindTagType('PROBE')).toBe('success')
      expect(kindTagType('CONTROL')).toBe('warning')
      expect(kindTagType('DEVICE')).toBe('primary')
    })

    it('tags GROUP nodes as info', () => {
      expect(kindTagType('GROUP')).toBe('info')
    })

    it('defaults to info', () => {
      expect(kindTagType('UNKNOWN')).toBe('info')
    })
  })

  describe('formatTimestamp', () => {
    it('returns empty string for falsy input', () => {
      expect(formatTimestamp(null)).toBe('')
      expect(formatTimestamp(0)).toBe('')
      expect(formatTimestamp('')).toBe('')
    })

    it('formats number timestamp', () => {
      const result = formatTimestamp(1700000000000)
      expect(result).toContain('2023')
    })

    it('formats string with T', () => {
      expect(formatTimestamp('2026-05-30T08:00:00')).toBe('2026-05-30 08:00:00')
    })
  })

  describe('parseTime', () => {
    it('returns empty string for falsy input', () => {
      expect(parseTime(null)).toBe('')
      expect(parseTime('')).toBe('')
    })

    it('parses ISO date string', () => {
      const result = parseTime('2026-05-30T08:30:00')
      expect(result).toBe('2026-05-30 08:30:00')
    })

    it('returns string for invalid date', () => {
      expect(parseTime('not-a-date')).toBe('not-a-date')
    })
  })

  // Operators read whole numbers and at most a couple of decimals — the raw
  // double (12.77434613895595) on screen is developer noise.
  describe('formatMetricValue', () => {
    it('rounds a long float to two decimals', () => {
      expect(formatMetricValue(12.77434613895595)).toBe('12.77')
    })

    it('keeps integers bare', () => {
      expect(formatMetricValue(25)).toBe('25')
      expect(formatMetricValue(25.0)).toBe('25')
    })

    it('strips trailing zeros', () => {
      expect(formatMetricValue(25.10)).toBe('25.1')
      expect(formatMetricValue(0.5)).toBe('0.5')
    })

    it('rounds negatives symmetrically', () => {
      expect(formatMetricValue(-3.14159)).toBe('-3.14')
    })

    it('keeps four decimals below one so small readings do not collapse to zero', () => {
      expect(formatMetricValue(0.001234)).toBe('0.0012')
    })

    it('formats numeric strings like numbers', () => {
      expect(formatMetricValue('12.77434613895595')).toBe('12.77')
    })

    it('passes non-numeric values through (control states)', () => {
      expect(formatMetricValue('ON')).toBe('ON')
      expect(formatMetricValue(true)).toBe('true')
    })

    it('shows an em dash for missing values', () => {
      expect(formatMetricValue(null)).toBe('—')
      expect(formatMetricValue(undefined)).toBe('—')
    })
  })

  // Asset paths arrive as '->'-joined caption segments from the backend;
  // the UI renders them as breadcrumb segments / an arrow-joined string.
  describe('splitPathSegments', () => {
    it('splits a caption path into segments', () => {
      expect(splitPathSegments('温湿度传感器组->102湿度')).toEqual(['温湿度传感器组', '102湿度'])
    })

    it('drops empty segments from leading or trailing separators', () => {
      expect(splitPathSegments('->a->')).toEqual(['a'])
    })

    it('returns an empty array for empty input', () => {
      expect(splitPathSegments('')).toEqual([])
      expect(splitPathSegments(null)).toEqual([])
      expect(splitPathSegments(undefined)).toEqual([])
    })
  })

  describe('formatDisplayPath', () => {
    it('joins segments with an arrow', () => {
      expect(formatDisplayPath('a->b->c')).toBe('a → b → c')
    })

    it('renders a single segment bare', () => {
      expect(formatDisplayPath('102湿度')).toBe('102湿度')
    })

    it('returns an empty string for empty input', () => {
      expect(formatDisplayPath('')).toBe('')
      expect(formatDisplayPath(null)).toBe('')
    })
  })

  // t_event_rank seed rows are 1提示/2警告/3重要/4紧急 — the raw numeric id
  // in an alarm list ("级别 2") is meaningless to operations staff.
  describe('rankLabel', () => {
    it('names the four seeded ranks', () => {
      expect(rankLabel(1)).toBe('提示')
      expect(rankLabel(2)).toBe('警告')
      expect(rankLabel(3)).toBe('重要')
      expect(rankLabel(4)).toBe('紧急')
    })

    it('labels an out-of-range rank without inventing a name', () => {
      expect(rankLabel(9)).toBe('级别 9')
    })

    it('shows an em dash for a missing rank', () => {
      expect(rankLabel(null)).toBe('—')
      expect(rankLabel(undefined)).toBe('—')
    })
  })

  describe('rankTagType', () => {
    it('colors ranks by severity', () => {
      expect(rankTagType(1)).toBe('info')
      expect(rankTagType(2)).toBe('warning')
      expect(rankTagType(3)).toBe('danger')
      expect(rankTagType(4)).toBe('danger')
    })

    it('defaults to info for unknown ranks', () => {
      expect(rankTagType(9)).toBe('info')
      expect(rankTagType(null)).toBe('info')
    })
  })

  // Work order type codes are stored uppercase; operators read Chinese badges.
  // 维护 is the term every existing screen (filters, dialogs, ledger) uses.
  describe('workOrderTypeLabel', () => {
    it('labels the three types in use', () => {
      expect(workOrderTypeLabel('REPAIR')).toBe('维修')
      expect(workOrderTypeLabel('MAINTENANCE')).toBe('维护')
      expect(workOrderTypeLabel('INSPECTION')).toBe('巡检')
    })

    it('passes unknown codes through', () => {
      expect(workOrderTypeLabel('OTHER')).toBe('OTHER')
    })

    it('returns an empty string for missing input', () => {
      expect(workOrderTypeLabel(null)).toBe('')
    })
  })

  // Work order priority mirrors the event rank id (AlarmToWorkOrderListener
  // copies eventRankId): 4 is the most urgent, matching the create dialog.
  describe('priorityLabel', () => {
    it('names the four priorities', () => {
      expect(priorityLabel(1)).toBe('P1 低')
      expect(priorityLabel(2)).toBe('P2 中')
      expect(priorityLabel(3)).toBe('P3 高')
      expect(priorityLabel(4)).toBe('P4 紧急')
    })

    it('labels an out-of-range priority without inventing a name', () => {
      expect(priorityLabel(5)).toBe('P5')
    })

    it('returns an empty string for missing input', () => {
      expect(priorityLabel(null)).toBe('')
    })
  })

  describe('escalationLabel', () => {
    it('reads zero as not escalated', () => {
      expect(escalationLabel(0)).toBe('未升级')
      expect(escalationLabel(null)).toBe('未升级')
    })

    it('counts escalations', () => {
      expect(escalationLabel(2)).toBe('已升级 2 次')
    })
  })

  describe('notifyTypeLabel', () => {
    it('labels the four notification channels', () => {
      expect(notifyTypeLabel('site_notice')).toBe('站内通知')
      expect(notifyTypeLabel('email')).toBe('邮件')
      expect(notifyTypeLabel('sms')).toBe('短信')
      expect(notifyTypeLabel('phone')).toBe('电话')
    })

    it('passes unknown codes through', () => {
      expect(notifyTypeLabel('carrier_pigeon')).toBe('carrier_pigeon')
    })

    it('returns an empty string for missing input', () => {
      expect(notifyTypeLabel(null)).toBe('')
    })
  })

  // Linkage effect commands: -1 is the engine's toggle sentinel, ON/OFF are the
  // common control commands; anything else is a custom command and passes through.
  describe('commandLabel', () => {
    it('translates the toggle sentinel and common commands', () => {
      expect(commandLabel('-1')).toBe('切换')
      expect(commandLabel('ON')).toBe('开')
      expect(commandLabel('OFF')).toBe('关')
    })

    it('passes custom commands through', () => {
      expect(commandLabel('RESET')).toBe('RESET')
    })

    it('returns an empty string for missing input', () => {
      expect(commandLabel(null)).toBe('')
    })
  })

  // Spring 6-field cron is what the schedule/inspection forms store; operators
  // read "每天 08:00", not "0 0 8 * * *". Unrecognized shapes fall back to raw.
  describe('describeCron', () => {
    it('describes a daily time', () => {
      expect(describeCron('0 0 8 * * *')).toBe('每天 08:00')
      expect(describeCron('0 0 8 * * ?')).toBe('每天 08:00')
    })

    it('describes multiple times a day', () => {
      expect(describeCron('0 0 8,14 * * *')).toBe('每天 08:00、14:00')
    })

    it('describes a weekly time', () => {
      expect(describeCron('0 0 9 ? * MON')).toBe('每周一 09:00')
    })

    it('describes weekdays', () => {
      expect(describeCron('0 0 0 * * MON-FRI')).toBe('工作日 00:00')
    })

    it('describes an every-N-minutes interval', () => {
      expect(describeCron('0 */5 * * * *')).toBe('每 5 分钟')
    })

    it('describes an every-N-hours interval', () => {
      expect(describeCron('0 0 */2 * * *')).toBe('每 2 小时')
    })

    it('falls back to the raw expression for shapes it cannot describe', () => {
      expect(describeCron('0 0 8 1 * *')).toBe('0 0 8 1 * *')
      expect(describeCron('not a cron')).toBe('not a cron')
    })

    it('returns an empty string for missing input', () => {
      expect(describeCron('')).toBe('')
      expect(describeCron(null)).toBe('')
    })
  })
})
