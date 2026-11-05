/// <reference types="vite/client" />
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent
  export default component
}

declare module 'qrcode' {
  export function toCanvas(
    canvas: HTMLCanvasElement,
    text: string,
    options?: {
      width?: number
      margin?: number
      errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H'
    },
  ): Promise<void>
}
