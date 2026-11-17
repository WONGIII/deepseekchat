import { create } from 'zustand'

type AuthUiState = {
  open: boolean
}

export const useAuthUi = create<AuthUiState>(() => ({ open: false }))

export const openAuthOverlay = () => useAuthUi.setState({ open: true })

export const closeAuthOverlay = () => useAuthUi.setState({ open: false })
