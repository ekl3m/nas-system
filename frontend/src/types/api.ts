
// --- Odpowiedzi (Response) ---

export interface DiskInfo {
  path: string;
  totalSpaceMB: number;
  usableSpaceMB: number;
}

export interface SystemStats {
  disks: DiskInfo[];
  totalSpaceMB: number;
  totalUsableSpaceMB: number;
  cpuTemperature: number;
  usedMemoryMB: number;
  totalMemoryMB: number;
  systemUptime: string;
}

export interface FileInfo {
  logicalPath: string;
  parentPath: string;
  name: string;
  isDirectory: boolean;
  size: number;
  createdAt: string;   
  lastModified: string;
}

export interface FileOperationResponse {
  message: string;
  node: FileInfo | null;
}

export interface AuthResponse {
  token: string;
}

// --- Żądania (Request Bodies) ---

export interface CreateFolderRequest {
  logicalPath: string;
}

export interface MoveRequest {
  fromPath: string;
  toPath: string;
}

export interface RestoreRequest {
  logicalPath: string;
}