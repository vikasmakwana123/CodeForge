import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import Editor from '@monaco-editor/react'
import { Client } from '@stomp/stompjs'
import { FaCss3Alt, FaArrowDown } from 'react-icons/fa'
import { FiFile, FiFolder, FiFolder as FiFolderOpen } from 'react-icons/fi'
import { SiJavascript, SiJson } from 'react-icons/si'
import { Link, useParams } from 'react-router-dom'
import SockJS from 'sockjs-client'
import './PlaygroundPage.css'
import Terminal from '../components/Terminal'

const demoFiles = [
  { name: 'src', isDirectory: true, children: [{ name: 'App.jsx', isDirectory: false }, { name: 'main.jsx', isDirectory: false }, { name: 'index.css', isDirectory: false }] },
  { name: 'public', isDirectory: true, children: [{ name: 'favicon.svg', isDirectory: false }] },
  { name: 'package.json', isDirectory: false },
  { name: 'README.md', isDirectory: false },
]

const demoContent = {
  'App.jsx': "import './index.css'\n\nfunction App() {\n  return (\n    <main className=\"app\">\n      <h1>Build something brilliant.</h1>\n      <p>Your CodeForge workspace is ready.</p>\n    </main>\n  )\n}\n\nexport default App\n",
  'main.jsx': "import { StrictMode } from 'react'\nimport { createRoot } from 'react-dom/client'\nimport App from './App.jsx'\n\ncreateRoot(document.getElementById('root')).render(\n  <StrictMode><App /></StrictMode>,\n)\n",
  'index.css': 'body {\n  margin: 0;\n  font-family: sans-serif;\n  background: #080a0d;\n  color: #f1f3f5;\n}\n',
  'package.json': '{\n  "name": "codeforge-project",\n  "private": true,\n  "scripts": {\n    "dev": "vite"\n  }\n}\n',
  'README.md': '# CodeForge project\n\nA new project created in your cloud workspace.\n',
  'favicon.svg': '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><circle cx="16" cy="16" r="14" fill="#63b5ff"/></svg>\n',
}

function flattenFiles(nodes, parent = '') {
  return nodes.flatMap((node) => {
    const path = parent ? `${parent}/${node.name}` : node.name
    return node.isDirectory ? flattenFiles(node.children || [], path) : [{ name: node.name, path }]
  })
}

function sortTree(nodes) {
  return [...nodes].sort((first, second) => {
    if (first.isDirectory !== second.isDirectory) return first.isDirectory ? -1 : 1
    return first.name.localeCompare(second.name)
  }).map((node) => node.isDirectory ? { ...node, children: sortTree(node.children || []) } : node)
}

function getFileIcon(fileName) {
  if (fileName.endsWith('.jsx') || fileName.endsWith('.js')) return <SiJavascript />
  if (fileName.endsWith('.css')) return <FaCss3Alt />
  if (fileName.endsWith('.json')) return <SiJson />
  return <FiFile />
}

function TreeNode({ node, path = '', onOpen, activePath, selectedPath, onSelect }) {
  const [expanded, setExpanded] = useState(true)
  const nodePath = path ? `${path}/${node.name}` : node.name
  if (node.isDirectory) {
    return <div className="tree-group"><button className={`tree-row folder-row ${selectedPath === nodePath ? 'selected-path' : ''}`} onClick={() => { onSelect(nodePath); setExpanded((value) => !value) }}><span className="tree-chevron">{expanded ? '⌄' : '›'}</span><span className="tree-icon folder-icon">{expanded ? <FiFolderOpen /> : <FiFolder />}</span><span>{node.name}</span></button>{expanded && <div className="tree-children">{sortTree(node.children || []).map((child) => <TreeNode key={`${nodePath}/${child.name}`} node={child} path={nodePath} onOpen={onOpen} activePath={activePath} selectedPath={selectedPath} onSelect={onSelect} />)}</div>}</div>
  }
  return <button className={`tree-row file-row ${activePath === nodePath ? 'active-file' : ''} ${selectedPath === nodePath ? 'selected-path' : ''}`} onDoubleClick={() => onOpen(node, nodePath)} onClick={() => { onSelect(nodePath); onOpen(node, nodePath) }}><span className="tree-chevron" /><span className="tree-icon file-icon">{getFileIcon(node.name)}</span><span>{node.name}</span></button>
}

