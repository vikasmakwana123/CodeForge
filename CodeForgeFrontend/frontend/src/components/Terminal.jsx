import { useEffect, useMemo, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import { FitAddon } from '@xterm/addon-fit'
import { useXTerm } from 'react-xtermjs'
import SockJS from 'sockjs-client'
import '@xterm/xterm/css/xterm.css'
import { getAccessToken } from '../api'

const Terminal = ({ projectId }) => {
  const sendRef = useRef(null)
  const connectionFailureRef = useRef(false)
  const connectionIdRef = useRef(crypto.randomUUID())
  const options = useMemo(() => ({
    cursorBlink: true,
    cursorStyle: 'bar',
    fontSize: 14,
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
    theme: {
      background: '#0b1120',
      foreground: '#e2e8f0',
      cursor: '#7dd3fc',
      cursorAccent: '#ffffff',
      selectionBackground: 'rgba(125, 211, 252, 0.35)',
    },
  }), [])
  const { instance, ref } = useXTerm({
    options,
    listeners: {
      onData: (data) => {
        const interruptIndex = data.indexOf('\x03')
        if (interruptIndex === -1) {
          sendRef.current?.('INPUT', data)
          return
        }

        if (interruptIndex > 0) sendRef.current?.('INPUT', data.slice(0, interruptIndex))
        sendRef.current?.('INTERRUPT')
        if (interruptIndex < data.length - 1) {
          sendRef.current?.('INPUT', data.slice(interruptIndex + 1))
        }
      },
      onResize: ({ cols, rows }) => sendRef.current?.('RESIZE', null, cols, rows),
    },
  })

  useEffect(() => {
    if (!instance) return undefined

    const fitAddon = new FitAddon()
    instance.loadAddon(fitAddon)
    fitAddon.fit()
    const handleWindowResize = () => fitAddon.fit()
    window.addEventListener('resize', handleWindowResize)

    if (!projectId) {
      instance.writeln('Open a saved project to use its terminal.')
      return () => {
        fitAddon.dispose()
        window.removeEventListener('resize', handleWindowResize)
      }
    }

    instance.writeln('Connecting to project terminal...')
    const connectionId = connectionIdRef.current
    const client = new Client({
      webSocketFactory: () => new SockJS('/api'),
      connectHeaders: { Authorization: `Bearer ${getAccessToken() || ''}` },
      reconnectDelay: 2000,
      connectionTimeout: 10000,
      onConnect: () => {
        connectionFailureRef.current = false
        client.subscribe(`/user/queue/terminal/${connectionId}`, (message) => {
          const event = JSON.parse(message.body)
          if (event.type === 'OUTPUT' || event.type === 'STATUS') {
            instance.write(event.data || '')
          } else if (event.type === 'ERROR') {
            instance.writeln(`\r\n[CodeForge] ${event.error || 'Terminal error'}\r\n`)
          } else if (event.type === 'EXITED') {
            instance.write(event.data || '\r\nTerminal process ended.\r\n')
          }
        })
        sendRef.current = (action, data, columns, rows) => client.publish({
          destination: '/app/terminal',
          body: JSON.stringify({ action, projectId, connectionId, data, columns, rows }),
        })
        sendRef.current('OPEN', null, instance.cols, instance.rows)
        instance.focus()
      },
      onStompError: (frame) => {
        client.reconnectDelay = 0
        if (connectionFailureRef.current) return
        connectionFailureRef.current = true
        const detail = Array.from(
          frame.headers?.message || frame.body || 'The server rejected the terminal connection.',
        )
          .filter((character) => character.charCodeAt(0) >= 32 && character.charCodeAt(0) !== 127)
          .join('')
          .slice(0, 300)
        instance.writeln(`\r\n[CodeForge] Terminal connection rejected: ${detail}\r\n`)
        void client.deactivate()
      },
      onWebSocketError: () => {
        if (connectionFailureRef.current) return
        connectionFailureRef.current = true
        instance.writeln('\r\n[CodeForge] Could not reach the terminal service.\r\n')
      },
      onWebSocketClose: () => {
        if (connectionFailureRef.current) return
        connectionFailureRef.current = true
        instance.writeln('\r\n[CodeForge] Terminal connection closed.\r\n')
      },
    })

    client.activate()

    return () => {
      sendRef.current = null
      void client.deactivate()
      fitAddon.dispose()
      window.removeEventListener('resize', handleWindowResize)
    }
  }, [instance, projectId])

  useEffect(() => {
    if (!ref.current) return undefined

    const focusTarget = ref.current
    const handleFocus = () => focusTarget.classList.add('terminal-shell--focused')
    const handleBlur = () => focusTarget.classList.remove('terminal-shell--focused')

    focusTarget.addEventListener('focusin', handleFocus)
    focusTarget.addEventListener('focusout', handleBlur)
    return () => {
      focusTarget.removeEventListener('focusin', handleFocus)
      focusTarget.removeEventListener('focusout', handleBlur)
    }
  }, [ref])

  return (
    <>
      <style>{`
        .terminal-shell {
          width: 100%;
          height: 100%;
          background: #0b1120;
          overflow: hidden;
        }

        .terminal-shell .xterm {
          height: 100%;
        }

        .terminal-shell .xterm-viewport {
          background: #0b1120 !important;
          scrollbar-color: rgba(148, 163, 184, 0.7) rgba(15, 23, 42, 0.5);
        }

        .terminal-shell .xterm-viewport::-webkit-scrollbar {
          width: 10px;
          height: 10px;
        }

        .terminal-shell .xterm-viewport::-webkit-scrollbar-track {
          background: rgba(15, 23, 42, 0.7);
        }

        .terminal-shell .xterm-viewport::-webkit-scrollbar-thumb {
          background: rgba(148, 163, 184, 0.7);
          border-radius: 999px;
          border: 2px solid rgba(15, 23, 42, 0.8);
        }

        .terminal-shell .xterm-viewport::-webkit-scrollbar-thumb:hover {
          background: rgba(148, 163, 184, 0.9);
        }

        .terminal-shell .xterm .xterm-cursor {
          border-left: 5px solid #7dd3fc !important;
          background: rgba(125, 211, 252, 0.18) !important;
          width: 5px !important;
          margin-left: 5px !important;
        }

        .terminal-shell--focused .xterm .xterm-cursor {
          border-left-color: #ffffff !important;
          background: rgba(255, 255, 255, 0.2) !important;
        }
      `}</style>
      <div ref={ref} className="terminal-shell" />
    </>
  )
}

export default Terminal
