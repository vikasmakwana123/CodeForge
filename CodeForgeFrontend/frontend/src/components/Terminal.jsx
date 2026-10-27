import React, { useEffect, useMemo, useRef } from 'react'
import { useXTerm } from 'react-xtermjs'
import { AttachAddon } from '@xterm/addon-attach'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'

const Terminal = () => {
  const socketRef = useRef(null)
  const fitAddonRef = useRef(null)
  const { instance, ref } = useXTerm({
    options: {
      cursorBlink: true,
      cursorStyle: 'bar',
      cursorWidth: 14,
      cursorInactiveStyle: 'bar',
      fontSize: 14,
      fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
      theme: {
        background: '#0b1120',
        foreground: '#e2e8f0',
        cursor: '#7dd3fc',
        cursorAccent: '#ffffff',
        selectionBackground: 'rgba(125, 211, 252, 0.35)',
        black: '#0b1120',
        red: '#f87171',
        green: '#34d399',
        yellow: '#fbbf24',
        blue: '#60a5fa',
        magenta: '#c084fc',
        cyan: '#22d3ee',
        white: '#e2e8f0',
        brightBlack: '#475569',
        brightRed: '#fca5a5',
        brightGreen: '#6ee7b7',
        brightYellow: '#fcd34d',
        brightBlue: '#93c5fd',
        brightMagenta: '#e9d5ff',
        brightCyan: '#67e8f9',
        brightWhite: '#ffffff',
      },
    },
    listeners: {
      onData: (data) => {
        if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
          socketRef.current.send(data)
        }
      },
      onResize: () => {
        if (fitAddonRef.current) {
          fitAddonRef.current.fit()
        }
      },
    },
  })

  const socketUrl = useMemo(() => {
    if (typeof window === 'undefined') {
      return 'ws://localhost:8080/terminal'
    }
    return import.meta.env.VITE_TERMINAL_WS || 'ws://localhost:8080/terminal'
  }, [])

  useEffect(() => {
    if (!instance) return undefined

    const fitAddon = new FitAddon()
    fitAddonRef.current = fitAddon
    instance.loadAddon(fitAddon)
    fitAddon.fit()

    const socket = (() => {
      try {
        return new WebSocket(socketUrl)
      } catch (error) {
        return null
      }
    })()

    if (socket) {
      socketRef.current = socket
      const attachAddon = new AttachAddon(socket)
      instance.loadAddon(attachAddon)
      instance.writeln('\r\nConnected to remote terminal session...')

      const handleMessage = (event) => {
        const output = typeof event.data === 'string'
          ? event.data
          : new TextDecoder().decode(event.data)
        instance.write(output)
      }

      socket.addEventListener('message', handleMessage)
      socket.addEventListener('close', () => {
        instance.writeln('\r\nTerminal connection closed.')
      })

      return () => {
        socket.removeEventListener('message', handleMessage)
        socket.close()
      }
    }

    instance.writeln('\r\nTerminal ready. Connect your backend to ws://localhost:8080/terminal for live updates.')

    const demoMessages = [
      'npm install complete',
      'build succeeded',
      'server restarted',
      'watching for file changes',
    ]

    let index = 0
    const intervalId = setInterval(() => {
      const message = demoMessages[index % demoMessages.length]
      instance.write(`\r\n[${new Date().toLocaleTimeString()}] ${message}`)
      index += 1
    }, 2200)

    return () => {
      clearInterval(intervalId)
    }
  }, [instance, socketUrl])

  useEffect(() => {
    if (!ref.current) return undefined

    const focusTarget = ref.current
    const handleFocus = () => {
      focusTarget.classList.add('terminal-shell--focused')
    }

    const handleBlur = () => {
      focusTarget.classList.remove('terminal-shell--focused')
    }

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

        .terminal-shell .xterm-viewport::-webkit-scrollbar-thumb:active {
          background: rgba(226, 232, 240, 0.9);
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

      <div ref={ref} className="terminal-shell" style={{ width: '100%', height: '100%' }} />
    </>
  )
}

export default Terminal