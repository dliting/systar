import { describe, it, expect } from 'vitest'
import { wizardToCron, cronToWizard, defaultWizardState } from '../cron-utils'

describe('wizardToCron', () => {
  it('daily + 08:00 → 0 0 8 * * ?', () => {
    expect(wizardToCron({ frequency: 'daily', time: '08:00' }))
      .toBe('0 0 8 * * ?')
  })

  it('daily + 14:30 → 0 30 14 * * ?', () => {
    expect(wizardToCron({ frequency: 'daily', time: '14:30' }))
      .toBe('0 30 14 * * ?')
  })

  it('weekly + 08:00 + [1,3,5] → 0 0 8 * * 1,3,5', () => {
    expect(wizardToCron({ frequency: 'weekly', time: '08:00', weekDays: [1, 3, 5] }))
      .toBe('0 0 8 * * 1,3,5')
  })

  it('weekly + single day [2] → 0 0 8 * * 2', () => {
    expect(wizardToCron({ frequency: 'weekly', time: '08:00', weekDays: [2] }))
      .toBe('0 0 8 * * 2')
  })

  it('monthly + 08:00 + day 1 → 0 0 8 1 * ?', () => {
    expect(wizardToCron({ frequency: 'monthly', time: '08:00', monthDay: 1 }))
      .toBe('0 0 8 1 * ?')
  })

  it('monthly + 08:00 + LAST_DAY → 0 0 8 L * ?', () => {
    expect(wizardToCron({ frequency: 'monthly', time: '08:00', monthDay: 'LAST_DAY' }))
      .toBe('0 0 8 L * ?')
  })

  it('monthly + day 31 → 0 0 8 31 * ?', () => {
    expect(wizardToCron({ frequency: 'monthly', time: '08:00', monthDay: 31 }))
      .toBe('0 0 8 31 * ?')
  })

  it('weekly + empty weekDays → null (validation fail)', () => {
    expect(wizardToCron({ frequency: 'weekly', time: '08:00', weekDays: [] }))
      .toBeNull()
  })
})

describe('cronToWizard', () => {
  it('0 0 8 * * ? → daily', () => {
    expect(cronToWizard('0 0 8 * * ?'))
      .toEqual({ frequency: 'daily', time: '08:00' })
  })

  it('0 0 8 ? * * → daily (* / ? equivalence)', () => {
    expect(cronToWizard('0 0 8 ? * *'))
      .toEqual({ frequency: 'daily', time: '08:00' })
  })

  it('0 0 8 ? * 1,3,5 → weekly', () => {
    expect(cronToWizard('0 0 8 ? * 1,3,5'))
      .toEqual({ frequency: 'weekly', time: '08:00', weekDays: [1, 3, 5] })
  })

  it('0 0 8 * * 1,3,5 → weekly (* / ? equivalence)', () => {
    expect(cronToWizard('0 0 8 * * 1,3,5'))
      .toEqual({ frequency: 'weekly', time: '08:00', weekDays: [1, 3, 5] })
  })

  it('0 0 8 1 * ? → monthly', () => {
    expect(cronToWizard('0 0 8 1 * ?'))
      .toEqual({ frequency: 'monthly', time: '08:00', monthDay: 1 })
  })

  it('0 0 8 L * ? → monthly LAST_DAY', () => {
    expect(cronToWizard('0 0 8 L * ?'))
      .toEqual({ frequency: 'monthly', time: '08:00', monthDay: 'LAST_DAY' })
  })

  it('0 */5 * * * ? → null (unparseable)', () => {
    expect(cronToWizard('0 */5 * * * ?'))
      .toBeNull()
  })

  it('empty string → null', () => {
    expect(cronToWizard(''))
      .toBeNull()
  })

  it('non-6-field expression → null', () => {
    expect(cronToWizard('0 0 8 * *'))
      .toBeNull()
  })

  it('non-zero seconds → null', () => {
    expect(cronToWizard('30 0 8 * * ?'))
      .toBeNull()
  })
})

describe('defaultWizardState', () => {
  it('returns daily at 08:00', () => {
    expect(defaultWizardState()).toEqual({
      frequency: 'daily',
      time: '08:00',
      weekDays: [1],
      monthDay: 1,
    })
  })
})