function PlaygroundPage() {
  const { projectId } = useParams()
  const [tree, setTree] = useState(sortTree(demoFiles))
  const [treeStatus, setTreeStatus] = useState(projectId ? 'Loading project tree...' : 'Demo workspace')
  const [openFiles, setOpenFiles] = useState([])
  const [activePath, setActivePath] = useState('src/App.jsx')
  const [content, setContent] = useState(demoContent['App.jsx'])
  const [saved, setSaved] = useState(true)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const [selectedPath, setSelectedPath] = useState('')
  const [connectionStatus, setConnectionStatus] = useState(projectId ? 'CONNECTING' : 'LOCAL')
  const [isSaving, setIsSaving] = useState(false)
  const [isTerminalOpen, setIsTerminalOpen] = useState(false)

  const projectPath = (path) => `${projectId}/${path}`

  const refreshTree = () => {
    if (!projectId) return Promise.resolve()
    return axios.get(`/api/projects/${projectId}/tree`).then((response) => {
      setTree(sortTree(response.data.children || []))
      setTreeStatus('Connected workspace')
    })
  }

  const loadInitialFile = (nextTree) => {
    const availableFiles = flattenFiles(nextTree)
    const firstFile = availableFiles.find((file) => file.name === 'App.jsx')
      || availableFiles.find((file) => /\.(jsx?|tsx?|css)$/.test(file.name))
      || availableFiles[0]
    if (!firstFile) return Promise.resolve()
    setActivePath(firstFile.path)
    setSelectedPath(firstFile.path)
    return axios.get('/api/files', { params: { path: projectPath(firstFile.path) } }).then((fileResponse) => {
      setContent(fileResponse.data?.content ?? fileResponse.data ?? '')
      setSaved(true)
    })
  }

  useEffect(() => {
    if (!projectId) return
    axios.get(`/api/projects/${projectId}/tree`).then((response) => {
      const nextTree = sortTree(response.data.children || [])
      setTree(nextTree)
      setTreeStatus('Connected workspace')
      return loadInitialFile(nextTree)
    }).catch(() => setTreeStatus('Tree unavailable · demo files loaded'))
  }, [projectId])

  useEffect(() => {
    if (!projectId) return undefined
    const client = new Client({
      webSocketFactory: () => new SockJS('/api'),
      reconnectDelay: 0,
      connectionTimeout: 5000,
      onConnect: () => {
        setConnectionStatus('LIVE')
        client.subscribe('/topic/files', (message) => {
          const event = JSON.parse(message.body)
          if (event.path?.startsWith(`${projectId}/`)) refreshTree()
        })
      },
      onDisconnect: () => setConnectionStatus('OFFLINE'),
      onWebSocketClose: () => {
        setConnectionStatus('OFFLINE')
        client.deactivate()
      },
      onWebSocketError: () => {
        setConnectionStatus('OFFLINE')
        client.deactivate()
      },
      onStompError: () => {
        setConnectionStatus('OFFLINE')
        client.deactivate()
      },
    })
    client.activate()
    return () => client.deactivate()
  }, [projectId])

  const files = useMemo(() => flattenFiles(tree), [tree])
  const activeFile = files.find((file) => file.path === activePath) || { name: activePath.split('/').pop(), path: activePath }
  const language = activeFile.name.endsWith('.jsx') ? 'javascript' : activeFile.name.endsWith('.css') ? 'css' : activeFile.name.endsWith('.json') ? 'json' : activeFile.name.endsWith('.md') ? 'markdown' : 'plaintext'

  const openFile = async (node, path) => {
    setActivePath(path)
    setSelectedPath(path)
    setOpenFiles((current) => current.includes(path) ? current : [...current, path])
    if (!projectId) {
      setContent(demoContent[node.name] || `// ${path}\n`)
      setSaved(true)
      return
    }
    try {
      const response = await axios.get('/api/files', { params: { path: projectPath(path) } })
      setContent(response.data?.content ?? response.data ?? '')
      setSaved(true)
    } catch (error) {
      setContent(`// Could not read ${path}\n// ${error.response?.data?.error || error.message}\n`)
      setSaved(false)
    }
  }

  const closeFile = (path) => {
    const remaining = openFiles.filter((file) => file !== path)
    setOpenFiles(remaining)
    if (path === activePath) {
      const next = remaining[remaining.length - 1]
      if (next) openFile({ name: next.split('/').pop() }, next)
    }
  }

  const saveFile = async () => {
    if (!projectId || !activePath) return setSaved(true)
    setIsSaving(true)
    try {
      await axios.put('/api/files', content, { params: { path: projectPath(activePath) }, headers: { 'Content-Type': 'text/plain' } })
      setSaved(true)
    } catch (error) {
      setStatusMessage(error.response?.data?.error || error.message)
    } finally {
      setIsSaving(false)
    }
  }

  const [statusMessage, setStatusMessage] = useState('')

  useEffect(() => {
    if (saved || !activePath) return undefined
    const saveTimer = setTimeout(() => saveFile(), 900)
    return () => clearTimeout(saveTimer)
  }, [content, activePath, projectId, saved])

  const createEntry = async (kind) => {
    if (!projectId) return setStatusMessage('Create actions require a project URL.')
    const name = window.prompt(`Name of new ${kind}:`)
    if (!name?.trim()) return
    const parent = selectedPath ? projectPath(selectedPath) : projectId
    const path = `${parent}/${name.trim()}`
    try {
      await axios.post(`/api/files/${kind}`, null, { params: { path } })
      setStatusMessage(`${kind === 'file' ? 'File' : 'Folder'} created.`)
      await refreshTree()
    } catch (error) {
      setStatusMessage(error.response?.data?.error || error.message)
    }
  }

  const deleteEntry = async () => {
    if (!projectId || !selectedPath) return setStatusMessage('Select a project file or folder first.')
    if (!window.confirm(`Delete ${selectedPath}? This cannot be undone.`)) return
    try {
      await axios.delete('/api/files', { params: { path: projectPath(selectedPath) } })
      setStatusMessage('Deleted successfully.')
      setSelectedPath('')
      await refreshTree()
    } catch (error) {
      setStatusMessage(error.response?.data?.error || error.message)
    }
  }

  return <main className="playground-page">
    <header className="playground-topbar">
      <button
        className="mobile-sidebar-toggle"
        onClick={() => setSidebarOpen((value) => !value)}
        aria-label="Toggle project explorer"
      >
        ☰
      </button>

      <Link className="playground-brand" to="/">
        <span>⌘</span> CodeForge
      </Link>

      <div className="workspace-name">
        <span className="live-dot" />
        {projectId
          ? `PROJECT / ${projectId.slice(0, 8)}`
          : "PLAYGROUND / LOCAL"}
      </div>

      <div className="playground-actions">
        <span
          className={`save-label ${connectionStatus === "LIVE" ? "socket-live" : ""
            }`}
        >
          {projectId ? connectionStatus : "LOCAL"}
        </span>

        <span className="save-label">
          {isSaving ? "SAVING" : saved ? "AUTO-SAVED" : "UNSAVED"}
        </span>

        <button
          className="save-button"
          onClick={saveFile}
          disabled={isSaving}
        >
          Save now <span>⌘ S</span>
        </button>

        <Link className="exit-button" to="/">
          Exit
        </Link>
      </div>
    </header>

    <div className="editor-workspace">
      <aside
        className={`explorer ${sidebarOpen ? "explorer-open" : ""
          }`}
      >
        <div className="explorer-heading">
          <span>EXPLORER</span>

          <div className="explorer-actions">
            <button
              onClick={() => createEntry("file")}
              aria-label="New file"
            >
              ＋
            </button>

            <button
              onClick={() => createEntry("folder")}
              aria-label="New folder"
            >
              ▱
            </button>

            <button
              onClick={deleteEntry}
              aria-label="Delete selected path"
            >
              ⌫
            </button>
          </div>
        </div>

        <div className="project-root">
          <span className="tree-chevron">⌄</span>
          <span className="root-icon">⌘</span>

          <strong>
            {projectId ? "PROJECT FILES" : "CODEFORGE PROJECT"}
          </strong>
        </div>

        <div className="tree-list">
          {tree.map((node) => (
            <TreeNode
              key={node.name}
              node={node}
              onOpen={openFile}
              activePath={activePath}
              selectedPath={selectedPath}
              onSelect={setSelectedPath}
            />
          ))}
        </div>

        <div className="tree-status">
          <span className="status-pulse" />
          {treeStatus}

          {statusMessage && (
            <small>{statusMessage}</small>
          )}
        </div>
      </aside>

      <section className="editor-area">
        <div className="editor-tabs">
          {openFiles.length === 0 && (
            <div className="empty-tab">
              Double-click a file to open it
            </div>
          )}

          {openFiles.map((path) => (
            <button
              className={`editor-tab ${activePath === path ? "active-tab" : ""
                }`}
              key={path}
              onClick={() =>
                openFile(
                  {
                    name: path.split("/").pop(),
                  },
                  path
                )
              }
            >
              <span className="tab-file-icon">
                {getFileIcon(path.split("/").pop())}
              </span>

              {path.split("/").pop()}

              <span
                className="tab-close"
                onClick={(event) => {
                  event.stopPropagation();
                  closeFile(path);
                }}
              >
                ×
              </span>
            </button>
          ))}
        </div>

        <div className="editor-breadcrumb">
          <span>src</span>
          <b>/</b>
          <span>{activeFile.name}</span>

          <span className="editor-language">
            {language.toUpperCase()}
          </span>
        </div>

        <div className="monaco-wrap">

          <div
            className="monaco-editor-container"
            style={{
              height: isTerminalOpen ? "calc(100% - 200px)" : "100%",
            }}
          >
            <Editor
              height="100%"
              language={language}
              theme="vs-dark"
              value={content}
              onChange={(value) => {
                setContent(value || "");
                setSaved(false);
              }}
              options={{
                minimap: {
                  enabled: true,
                },
                fontSize: 14,
                fontFamily: "DM Mono, monospace",
                padding: {
                  top: 22,
                },
                smoothScrolling: true,
                wordWrap: "on",
                tabSize: 2,
              }}
            />
          </div>

          {isTerminalOpen ? (
            <div
              className="terminal-container"
              style={{
                height: "200px",
                position: "relative",
              }}
            >
              <Terminal />

              <FaArrowDown
                style={{
                  position: "absolute",
                  top: "5px",
                  right: "20px",
                  color: "gray",
                  fontSize: "20px",
                  cursor: "pointer",
                  zIndex: 10,
                  transform: "rotate(180deg)",
                }}
                onClick={() => setIsTerminalOpen(false)}
              />
            </div>
          ) : (
            <div
              className="terminal-closed"
              style={{
                height: "20px",
                position: "relative",
                backgroundColor: "#1e1e1e",
                borderTop: "1px solid #333",
              }}
            >
              <FaArrowDown
                style={{
                  color: "gray",
                  position: "absolute",
                  bottom: "2px",
                  left: "98%",
                  transform: "translateX(-50%)",
                  fontSize: "18px",
                  cursor: "pointer",
                }}
                onClick={() => setIsTerminalOpen(true)}
              />
            </div>
          )}
        </div>

        <footer className="editor-statusbar">
          <span>Ln 1, Col 1</span>
          <span>{language}</span>
          <span>UTF-8</span>
          <span>Spaces: 2</span>

          <span className="status-remote">
            {projectId ? connectionStatus : "LOCAL DEMO"}
          </span>
        </footer>
      </section>
    </div>
  </main>
}

export default PlaygroundPage
