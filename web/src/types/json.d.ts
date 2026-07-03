declare module '*.json' {
  const value: unknown
  export default value
}

declare module '*.svg' {
  const value: string
  export default value
}

declare module '*.png' {
  const value: string
  export default value
}

declare module 'virtual:svg-icons-register' {
  const value: void
  export default value
}
