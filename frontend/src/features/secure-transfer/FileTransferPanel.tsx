"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { DragEvent, FormEvent } from "react";
import {
  AlertCircle,
  Check,
  CheckCircle2,
  Copy,
  Download,
  FolderOpen,
  HardDrive,
  Loader2,
  Lock,
  RefreshCw,
  Search,
  ShieldCheck,
  Upload,
  UploadCloud,
  X,
} from "lucide-react";
import {
  downloadTransferFile,
  fetchTransferFiles,
  formatFileSize,
  isSafeTransferFileName,
  uploadTransferFile,
  type TransferFile,
} from "./api";

const MAX_CLIENT_BYTES = 500 * 1024 * 1024;

function fileExtensionLabel(fileName: string): string {
  const dot = fileName.lastIndexOf(".");
  if (dot <= 0 || dot === fileName.length - 1) return "FILE";
  return fileName.slice(dot + 1, dot + 5).toUpperCase();
}

export function FileTransferPanel() {
  const [files, setFiles] = useState<TransferFile[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [downloading, setDownloading] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<{ text: string; sha256?: string } | null>(null);
  const [copiedHash, setCopiedHash] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
    };
  }, []);

  const refresh = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const result = await fetchTransferFiles(signal);
      setFiles(result);
      setError(null);
    } catch (cause) {
      if (cause instanceof Error && cause.name === "AbortError") return;
      setError(cause instanceof Error ? cause.message : "Could not load the shared files.");
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let disposed = false;
    async function load() {
      try {
        const result = await fetchTransferFiles(controller.signal);
        if (!disposed) {
          setFiles(result);
          setError(null);
        }
      } catch (cause) {
        if (!disposed && !(cause instanceof Error && cause.name === "AbortError")) {
          setError(cause instanceof Error ? cause.message : "Could not load the shared files.");
        }
      } finally {
        if (!disposed) setLoading(false);
      }
    }
    void load();
    return () => {
      disposed = true;
      controller.abort();
    };
  }, []);

  const totalBytes = useMemo(
    () => (files ? files.reduce((sum, item) => sum + item.size, 0) : 0),
    [files]
  );

  const filteredFiles = useMemo(() => {
    if (!files) return null;
    const term = searchQuery.trim().toLowerCase();
    if (!term) return files;
    return files.filter(
      (file) =>
        file.fileName.toLowerCase().includes(term) ||
        file.sha256.toLowerCase().includes(term)
    );
  }, [files, searchQuery]);

  function clearSelectedFile() {
    setSelectedFile(null);
    if (inputRef.current) inputRef.current.value = "";
  }

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragging(false);
    if (busy) return;
    const dropped = event.dataTransfer.files?.[0] ?? null;
    if (dropped) {
      setSelectedFile(dropped);
      setError(null);
    }
  }

  async function copySha256(hash: string) {
    try {
      await navigator.clipboard?.writeText(hash);
      setCopiedHash(hash);
      if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
      copyTimerRef.current = setTimeout(() => setCopiedHash(null), 2000);
    } catch {
      /* Ignore clipboard unavailability in restricted browser contexts. */
    }
  }

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedFile) {
      setError("Choose a file before uploading.");
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const result = await uploadTransferFile(selectedFile);
      setNotice({
        text: `Uploaded and verified ${result.fileName} · SHA-256 ${result.sha256}`,
        sha256: result.sha256,
      });
      clearSelectedFile();
      await refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Upload failed.");
    } finally {
      setBusy(false);
    }
  }

  async function download(file: TransferFile) {
    setDownloading(file.fileName);
    setError(null);
    setNotice(null);
    try {
      const blob = await downloadTransferFile(file.fileName);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = file.fileName;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
      setNotice({
        text: `Downloaded and SHA-256 verified ${file.fileName}.`,
        sha256: file.sha256,
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Download failed.");
    } finally {
      setDownloading(null);
    }
  }

  const selectedNameInvalid = selectedFile ? !isSafeTransferFileName(selectedFile.name) : false;
  const selectedTooLarge = selectedFile ? selectedFile.size > MAX_CLIENT_BYTES : false;
  const isConnected = files !== null;

  return (
    <div className="mt-7 space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="theme-card theme-border rounded-2xl border p-5">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-slate-500">Service connection</p>
            <ShieldCheck size={16} className={isConnected ? "text-emerald-600" : "text-slate-400"} />
          </div>
          <div className="mt-2.5 flex items-center gap-2.5">
            <span
              className={`inline-block h-2.5 w-2.5 rounded-full ${
                isConnected
                  ? "bg-emerald-500 shadow-[0_0_0_4px_rgba(16,185,129,0.18)]"
                  : loading
                    ? "animate-pulse bg-amber-400"
                    : "bg-red-500"
              }`}
              aria-hidden="true"
            />
            <p className="text-lg font-bold tracking-tight text-slate-900">
              {isConnected ? "Connected" : loading ? "Connecting…" : "Offline"}
            </p>
          </div>
          <p className="mt-1 text-xs text-slate-500">Gateway :8080 → TLS Server :5004</p>
        </div>

        <div className="theme-card theme-border rounded-2xl border p-5">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-slate-500">Stored files</p>
            <FolderOpen size={16} className="text-[#2563EB]" />
          </div>
          <p className="mt-2 text-2xl font-bold tracking-tight text-slate-900">
            {files ? files.length : "—"}
          </p>
          <p className="mt-1 text-xs text-slate-500">Verified in TLS server storage</p>
        </div>

        <div className="theme-card theme-border rounded-2xl border p-5">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-slate-500">Total shared size</p>
            <HardDrive size={16} className="text-[#2563EB]" />
          </div>
          <p className="mt-2 text-2xl font-bold tracking-tight text-[#2563EB]">
            {files ? formatFileSize(totalBytes) : "—"}
          </p>
          <p className="mt-1 text-xs text-slate-500">Binary streaming · SHA-256 verified</p>
        </div>
      </div>

      <div className="grid items-start gap-6 xl:grid-cols-[0.9fr_1.15fr]">
        <section
          aria-labelledby="upload-heading"
          className="theme-card theme-border rounded-2xl border p-5 sm:p-6"
        >
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.14em] text-[#2563EB]">
                Encrypted upload
              </p>
              <h3 id="upload-heading" className="mt-1.5 text-lg font-bold tracking-tight text-slate-900">
                Send a file over TLS
              </h3>
            </div>
            <span
              aria-hidden="true"
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-[#2563EB] dark:bg-blue-950/40"
            >
              <Upload size={18} />
            </span>
          </div>

          <p className="mt-2 text-xs leading-5 text-slate-500">
            Drop a file or browse from your device. The Gateway streams raw bytes over an encrypted <code className="font-mono">SSLSocket</code> and checks SHA-256 before committing.
          </p>

          <form className="mt-5 space-y-4" onSubmit={upload}>
            <label
              htmlFor="transfer-file"
              onDragEnter={(event) => {
                event.preventDefault();
                if (!busy) setDragging(true);
              }}
              onDragOver={(event) => {
                event.preventDefault();
                if (!busy && !dragging) setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={handleDrop}
              className={`group flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-5 py-8 text-center transition ${
                dragging
                  ? "border-[#2563EB] theme-selected"
                  : "theme-border theme-subtle hover:border-[#2563EB]"
              } ${busy ? "pointer-events-none opacity-60" : ""}`}
            >
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-blue-50 text-[#2563EB] transition group-hover:scale-105 dark:bg-blue-950/50">
                <UploadCloud size={22} />
              </span>
              <span className="mt-3 text-sm font-semibold text-slate-900">
                {dragging ? "Drop your file here" : "Drop a file here, or click to browse"}
              </span>
              <span className="mt-1 text-xs text-slate-500">
                Any binary or text file · up to 500 MB
              </span>
              <input
                ref={inputRef}
                id="transfer-file"
                className="sr-only"
                type="file"
                onChange={(event) => {
                  setSelectedFile(event.currentTarget.files?.[0] ?? null);
                  setError(null);
                }}
                disabled={busy}
              />
            </label>

            {selectedFile && (
              <div className="theme-subtle theme-border rounded-xl border p-3.5">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="flex h-9 w-11 shrink-0 items-center justify-center rounded-lg bg-blue-50 font-mono text-[11px] font-bold text-[#2563EB] dark:bg-blue-950/50">
                      {fileExtensionLabel(selectedFile.name)}
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-900" title={selectedFile.name}>
                        {selectedFile.name}
                      </p>
                      <div className="mt-0.5 flex flex-wrap items-center gap-2 text-xs">
                        <span className="text-slate-500">{formatFileSize(selectedFile.size)}</span>
                        <span aria-hidden="true" className="text-slate-400">·</span>
                        {selectedNameInvalid ? (
                          <span className="font-medium text-amber-600 dark:text-amber-400">Invalid characters in filename</span>
                        ) : selectedTooLarge ? (
                          <span className="font-medium text-amber-600 dark:text-amber-400">Exceeds 500 MB client limit</span>
                        ) : (
                          <span className="font-medium text-emerald-600 dark:text-emerald-400">Ready for TLS transfer</span>
                        )}
                      </div>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={clearSelectedFile}
                    disabled={busy}
                    aria-label="Remove selected file"
                    className="theme-hover flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:text-slate-700 disabled:opacity-40"
                  >
                    <X size={16} />
                  </button>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={busy || !selectedFile}
              className="theme-primary inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl px-5 text-sm font-semibold shadow-sm transition disabled:cursor-not-allowed disabled:opacity-45"
            >
              {busy ? (
                <>
                  <Loader2 size={16} className="animate-spin" />
                  Encrypting and uploading…
                </>
              ) : (
                <>
                  <Lock size={15} />
                  Upload securely
                </>
              )}
            </button>
          </form>

          <p className="mt-4 text-xs leading-5 text-slate-500">
            Allowed filenames: 1–255 safe ASCII characters (letters, numbers, spaces, dots, parentheses, brackets, commas, hyphens, underscores).
          </p>

          <div className="theme-subtle theme-border mt-4 rounded-xl border p-3.5">
            <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-500">
              Transfer pipeline
            </p>
            <ol className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-slate-600">
              <li className="theme-surface theme-border rounded-lg border px-2.5 py-1 font-medium">1. Browser</li>
              <li aria-hidden="true" className="text-slate-400">→</li>
              <li className="theme-surface theme-border rounded-lg border px-2.5 py-1 font-medium">2. Gateway :8080</li>
              <li aria-hidden="true" className="text-slate-400">→</li>
              <li className="rounded-lg bg-blue-50 px-2.5 py-1 font-semibold text-[#2563EB] dark:bg-blue-950/50">
                3. TLS :5004 + SHA-256
              </li>
            </ol>
          </div>
        </section>

        <section
          aria-labelledby="files-heading"
          className="theme-card theme-border rounded-2xl border p-5 sm:p-6"
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.14em] text-slate-500">
                Shared storage
              </p>
              <h3 id="files-heading" className="mt-1.5 flex items-center gap-2 text-lg font-bold tracking-tight text-slate-900">
                <span>Available files</span>
                {files && (
                  <span className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-[#2563EB] dark:bg-blue-950/50">
                    {files.length}
                  </span>
                )}
              </h3>
            </div>

            <button
              type="button"
              onClick={() => void refresh()}
              disabled={loading}
              className="theme-surface theme-border theme-hover inline-flex h-9 items-center gap-1.5 rounded-xl border px-3.5 text-xs font-semibold text-slate-700 transition disabled:opacity-50"
            >
              <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
              {loading ? "Refreshing…" : "Refresh list"}
            </button>
          </div>

          {files && files.length > 0 && (
            <div className="relative mt-4">
              <Search
                size={15}
                className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              />
              <input
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Filter by filename or SHA-256…"
                aria-label="Filter files by filename or SHA-256"
                className="theme-input theme-border theme-text h-10 w-full rounded-xl border pr-4 pl-10 text-sm placeholder:text-slate-400 focus:border-[#2563EB] focus:outline-none"
              />
            </div>
          )}

          {error && (
            <div
              role="alert"
              className="mt-4 flex items-start justify-between gap-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800 dark:border-red-900/80 dark:bg-red-950/40 dark:text-red-200"
            >
              <div className="flex items-start gap-2.5">
                <AlertCircle size={16} className="mt-0.5 shrink-0 text-red-600 dark:text-red-400" />
                <span>{error}</span>
              </div>
              <button
                type="button"
                onClick={() => setError(null)}
                aria-label="Dismiss error"
                className="shrink-0 rounded-lg p-1 text-red-500 hover:bg-red-100 dark:hover:bg-red-900/40"
              >
                <X size={14} />
              </button>
            </div>
          )}

          {notice && (
            <div
              role="status"
              className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900 dark:border-emerald-900/80 dark:bg-emerald-950/40 dark:text-emerald-200"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex min-w-0 items-start gap-2.5">
                  <CheckCircle2 size={16} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" />
                  <span className="break-all">{notice.text}</span>
                </div>
                <button
                  type="button"
                  onClick={() => setNotice(null)}
                  aria-label="Dismiss notice"
                  className="shrink-0 rounded-lg p-1 text-emerald-600 hover:bg-emerald-100 dark:text-emerald-400 dark:hover:bg-emerald-900/40"
                >
                  <X size={14} />
                </button>
              </div>
              {notice.sha256 && (
                <div className="mt-2.5 pl-6">
                  <button
                    type="button"
                    onClick={() => void copySha256(notice.sha256!)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-300 bg-white px-2.5 py-1 text-xs font-semibold text-emerald-800 transition hover:bg-emerald-50 dark:border-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200"
                  >
                    {copiedHash === notice.sha256 ? <Check size={12} /> : <Copy size={12} />}
                    {copiedHash === notice.sha256 ? "Copied SHA-256!" : "Copy full SHA-256"}
                  </button>
                </div>
              )}
            </div>
          )}

          <div className="mt-5">
            {loading && !files ? (
              <div className="theme-subtle flex flex-col items-center justify-center rounded-xl px-4 py-12 text-center">
                <Loader2 size={22} className="animate-spin text-[#2563EB]" />
                <p className="mt-3 text-sm font-medium text-slate-500">
                  Connecting to the secure file service…
                </p>
              </div>
            ) : filteredFiles && filteredFiles.length > 0 ? (
              <ul className="space-y-2.5">
                {filteredFiles.map((file) => {
                  const isCopied = copiedHash === file.sha256;
                  const isDownloadingThis = downloading === file.fileName;
                  return (
                    <li
                      key={file.fileName}
                      className="theme-subtle theme-border flex flex-wrap items-center justify-between gap-3 rounded-xl border p-3.5 transition hover:border-[#2563EB]"
                    >
                      <div className="flex min-w-0 flex-1 items-center gap-3">
                        <span className="theme-surface theme-border flex h-10 w-11 shrink-0 items-center justify-center rounded-lg border font-mono text-[11px] font-bold text-[#2563EB]">
                          {fileExtensionLabel(file.fileName)}
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-semibold text-slate-900" title={file.fileName}>
                            {file.fileName}
                          </p>
                          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                            <span className="font-medium">{formatFileSize(file.size)}</span>
                            <span aria-hidden="true">·</span>
                            <button
                              type="button"
                              onClick={() => void copySha256(file.sha256)}
                              title={`Click to copy full SHA-256: ${file.sha256}`}
                              className="theme-surface theme-border inline-flex items-center gap-1 rounded-md border px-2 py-0.5 font-mono text-[11px] text-slate-600 transition hover:border-[#2563EB] hover:text-[#2563EB]"
                            >
                              <code title={file.sha256}>
                                {isCopied ? "Copied!" : `${file.sha256.slice(0, 10)}…${file.sha256.slice(-6)}`}
                              </code>
                              {isCopied ? <Check size={11} /> : <Copy size={11} className="opacity-65" />}
                            </button>
                          </div>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => void download(file)}
                        disabled={downloading !== null}
                        className="theme-primary inline-flex h-9 shrink-0 items-center justify-center gap-1.5 rounded-xl px-3.5 text-xs font-semibold shadow-2xs transition disabled:opacity-50"
                      >
                        {isDownloadingThis ? (
                          <>
                            <Loader2 size={14} className="animate-spin" />
                            Verifying…
                          </>
                        ) : (
                          <>
                            <Download size={14} />
                            Download
                          </>
                        )}
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : files && files.length > 0 ? (
              <div className="theme-subtle theme-border rounded-xl border border-dashed px-4 py-10 text-center">
                <p className="text-sm font-semibold text-slate-900">No matching files</p>
                <p className="mt-1 text-xs text-slate-500">
                  No files match &ldquo;{searchQuery}&rdquo;.
                </p>
                <button
                  type="button"
                  onClick={() => setSearchQuery("")}
                  className="theme-surface theme-border theme-hover mt-3 rounded-lg border px-3.5 py-1.5 text-xs font-semibold text-slate-700"
                >
                  Clear filter
                </button>
              </div>
            ) : files ? (
              <div className="theme-subtle theme-border rounded-xl border border-dashed px-5 py-12 text-center">
                <span className="theme-surface theme-border mx-auto flex h-11 w-11 items-center justify-center rounded-xl border text-slate-400">
                  <FolderOpen size={20} />
                </span>
                <p className="mt-3 text-sm font-semibold text-slate-900">No files shared yet</p>
                <p className="mt-1 text-xs text-slate-500">
                  Drop or select a file on the left to test encrypted TLS upload and SHA-256 verification.
                </p>
              </div>
            ) : (
              <div className="rounded-xl border border-amber-200 bg-amber-50 px-5 py-10 text-center dark:border-amber-900/60 dark:bg-amber-950/30">
                <p className="text-sm font-semibold text-amber-900 dark:text-amber-200">
                  Secure file service is offline
                </p>
                <p className="mt-1.5 text-xs leading-5 text-amber-800 dark:text-amber-300">
                  Ensure <code className="font-mono">SecureTransferServer</code> (:5004) and <code className="font-mono">GatewayServer</code> (:8080) are running, then retry.
                </p>
                <button
                  type="button"
                  onClick={() => void refresh()}
                  className="mt-4 inline-flex h-9 items-center gap-1.5 rounded-xl bg-amber-600 px-4 text-xs font-semibold text-white transition hover:bg-amber-700"
                >
                  <RefreshCw size={13} />
                  Retry connection
                </button>
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
