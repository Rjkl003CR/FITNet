export interface TransferFile {
  fileName: string;
  size: number;
  sha256: string;
}

const gateway = (process.env.NEXT_PUBLIC_GATEWAY_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const fileNamePattern = /^[A-Za-z0-9][A-Za-z0-9._ ()\[\],&'-]{0,254}$/;

export function isSafeTransferFileName(value: string): boolean {
  return fileNamePattern.test(value) && value !== "." && value !== ".." && !/[. ]$/.test(value);
}

function parseTransferFiles(value: unknown): TransferFile[] {
  if (!Array.isArray(value)) throw new Error("The file service returned an invalid list.");
  const seen = new Set<string>();
  return value.map((item: unknown) => {
    if (!item || typeof item !== "object") throw new Error("The file service returned invalid metadata.");
    const file = item as Record<string, unknown>;
    if (typeof file.fileName !== "string" || !isSafeTransferFileName(file.fileName)
      || typeof file.size !== "number" || !Number.isSafeInteger(file.size) || file.size < 0
      || typeof file.sha256 !== "string" || !/^[a-f0-9]{64}$/.test(file.sha256)
      || seen.has(file.fileName)) {
      throw new Error("The file service returned invalid file metadata.");
    }
    seen.add(file.fileName);
    return { fileName: file.fileName, size: file.size, sha256: file.sha256 };
  });
}

async function readError(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === "object" && typeof (body as Record<string, unknown>).error === "string") {
      return (body as { error: string }).error;
    }
  } catch { /* Keep a useful generic message if the Gateway response is not JSON. */ }
  return `File service request failed (HTTP ${response.status}).`;
}

export async function fetchTransferFiles(signal?: AbortSignal): Promise<TransferFile[]> {
  const response = await fetch(`${gateway}/api/files`, {
    signal,
    cache: "no-store",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(await readError(response));
  if (!response.headers.get("content-type")?.includes("application/json")) {
    throw new Error("The file service did not return JSON.");
  }
  return parseTransferFiles(await response.json());
}

export async function uploadTransferFile(file: File, signal?: AbortSignal): Promise<TransferFile> {
  if (!isSafeTransferFileName(file.name)) {
    throw new Error("Use a file name with up to 255 safe ASCII characters without path separators.");
  }
  if (file.size > 500 * 1024 * 1024) throw new Error("The upload limit is 500 MB.");
  const response = await fetch(`${gateway}/api/files`, {
    method: "POST",
    signal,
    headers: { "Content-Type": "application/octet-stream", "X-Filename": file.name },
    body: file,
  });
  if (!response.ok) throw new Error(await readError(response));
  const metadata: unknown = await response.json();
  return parseTransferFiles([metadata])[0];
}

export async function downloadTransferFile(fileName: string, signal?: AbortSignal): Promise<Blob> {
  if (!isSafeTransferFileName(fileName)) throw new Error("Invalid file name.");
  const response = await fetch(`${gateway}/api/files/${encodeURIComponent(fileName)}`, {
    signal,
    headers: { Accept: "application/octet-stream" },
  });
  if (!response.ok) throw new Error(await readError(response));
  return response.blob();
}

export function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}
