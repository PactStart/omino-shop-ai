import { ElMessage } from 'element-plus'
import { apiPost, errorMessage, isApiError } from '../api/http'

export async function issueGodView(userId: string): Promise<'ok' | 'denied' | string> {
  try {
    await apiPost('/v1/god-view/tokens', { userId })
    ElMessage.success('签发成功')
    return 'ok'
  } catch (error) {
    if (isApiError(error) && error.code === 40303) return 'denied'
    return errorMessage(error)
  }
}
