import { systarApi } from '@/api/request'

export function executeControl(id, command) {
  return systarApi({ url: '/api/monitor/control/' + id + '/execute', method: 'post', data: { command } })
}
