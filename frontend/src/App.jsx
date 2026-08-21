import { useEffect, useRef, useState } from 'react'
import './App.css'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8082'

function App() {
  const [selectedFile, setSelectedFile] = useState(null)
  const [isDragging, setIsDragging] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [isPolling, setIsPolling] = useState(false)
  const [job, setJob] = useState(null)
  const [error, setError] = useState('')
  const [downloadUrl, setDownloadUrl] = useState('')
  const jobIdRef = useRef(null)

  useEffect(() => {
    if (!jobIdRef.current || isPolling === false) {
      return undefined
    }

    let cancelled = false

    const pollStatus = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/jobs/${jobIdRef.current}`)
        if (!response.ok) {
          throw new Error('Unable to fetch job status.')
        }

        const data = await response.json()
        if (cancelled) {
          return
        }

        setJob(data)

        if (data.status === 'COMPLETED') {
          setIsPolling(false)
          await fetchDownloadUrl(jobIdRef.current)
          return
        }

        if (data.status === 'FAILED') {
          setError('The PDF conversion failed. Please try again with a different file.')
          setIsPolling(false)
          return
        }

        window.setTimeout(pollStatus, 2000)
      } catch (pollError) {
        if (!cancelled) {
          setError(pollError.message)
          setIsPolling(false)
        }
      }
    }

    pollStatus()

    return () => {
      cancelled = true
    }
  }, [isPolling])

  const fetchDownloadUrl = async (jobId) => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/jobs/${jobId}/download`)
      if (!response.ok) {
        throw new Error('Download endpoint is not ready yet.')
      }

      const data = await response.json()
      setDownloadUrl(data.url)
      setError('')
      return data
    } catch (downloadError) {
      setError(downloadError.message)
      return null
    }
  }

  const handleFileSelection = (file) => {
    if (!file) {
      return
    }

    if (!file.name.toLowerCase().endsWith('.txt')) {
      setError('Please select a .txt file.')
      return
    }

    setSelectedFile(file)
    setError('')
    setDownloadUrl('')
    setJob(null)
  }

  const handleUpload = async () => {
    if (!selectedFile) {
      setError('Choose a text file before uploading.')
      return
    }

    setIsUploading(true)
    setError('')

    try {
      const formData = new FormData()
      formData.append('userId', 'demo-user')
      formData.append('jobType', 'TXT_TO_PDF')
      formData.append('file', selectedFile)

      const response = await fetch(`${API_BASE_URL}/api/jobs/upload`, {
        method: 'POST',
        body: formData,
      })

      if (!response.ok) {
        const payload = await response.text()
        throw new Error(payload || 'Unable to upload the file.')
      }

      const createdJob = await response.json()
      jobIdRef.current = createdJob.jobId
      setJob(createdJob)
      setIsPolling(true)
    } catch (uploadError) {
      setError(uploadError.message)
    } finally {
      setIsUploading(false)
    }
  }

  const handleDownload = async () => {
    if (!job?.jobId) {
      return
    }

    const result = await fetchDownloadUrl(job.jobId)
    if (!result || !result.url) {
      return
    }

    const link = document.createElement('a')
    link.href = result.url
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
    link.download = result.fileName || 'converted.pdf'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  return (
    <main className="app-shell">
      <section className="upload-panel">
        <p className="eyebrow">TXT to PDF</p>
        <h1>Convert your text file into a PDF</h1>

        <label
          className={`dropzone ${isDragging ? 'dragging' : ''}`}
          onDragOver={(event) => {
            event.preventDefault()
            setIsDragging(true)
          }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={(event) => {
            event.preventDefault()
            setIsDragging(false)
            const file = event.dataTransfer.files?.[0]
            handleFileSelection(file)
          }}
        >
          <input
            type="file"
            accept=".txt,text/plain"
            onChange={(event) => handleFileSelection(event.target.files?.[0])}
          />
          <span className="dropzone-icon">⬆</span>
          <strong>{selectedFile ? selectedFile.name : 'Drag and drop a .txt file here'}</strong>
          <small>or click to choose a file</small>
        </label>

        <button type="button" className="primary-button" onClick={handleUpload} disabled={!selectedFile || isUploading}>
          {isUploading ? 'Uploading...' : 'Upload and convert'}
        </button>

        {error && <p className="error-text">{error}</p>}

        {job && (
          <div className="job-status">
            <div>
              <span className="status-label">Job ID</span>
              <strong>{job.jobId}</strong>
            </div>
            <div>
              <span className="status-label">Status</span>
              <strong>{job.status}</strong>
            </div>
          </div>
        )}

        {job?.status === 'COMPLETED' && (
          <button type="button" className="download-button" onClick={handleDownload}>
            Download PDF
          </button>
        )}

        {downloadUrl && (
          <a className="download-link" href={downloadUrl} target="_blank" rel="noreferrer">
            Open generated PDF in a new tab
          </a>
        )}
      </section>
    </main>
  )
}

export default App
