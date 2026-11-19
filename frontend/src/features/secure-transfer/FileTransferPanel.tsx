"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { DragEvent, FormEvent } from "react";
import {
  downloadTransferFile,
  fetchTransferFiles,
  formatFileSize,
  isSafeTransferFileName,
  uploadTransferFile,
  type TransferFile,
} from "./api";

const MAX_CLIENT_BYTES = 100 * 1024 * 1024;

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
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-2xl border border-slate-200/90 bg-white/90 p-5 shadow-xs dark:border-slate-800 dark:bg-slate-900/75">
          <p className="text-xs font-medium text-slate-500 dark:text-slate-400">Service connection</p>
          <div className="mt-2.5 flex items-center gap-2.5">
            <span
              className={`inline-block size-2.5 rounded-full ${
                isConnected
                  ? "bg-emerald-500 shadow-[0_0_0_4px_rgba(16,185,129,0.18)]"
                  : loading
                    ? "animate-pulse bg-amber-400"
                    : "bg-rose-500"
              }`}
              aria-hidden="true"
            />
            <p className="text-lg font-semibold tracking-tight text-slate-950 dark:text-white">
              {isConnected ? "Connected" : loading ? "Connecting…" : "Offline"}
            </p>
          </div>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Gateway :8080 → TLS Server :5004
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200/90 bg-white/90 p-5 shadow-xs dark:border-slate-800 dark:bg-slate-900/75">
          <p className="text-xs font-medium text-slate-500 dark:text-slate-400">Stored files</p>
          <p className="mt-2 text-2xl font-bold tracking-tight text-slate-950 dark:text-white">
            {files ? files.length : "—"}
          </p>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Verified in encrypted server storage
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200/90 bg-white/90 p-5 shadow-xs dark:border-slate-800 dark:bg-slate-900/75">
          <p className="text-xs font-medium text-slate-500 dark:text-slate-400">Total shared size</p>
          <p className="mt-2 text-2xl font-bold tracking-tight text-emerald-700 dark:text-emerald-400">
            {files ? formatFileSize(totalBytes) : "—"}
          </p>
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            Binary streaming · up to 100 MB per file
          </p>
        </div>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[0.9fr_1.15fr]">
        <section
          aria-labelledby="upload-heading"
          className="rounded-3xl border border-slate-200/90 bg-white p-6 shadow-sm shadow-slate-900/5 sm:p-7 dark:border-slate-800 dark:bg-slate-900/80"
        >
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-emerald-700 dark:text-emerald-400">
                Encrypted upload
              </p>
              <h2 id="upload-heading" className="mt-1.5 text-xl font-semibold tracking-tight text-slate-950 dark:text-white">
                Send a file over TLS
              </h2>
            </div>
            <span
              aria-hidden="true"
              className="grid size-11 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-emerald-50 to-teal-100 text-emerald-700 ring-1 ring-emerald-200/70 dark:from-emerald-950 dark:to-teal-950 dark:text-emerald-300 dark:ring-emerald-800"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" className="size-5">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 16V4m0 0-4 4m4-4 4 4M4 17v1a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-1" />
              </svg>
            </span>
          </div>

          <p className="mt-2.5 text-sm leading-6 text-slate-600 dark:text-slate-400">
            Drag and drop a file or browse from your device. The Gateway streams bytes over TLS and verifies SHA-256 before committing.
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
                  ? "border-emerald-500 bg-emerald-50/70 dark:border-emerald-400 dark:bg-emerald-950/40"
                  : "border-slate-300 bg-slate-50/70 hover:border-emerald-400 hover:bg-emerald-50/30 dark:border-slate-700 dark:bg-slate-950/60 dark:hover:border-emerald-500/70"
              } ${busy ? "pointer-events-none opacity-60" : ""}`}
            >
              <span className="grid size-12 place-items-center rounded-2xl bg-white text-emerald-700 shadow-xs ring-1 ring-slate-200 transition group-hover:scale-105 dark:bg-slate-900 dark:text-emerald-400 dark:ring-slate-700">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="size-6" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M7 16a4 4 0 0 1-.88-7.9A5.5 5.5 0 0 1 16.9 6.7 4.5 4.5 0 0 1 17.5 16H15m-3 4v-8m0 0-3 3m3-3 3 3" />
                </svg>
              </span>
              <span className="mt-3.5 text-sm font-semibold text-slate-900 dark:text-white">
                {dragging ? "Drop your file here" : "Drop a file here, or click to browse"}
              </span>
              <span className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                Any binary or text file · up to 100 MB
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
              <div className="rounded-2xl border border-slate-200 bg-slate-50/80 p-3.5 dark:border-slate-800 dark:bg-slate-950/70">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex min-w-0 items-center gap-3">
                    <span className="grid h-10 w-11 shrink-0 place-items-center rounded-xl bg-emerald-100 font-mono text-[11px] font-bold text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
                      {fileExtensionLabel(selectedFile.name)}
                    </span>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-900 dark:text-white" title={selectedFile.name}>
                        {selectedFile.name}
                      </p>
                      <div className="mt-0.5 flex flex-wrap items-center gap-2 text-xs">
                        <span className="text-slate-500 dark:text-slate-400">{formatFileSize(selectedFile.size)}</span>
                        <span aria-hidden="true" className="text-slate-300 dark:text-slate-700">·</span>
                        {selectedNameInvalid ? (
                          <span className="font-medium text-amber-700 dark:text-amber-400">Invalid characters in filename</span>
                        ) : selectedTooLarge ? (
                          <span className="font-medium text-amber-700 dark:text-amber-400">Exceeds 100 MB client limit</span>
                        ) : (
                          <span className="font-medium text-emerald-700 dark:text-emerald-400">Ready for TLS transfer</span>
                        )}
                      </div>
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={clearSelectedFile}
                    disabled={busy}
                    aria-label="Remove selected file"
                    className="grid size-8 shrink-0 place-items-center rounded-full text-slate-400 transition hover:bg-slate-200/70 hover:text-slate-700 disabled:opacity-40 dark:hover:bg-slate-800 dark:hover:text-slate-200"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={busy || !selectedFile}
              className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-emerald-600 px-5 text-sm font-semibold text-white shadow-sm shadow-emerald-600/20 transition hover:bg-emerald-700 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-600 disabled:cursor-not-allowed disabled:bg-slate-200 disabled:text-slate-500 disabled:shadow-none dark:bg-emerald-500 dark:text-slate-950 dark:hover:bg-emerald-400 dark:disabled:bg-slate-800 dark:disabled:text-slate-500"
            >
              {busy ? (
                <>
                  <svg className="size-4 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v3a5 5 0 0 0-5 5H4Z" />
                  </svg>
                  Encrypting and uploading…
                </>
              ) : (
                <>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-4" aria-hidden="true">
                    <rect width="18" height="11" x="3" y="11" rx="2" />
                    <path strokeLinecap="round" strokeLinejoin="round" d="M7 11V7a5 5 0 0 1 10 0v4" />
                  </svg>
                  Upload securely
                </>
              )}
            </button>
          </form>

          <p className="mt-4 text-xs leading-5 text-slate-500 dark:text-slate-400">
            Allowed filenames: 1–128 ASCII letters, numbers, spaces, dots, underscores, and hyphens.
          </p>

          <div className="mt-5 rounded-2xl border border-slate-100 bg-slate-50/80 p-4 dark:border-slate-800/80 dark:bg-slate-950/60">
            <p className="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">
              Transfer pipeline
            </p>
            <ol className="mt-2.5 flex flex-wrap items-center gap-2 text-xs text-slate-600 dark:text-slate-300">
              <li className="rounded-lg bg-white px-2.5 py-1 font-medium ring-1 ring-slate-200/80 dark:bg-slate-900 dark:ring-slate-800">
                1. Browser
              </li>
              <li aria-hidden="true" className="text-slate-400">→</li>
              <li className="rounded-lg bg-white px-2.5 py-1 font-medium ring-1 ring-slate-200/80 dark:bg-slate-900 dark:ring-slate-800">
                2. Gateway :8080
              </li>
              <li aria-hidden="true" className="text-slate-400">→</li>
              <li className="rounded-lg bg-emerald-50 px-2.5 py-1 font-semibold text-emerald-800 ring-1 ring-emerald-200/80 dark:bg-emerald-950/70 dark:text-emerald-300 dark:ring-emerald-800">
                3. TLS :5004 + SHA-256
              </li>
            </ol>
          </div>
        </section>

        <section
          aria-labelledby="files-heading"
          className="rounded-3xl border border-slate-200/90 bg-white p-6 shadow-sm shadow-slate-900/5 sm:p-7 dark:border-slate-800 dark:bg-slate-900/80"
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
                Shared storage
              </p>
              <h2 id="files-heading" className="mt-1.5 flex items-center gap-2.5 text-xl font-semibold tracking-tight text-slate-950 dark:text-white">
                <span>Available files</span>
                {files && (
                  <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                    {files.length}
                  </span>
                )}
              </h2>
            </div>

            <button
              type="button"
              onClick={() => void refresh()}
              disabled={loading}
              className="inline-flex h-9 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-700 transition hover:border-emerald-500 hover:text-emerald-700 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:border-emerald-500 dark:hover:text-emerald-300"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                className={`size-3.5 ${loading ? "animate-spin" : ""}`}
                aria-hidden="true"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h5M20 20v-5h-5M5.64 18.36A9 9 0 0 0 20.49 15M18.36 5.64A9 9 0 0 0 3.51 9" />
              </svg>
              {loading ? "Refreshing…" : "Refresh list"}
            </button>
          </div>

          {files && files.length > 0 && (
            <div className="relative mt-4">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-slate-400"
                aria-hidden="true"
              >
                <circle cx="11" cy="11" r="7" />
                <path strokeLinecap="round" strokeLinejoin="round" d="m20 20-3.5-3.5" />
              </svg>
              <input
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Filter by filename or SHA-256…"
                aria-label="Filter files by filename or SHA-256"
                className="h-10 w-full rounded-xl border border-slate-200 bg-slate-50/70 pr-4 pl-10 text-sm text-slate-900 placeholder:text-slate-400 focus:border-emerald-500 focus:bg-white focus:outline-none dark:border-slate-800 dark:bg-slate-950/60 dark:text-white dark:placeholder:text-slate-500 dark:focus:border-emerald-500 dark:focus:bg-slate-950"
              />
            </div>
          )}

          {error && (
            <div
              role="alert"
              className="mt-4 flex items-start justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3.5 text-sm text-rose-900 dark:border-rose-900/80 dark:bg-rose-950/50 dark:text-rose-200"
            >
              <div className="flex items-start gap-2.5">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="mt-0.5 size-4 shrink-0 text-rose-600 dark:text-rose-400" aria-hidden="true">
                  <circle cx="12" cy="12" r="9" />
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4m0 4h.01" />
                </svg>
                <span>{error}</span>
              </div>
              <button
                type="button"
                onClick={() => setError(null)}
                aria-label="Dismiss error"
                className="shrink-0 rounded-lg p-1 text-rose-500 hover:bg-rose-100 dark:hover:bg-rose-900/50"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-3.5" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          )}

          {notice && (
            <div
              role="status"
              className="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3.5 text-sm text-emerald-950 dark:border-emerald-900/80 dark:bg-emerald-950/50 dark:text-emerald-200"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="flex min-w-0 items-start gap-2.5">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="mt-0.5 size-4 shrink-0 text-emerald-600 dark:text-emerald-400" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
                  </svg>
                  <span className="break-all">{notice.text}</span>
                </div>
                <button
                  type="button"
                  onClick={() => setNotice(null)}
                  aria-label="Dismiss notice"
                  className="shrink-0 rounded-lg p-1 text-emerald-600 hover:bg-emerald-100 dark:text-emerald-400 dark:hover:bg-emerald-900/50"
                >
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-3.5" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              {notice.sha256 && (
                <div className="mt-2.5 pl-6">
                  <button
                    type="button"
                    onClick={() => void copySha256(notice.sha256!)}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-white/90 px-2.5 py-1 text-xs font-semibold text-emerald-800 ring-1 ring-emerald-200 transition hover:bg-white dark:bg-emerald-900/50 dark:text-emerald-200 dark:ring-emerald-800"
                  >
                    {copiedHash === notice.sha256 ? "Copied SHA-256!" : "Copy full SHA-256"}
                  </button>
                </div>
              )}
            </div>
          )}

          <div className="mt-5">
            {loading && !files ? (
              <div className="flex flex-col items-center justify-center rounded-2xl bg-slate-50 px-4 py-12 text-center dark:bg-slate-950/60">
                <svg className="size-6 animate-spin text-emerald-600 dark:text-emerald-400" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v3a5 5 0 0 0-5 5H4Z" />
                </svg>
                <p className="mt-3 text-sm font-medium text-slate-600 dark:text-slate-400">
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
                      className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-slate-200/80 bg-slate-50/60 p-3.5 transition hover:border-emerald-300/80 hover:bg-white dark:border-slate-800 dark:bg-slate-950/50 dark:hover:border-emerald-800 dark:hover:bg-slate-900/70"
                    >
                      <div className="flex min-w-0 flex-1 items-center gap-3">
                        <span className="grid h-10 w-11 shrink-0 place-items-center rounded-xl bg-white font-mono text-[11px] font-bold text-slate-700 ring-1 ring-slate-200/90 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700">
                          {fileExtensionLabel(file.fileName)}
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-semibold text-slate-950 dark:text-white" title={file.fileName}>
                            {file.fileName}
                          </p>
                          <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                            <span className="font-medium text-slate-600 dark:text-slate-300">{formatFileSize(file.size)}</span>
                            <span aria-hidden="true">·</span>
                            <button
                              type="button"
                              onClick={() => void copySha256(file.sha256)}
                              title={`Click to copy full SHA-256: ${file.sha256}`}
                              className="inline-flex items-center gap-1 rounded-md bg-white px-2 py-0.5 font-mono text-[11px] text-slate-600 ring-1 ring-slate-200/90 transition hover:text-emerald-700 hover:ring-emerald-300 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700 dark:hover:text-emerald-300"
                            >
                              <code title={file.sha256}>
                                {isCopied ? "Copied!" : `${file.sha256.slice(0, 10)}…${file.sha256.slice(-6)}`}
                              </code>
                              {!isCopied && (
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-3 opacity-65" aria-hidden="true">
                                  <rect width="13" height="13" x="9" y="9" rx="2" />
                                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                                </svg>
                              )}
                            </button>
                          </div>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => void download(file)}
                        disabled={downloading !== null}
                        className="inline-flex h-9 shrink-0 items-center justify-center gap-1.5 rounded-full border border-slate-200 bg-white px-4 text-xs font-semibold text-slate-800 shadow-2xs transition hover:border-emerald-500 hover:bg-emerald-600 hover:text-white disabled:opacity-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:border-emerald-500 dark:hover:bg-emerald-500 dark:hover:text-slate-950"
                      >
                        {isDownloadingThis ? (
                          <>
                            <svg className="size-3.5 animate-spin" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 0 1 8-8v3a5 5 0 0 0-5 5H4Z" />
                            </svg>
                            Verifying…
                          </>
                        ) : (
                          <>
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="size-3.5" aria-hidden="true">
                              <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v12m0 0-4-4m4 4 4-4M4 17v1a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-1" />
                            </svg>
                            Download
                          </>
                        )}
                      </button>
                    </li>
                  );
                })}
              </ul>
            ) : files && files.length > 0 ? (
              <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 px-4 py-10 text-center dark:border-slate-800 dark:bg-slate-950/60">
                <p className="text-sm font-semibold text-slate-800 dark:text-slate-200">No matching files</p>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  No files match &ldquo;{searchQuery}&rdquo;.
                </p>
                <button
                  type="button"
                  onClick={() => setSearchQuery("")}
                  className="mt-3 rounded-full border border-slate-300 bg-white px-3.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                >
                  Clear filter
                </button>
              </div>
            ) : files ? (
              <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50/70 px-5 py-12 text-center dark:border-slate-800 dark:bg-slate-950/60">
                <span className="mx-auto grid size-12 place-items-center rounded-2xl bg-white text-slate-400 ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="size-6" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />
                  </svg>
                </span>
                <p className="mt-4 text-sm font-semibold text-slate-900 dark:text-white">No files shared yet</p>
                <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  Drop or choose a file on the left to test encrypted TLS upload and SHA-256 verification.
                </p>
              </div>
            ) : (
              <div className="rounded-2xl border border-amber-200/80 bg-amber-50/70 px-5 py-10 text-center dark:border-amber-900/60 dark:bg-amber-950/30">
                <p className="text-sm font-semibold text-amber-950 dark:text-amber-200">
                  Secure file service is offline
                </p>
                <p className="mt-1.5 text-xs leading-5 text-amber-800/90 dark:text-amber-300/80">
                  Ensure <code className="font-mono">SecureTransferServer</code> (:5004) and <code className="font-mono">GatewayServer</code> (:8080) are running, then retry.
                </p>
                <button
                  type="button"
                  onClick={() => void refresh()}
                  className="mt-4 inline-flex h-9 items-center gap-1.5 rounded-full bg-amber-900 px-4 text-xs font-semibold text-white transition hover:bg-amber-950 dark:bg-amber-400 dark:text-slate-950 dark:hover:bg-amber-300"
                >
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
