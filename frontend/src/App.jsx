import { useEffect, useRef, useState } from 'react'
import './App.css'

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8082'

function App() {
  const [mode, setMode] = useState('pdf')
  const [selectedFile, setSelectedFile] = useState(null)
  const [barcodeInput, setBarcodeInput] = useState('')
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
          setError(mode === 'pdf' ? 'The PDF conversion failed. Please try again with a different file.' : 'The barcode generation failed. Please try another value.')
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
  }, [isPolling, mode])

  const fetchDownloadUrl = async (jobId) => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/jobs/${jobId}/download`)
      if (!response.ok) {
        throw new Error('Download endpoint is not ready yet.')
      }

      const data = await response.json()
      data.url = new URL(data.url, `${API_BASE_URL}/`).toString()
      setDownloadUrl(data.url)
      setError('')
      return data
    } catch (downloadError) {
      setError(downloadError.message)
      return null
    }
  }

  const resetResults = () => {
    setError('')
    setDownloadUrl('')
    setJob(null)
    jobIdRef.current = null
    setIsPolling(false)
  }

  const handleModeChange = (nextMode) => {
    setMode(nextMode)
    resetResults()
    if (nextMode === 'pdf') {
      setBarcodeInput('')
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

  const handleBarcodeSubmit = async () => {
    const value = barcodeInput.trim()
    if (!value) {
      setError('Paste text or a URL before generating the barcode.')
      return
    }

    setIsUploading(true)
    setError('')

    try {
      const response = await fetch(`${API_BASE_URL}/api/jobs`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          userId: 'demo-user',
          inputText: value,
          jobType: 'BARCODE_GENERATOR',
        }),
      })

      if (!response.ok) {
        const payload = await response.text()
        throw new Error(payload || 'Unable to generate the barcode.')
      }

      const createdJob = await response.json()
      jobIdRef.current = createdJob.jobId
      setJob(createdJob)
      setIsPolling(true)
    } catch (submitError) {
      setError(submitError.message)
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
    const fileName = result.fileName || (mode === 'pdf' ? 'converted.pdf' : 'barcode.png')
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  const isPdfMode = mode === 'pdf'

  return (
    <main className="app-shell">
      <section className="upload-panel">
        <div className="mode-switcher" aria-label="Choose a generator type">
          <button
            type="button"
            className={isPdfMode ? 'mode-button active' : 'mode-button'}
            onClick={() => handleModeChange('pdf')}
          >
            Text to PDF
          </button>
          <button
            type="button"
            className={!isPdfMode ? 'mode-button active' : 'mode-button'}
            onClick={() => handleModeChange('barcode')}
          >
            Generate Barcode
          </button>
        </div>

        <p className="eyebrow">{isPdfMode ? 'TXT to PDF' : 'Barcode generator'}</p>
        <h1>{isPdfMode ? 'Convert your text file into a PDF' : 'Create a barcode from text or a URL'}</h1>

        {isPdfMode ? (
          <>
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
          </>
        ) : (
          <div className="barcode-form">
            <label className="input-label" htmlFor="barcode-input">
              Paste text or a URL
            </label>
            <textarea
              id="barcode-input"
              value={barcodeInput}
              placeholder="Example: https://example.com or ABC-12345"
              rows="6"
              onChange={(event) => setBarcodeInput(event.target.value)}
            />

            <button type="button" className="primary-button" onClick={handleBarcodeSubmit} disabled={isUploading || !barcodeInput.trim()}>
              {isUploading ? 'Generating...' : 'Generate barcode'}
            </button>
          </div>
        )}

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
            {isPdfMode ? 'Download PDF' : 'Download barcode image'}
          </button>
        )}

        {downloadUrl && (
          <a className="download-link" href={downloadUrl} target="_blank" rel="noreferrer">
            Open generated {isPdfMode ? 'PDF' : 'barcode image'} in a new tab
          </a>
        )}
      </section>
    </main>
  )
}

export default App
