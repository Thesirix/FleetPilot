export interface Toast {
  id: number
  kind: 'success' | 'error' | 'info'
  message: string
  reference?: string
}

const toasts = ref<Toast[]>([])
let sequence = 0

/** Short notifications in the corner of the screen: confirmations and errors of the user's actions. */
export function useToast() {
  function push(kind: Toast['kind'], message: string, reference?: string) {
    const id = ++sequence
    toasts.value = [...toasts.value, { id, kind, message, reference }]
    setTimeout(() => dismiss(id), kind === 'error' ? 8000 : 4000)
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter(toast => toast.id !== id)
  }

  return {
    toasts: readonly(toasts),
    success: (message: string) => push('success', message),
    error: (message: string, reference?: string) => push('error', message, reference),
    info: (message: string) => push('info', message),
    dismiss,
  }
}
