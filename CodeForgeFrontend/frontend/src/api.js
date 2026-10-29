import axios from 'axios'
import { useSyncExternalStore } from 'react'

const TOKEN_KEY = 'codeforge.accessToken'
const AUTH_CHANGE_EVENT = 'codeforge-auth-change'

const api = axios.create()

function subscribeToAuthChanges(callback) {
  window.addEventListener(AUTH_CHANGE_EVENT, callback)
  window.addEventListener('storage', callback)
  return () => {
    window.removeEventListener(AUTH_CHANGE_EVENT, callback)
    window.removeEventListener('storage', callback)
  }
}

function getAuthSnapshot() {
  return Boolean(localStorage.getItem(TOKEN_KEY))
}

export function useAuthStatus() {
  return useSyncExternalStore(subscribeToAuthChanges, getAuthSnapshot, () => false)
}

function notifyAuthChanged() {
  window.dispatchEvent(new Event(AUTH_CHANGE_EVENT))
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export function saveAccessToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
  notifyAuthChanged()
}

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function clearAccessToken() {
  localStorage.removeItem(TOKEN_KEY)
  notifyAuthChanged()
}

export default api
