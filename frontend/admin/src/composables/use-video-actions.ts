import { computed, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { readFieldErrors, resolveErrorMessage } from '@/utils/error-message'

/**
 * 视频页面复用请求状态和一次失败提示，表单错误同时按字段回显。
 */
export function useVideoActions() {
  const count = ref(0)
  const errors = ref<Record<string, string>>({})
  const message = useMessage()
  const busy = computed(() => count.value > 0)
  /**
   * 执行一次明确业务操作，并返回成功状态。
   */
  async function act(operation: () => Promise<unknown>, success?: string): Promise<boolean> {
    count.value++
    errors.value = {}
    try {
      await operation()
      if (success) message.success(success)
      return true
    } catch (error: unknown) {
      errors.value = readFieldErrors(error)
      message.error(resolveErrorMessage(error))
      return false
    } finally {
      count.value--
    }
  }
  return { busy, errors, act, message }
}
