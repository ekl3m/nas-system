import React, { useState, useEffect, useRef, useMemo } from 'react';
import { 
  Folder, FileText, FileImage, FileVideo, Download, Trash2, Upload, Home, 
  Plus, RotateCcw, XCircle, AlertTriangle, Music, FileCode, HardDrive, Search, X, Eye,
  Save, ChevronUp, ChevronDown, ChevronLeft, ChevronRight, CornerUpLeft, ArrowLeft
} from 'lucide-react';
import { fileService } from '../services/api';
import type { FileInfo } from '../types/api';
import { useModal } from '../context/ModalContext';
import { useTransfer } from '../context/TransferContext';

export const Files = () => {
  const modal = useModal();
  const { addUpload } = useTransfer();

  const [files, setFiles] = useState<FileInfo[]>([]);
  const [currentPath, setCurrentPath] = useState('');
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<'files' | 'trash'>('files');
  
  const [searchQuery, setSearchQuery] = useState('');
  const [isDragging, setIsDragging] = useState(false);
  
  const [sortConfig, setSortConfig] = useState<{ key: 'name' | 'size' | 'date', direction: 'asc' | 'desc' }>({ key: 'name', direction: 'asc' });

  const [previewFile, setPreviewFile] = useState<{ url: string, type: 'image' | 'video', name: string, originalFile: FileInfo } | null>(null);

  const [contextMenu, setContextMenu] = useState<{ x: number, y: number, file: FileInfo } | null>(null);
  const [editor, setEditor] = useState<{ isOpen: boolean, content: string, file: FileInfo | null, saving: boolean } | null>(null);

  const [selectedFiles, setSelectedFiles] = useState<Set<string>>(new Set());
  const [lastSelectedIndex, setLastSelectedIndex] = useState<number | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadFiles();
    setSearchQuery(''); 
    setSelectedFiles(new Set());
    setLastSelectedIndex(null);
  }, [currentPath, viewMode]);

  useEffect(() => {
    const handleClick = () => setContextMenu(null);
    window.addEventListener('click', handleClick);
    return () => window.removeEventListener('click', handleClick);
  }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (previewFile) {
        if (e.key === 'ArrowRight') navigatePreview('next');
        if (e.key === 'ArrowLeft') navigatePreview('prev');
        if (e.key === 'Escape') closePreview();
      }
      if (!previewFile && !editor?.isOpen) {
        if (e.key === 'Escape') {
          setSelectedFiles(new Set());
        }
        if ((e.ctrlKey || e.metaKey) && e.key === 'a') {
          e.preventDefault();
          const allNames = processedFiles.map(f => f.name);
          setSelectedFiles(new Set(allNames));
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [previewFile, files, editor]);

  const loadFiles = async () => {
    setLoading(true);
    setFiles([]);
    try {
      let data: FileInfo[] = [];
      if (viewMode === 'trash') {
        data = await fileService.getTrashFiles();
      } else {
        data = await fileService.listFiles(currentPath);
      }
      setFiles(data);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const handleSort = (key: 'name' | 'size' | 'date') => {
    setSortConfig(current => ({
      key,
      direction: current.key === key && current.direction === 'asc' ? 'desc' : 'asc'
    }));
  };

  const processedFiles = useMemo(() => {
    let result = files.filter(f => f.name.toLowerCase().includes(searchQuery.toLowerCase()));

    return result.sort((a, b) => {
      if (a.isDirectory !== b.isDirectory) {
        return a.isDirectory ? -1 : 1;
      }

      let comparison = 0;
      switch (sortConfig.key) {
        case 'name':
          comparison = a.name.localeCompare(b.name);
          break;
        case 'size':
          comparison = a.size - b.size;
          break;
        case 'date':
          comparison = new Date(a.lastModified).getTime() - new Date(b.lastModified).getTime();
          break;
      }

      return sortConfig.direction === 'asc' ? comparison : -comparison;
    });
  }, [files, searchQuery, sortConfig]);

  const handleSelection = (e: React.MouseEvent, file: FileInfo, index: number) => {
    if (e.ctrlKey || e.metaKey) {
      const newSelection = new Set(selectedFiles);
      if (newSelection.has(file.name)) {
        newSelection.delete(file.name);
      } else {
        newSelection.add(file.name);
      }
      setSelectedFiles(newSelection);
      setLastSelectedIndex(index);
    } else if (e.shiftKey && lastSelectedIndex !== null) {
      const start = Math.min(lastSelectedIndex, index);
      const end = Math.max(lastSelectedIndex, index);
      const newSelection = new Set(selectedFiles);
      
      for (let i = start; i <= end; i++) {
        newSelection.add(processedFiles[i].name);
      }
      setSelectedFiles(newSelection);
    } else {
      setSelectedFiles(new Set([file.name]));
      setLastSelectedIndex(index);
    }
  };

  const handleBackgroundClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      setSelectedFiles(new Set());
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const day = date.getDate().toString().padStart(2, '0');
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const year = date.getFullYear();
    return `${day}/${month}/${year}`;
  };

  const handleContextMenu = (e: React.MouseEvent, file: FileInfo) => {
    e.preventDefault();
    e.stopPropagation();

    if (!selectedFiles.has(file.name)) {
      setSelectedFiles(new Set([file.name]));
    }
    
    setContextMenu({
      x: e.pageX,
      y: e.pageY,
      file: file
    });
  };

  const handleEdit = async (file: FileInfo) => {
    const editableExts = ['txt', 'md', 'json', 'xml', 'yml', 'yaml', 'js', 'ts', 'css', 'html', 'env', 'log', 'properties', 'ini', 'sh', 'bat'];
    const ext = file.name.split('.').pop()?.toLowerCase() || '';

    if (!editableExts.includes(ext)) {
      await modal.showAlert("Info", "Plik nieedytowalny.");
      return;
    }

    try {
      const path = currentPath ? `${currentPath}/${file.name}` : file.name;
      const blob = await fileService.getFileBlob(path);
      const text = await blob.text();

      setEditor({
        isOpen: true,
        content: text,
        file: file,
        saving: false
      });
    } catch (e) {
      await modal.showAlert("Błąd", "Nie udało się pobrać pliku.");
    }
  };

  const handleSaveEditor = async () => {
    if (!editor || !editor.file) return;
    
    setEditor(prev => prev ? { ...prev, saving: true } : null);
    try {
      const blob = new Blob([editor.content], { type: 'text/plain' });
      const fileToUpload = new File([blob], editor.file.name, { type: 'text/plain' });
      
      const pathToFile = currentPath ? `${currentPath}/${editor.file.name}` : editor.file.name;
      
      await fileService.delete(pathToFile);
      
      await addUpload(currentPath, fileToUpload, () => {
        setEditor(null);
        modal.showAlert("Sukces", "Zapisano.");
        loadFiles();
      });
      
    } catch (e) {
      setEditor(prev => prev ? { ...prev, saving: false } : null);
      await modal.showAlert("Błąd", "Błąd zapisu.");
    }
  };

  const handlePreview = async (file: FileInfo) => {
    const ext = file.name.split('.').pop()?.toLowerCase() || '';
    const isImage = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext);
    const isVideo = ['mp4', 'webm', 'ogg'].includes(ext);

    if (!isImage && !isVideo) {
      const editableExts = ['txt', 'md', 'json', 'xml', 'yml', 'yaml', 'js', 'ts', 'css', 'html', 'env', 'log'];
      if (editableExts.includes(ext)) {
          handleEdit(file);
          return;
      }
      const confirmed = await modal.showConfirm("Podgląd", `Pobrać plik "${file.name}"?`);
      if (confirmed) handleDownload(file.name);
      return;
    }

    try {
      const relativePath = currentPath ? `${currentPath}/${file.name}` : file.name;
      const finalPath = viewMode === 'trash' ? `trash/${file.name}` : relativePath;

      const blob = await fileService.getFileBlob(finalPath);
      const url = URL.createObjectURL(blob);
      
      setPreviewFile({ 
        url, 
        type: isImage ? 'image' : 'video', 
        name: file.name,
        originalFile: file
      });
    } catch (e) {
      const confirmed = await modal.showConfirm("Błąd", "Nie udało się otworzyć podglądu. Pobrać?");
      if (confirmed) handleDownload(file.name);
    }
  };

  const navigatePreview = (direction: 'next' | 'prev') => {
    if (!previewFile) return;
    
    const mediaFiles = processedFiles.filter(f => {
        const ext = f.name.split('.').pop()?.toLowerCase() || '';
        return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'mp4', 'webm', 'ogg'].includes(ext);
    });

    const currentIndex = mediaFiles.findIndex(f => f.name === previewFile.name);
    if (currentIndex === -1) return;

    let nextIndex = direction === 'next' ? currentIndex + 1 : currentIndex - 1;

    if (nextIndex >= 0 && nextIndex < mediaFiles.length) {
        URL.revokeObjectURL(previewFile.url);
        handlePreview(mediaFiles[nextIndex]);
    }
  };

  const closePreview = () => {
    if (previewFile) URL.revokeObjectURL(previewFile.url);
    setPreviewFile(null);
  };

  const handleDragOver = (e: React.DragEvent) => { e.preventDefault(); if (viewMode === 'files') setIsDragging(true); };
  const handleDragLeave = (e: React.DragEvent) => { e.preventDefault(); setIsDragging(false); };
  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault(); setIsDragging(false);
    if (viewMode !== 'files') return;
    const droppedFiles = Array.from(e.dataTransfer.files);
    if (!droppedFiles.length) return;
    
    for (const file of droppedFiles) {
        if (file.size > 2 * 1024 * 1024 * 1024) { 
            await modal.showAlert("Błąd", `Plik ${file.name} jest za duży.`); 
            continue; 
        }
        addUpload(currentPath, file, loadFiles);
    }
  };

  const handleNavigate = (folderName: string) => {
    if (viewMode === 'trash') return; 
    const newPath = currentPath ? `${currentPath}/${folderName}` : folderName;
    setCurrentPath(newPath);
  };
  
  const handleGoBack = () => {
    if (!currentPath) return;
    setCurrentPath(currentPath.split('/').slice(0, -1).join('/'));
  };

  const handleBreadcrumbClick = (index: number) => {
    if (!currentPath) return;
    const segments = currentPath.split('/');
    const newPath = segments.slice(0, index + 1).join('/');
    setCurrentPath(newPath);
  };

  const handleGoHome = () => setCurrentPath('');
  
  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!e.target.files?.length) return;
    const file = e.target.files[0];
    
    addUpload(currentPath, file, loadFiles);

    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleCreateFolder = async () => {
    const name = await modal.showPrompt("Nowy Folder", "Nazwa:");
    if (!name) return;
    try { await fileService.createFolder(currentPath ? `${currentPath}/${name}` : name); loadFiles(); } 
    catch (e) { await modal.showAlert("Błąd", "Nie udało się utworzyć."); }
  };

  const handleDownload = async (fileName: string) => {
    const fullPath = currentPath ? `${currentPath}/${fileName}` : fileName;
    try { await fileService.download(fullPath); } catch (e) { await modal.showAlert("Błąd", "Niepowodzenie pobierania."); }
  };

  const handleBatchDownload = async () => {
    if (selectedFiles.size === 0) return;
    const filesToDownload = Array.from(selectedFiles);
    for (const fileName of filesToDownload) {
        await handleDownload(fileName);
    }
  };

  const handleDeleteSoft = async (fileName: string) => {
    if (!await modal.showConfirm("Usuwanie", `Czy na pewno usunąć "${fileName}"?`)) return;
    try { await fileService.delete(currentPath ? `${currentPath}/${fileName}` : fileName); loadFiles(); } 
    catch (e) { await modal.showAlert("Błąd", "Niepowodzenie usuwania."); }
  };

  const handleBatchDeleteSoft = async () => {
    if (selectedFiles.size === 0) return;
    if (!await modal.showConfirm("Usuwanie grupowe", `Czy usunąć ${selectedFiles.size} elementów?`)) return;
    
    setLoading(true);
    for (const fileName of selectedFiles) {
        try {
            await fileService.delete(currentPath ? `${currentPath}/${fileName}` : fileName);
        } catch(e) { console.error(e); }
    }
    setLoading(false);
    setSelectedFiles(new Set());
    loadFiles();
  };

  const handleRestore = async (file: FileInfo) => {
    try { await fileService.restoreFile(file.logicalPath); loadFiles(); } catch (e) { await modal.showAlert("Błąd", "Niepowodzenie przywracania."); }
  };

  const handleBatchRestore = async () => {
    if (selectedFiles.size === 0) return;
    setLoading(true);
    const filesToRestore = files.filter(f => selectedFiles.has(f.name));
    for (const file of filesToRestore) {
        try { await fileService.restoreFile(file.logicalPath); } catch(e) { console.error(e); }
    }
    setLoading(false);
    setSelectedFiles(new Set());
    loadFiles();
  };

  const handleDeletePermanent = async (fileName: string) => {
    if (!await modal.showConfirm("Usuwanie", `Czy na pewno bezpowrotnie usunąć "${fileName}"?`)) return;
    try { await fileService.deletePermanently(fileName); loadFiles(); } catch (e) { await modal.showAlert("Błąd", "Niepowodzenie usuwania."); }
  };

  const handleBatchDeletePermanent = async () => {
    if (selectedFiles.size === 0) return;
    if (!await modal.showConfirm("Usuwanie trwałe", `Czy bezpowrotnie usunąć ${selectedFiles.size} elementów?`)) return;

    setLoading(true);
    for (const fileName of selectedFiles) {
        try { await fileService.deletePermanently(fileName); } catch(e) { console.error(e); }
    }
    setLoading(false);
    setSelectedFiles(new Set());
    loadFiles();
  };

  const handleEmptyTrash = async () => {
    if (!await modal.showConfirm("Kosz", "Czy opróżnić kosz?")) return;
    setLoading(true);
    try { await fileService.emptyTrash(); loadFiles(); } finally { setLoading(false); loadFiles(); }
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '-';
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return parseFloat((bytes / Math.pow(1024, i)).toFixed(1)) + ' ' + ['B', 'KB', 'MB', 'GB', 'TB'][i];
  };

  const getIcon = (file: FileInfo) => {
    if (file.isDirectory) return <Folder className="text-blue-500 fill-blue-50" />;
    const ext = file.name.split('.').pop()?.toLowerCase() || '';
    if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) return <FileImage className="text-purple-500" />;
    if (['mp4', 'mkv', 'mov', 'avi', 'webm'].includes(ext)) return <FileVideo className="text-red-500" />;
    if (['mp3', 'wav', 'ogg', 'flac'].includes(ext)) return <Music className="text-pink-500" />;
    if (['js', 'ts', 'jsx', 'tsx', 'java', 'py', 'html', 'css', 'json', 'xml', 'yml', 'md', 'txt'].includes(ext)) return <FileCode className="text-emerald-500" />;
    if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return <HardDrive className="text-orange-500" />;
    return <FileText className="text-slate-400" />;
  };

  const SortHeader = ({ label, sortKey, className = "" }: { label: string, sortKey: 'name' | 'size' | 'date', className?: string }) => (
    <th 
      className={`px-6 py-4 cursor-pointer hover:bg-slate-100 transition-colors select-none ${className}`}
      onClick={() => handleSort(sortKey)}
    >
      <div className="flex items-center gap-1">
        {label}
        {sortConfig.key === sortKey && (
          sortConfig.direction === 'asc' ? <ChevronUp size={14} /> : <ChevronDown size={14} />
        )}
      </div>
    </th>
  );

  return (
    <div 
      className={`relative transition-colors flex flex-col h-[calc(100vh-140px)] ${isDragging ? 'bg-blue-50/50 ring-4 ring-blue-200 ring-inset rounded-xl' : ''}`}
      onDragOver={handleDragOver} onDragLeave={handleDragLeave} onDrop={handleDrop}
      onClick={handleBackgroundClick}
    >
      {isDragging && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-blue-50/80 backdrop-blur-sm rounded-xl border-2 border-dashed border-blue-400 pointer-events-none">
          <div className="text-center text-blue-600 animate-bounce">
            <Upload size={64} className="mx-auto mb-4" />
            <h3 className="text-2xl font-bold">Upuść pliki tutaj</h3>
          </div>
        </div>
      )}

      <div className={`flex flex-col md:flex-row md:items-center justify-between gap-4 p-4 rounded-t-xl border-t border-x shadow-sm transition-colors ${viewMode === 'trash' ? 'bg-red-50 border-red-100' : 'bg-white border-slate-200'}`}>
        <div className="flex items-center gap-2 text-sm overflow-hidden flex-1 mr-4">
          {viewMode === 'trash' ? (
             <div className="flex items-center gap-2 text-red-700 font-bold px-2"><Trash2 size={20} /><span>Kosz Systemowy</span></div>
          ) : (
            <div className="flex items-center flex-wrap gap-1 text-slate-600">
              <button onClick={handleGoHome} className="hover:text-blue-600 p-1.5 rounded hover:bg-slate-100 transition-colors"><Home size={18} /></button>
              
              {currentPath ? (
                  <>
                    <span className="text-slate-300">/</span>
                    {currentPath.split('/').map((segment, index) => (
                        <React.Fragment key={index}>
                            {index > 0 && <span className="text-slate-300">/</span>}
                            <button 
                                onClick={() => handleBreadcrumbClick(index)}
                                className="hover:text-blue-600 hover:underline px-1 rounded font-medium truncate max-w-[100px]"
                            >
                                {segment}
                            </button>
                        </React.Fragment>
                    ))}
                  </>
              ) : (
                  <>
                    <span className="text-slate-300 mx-1">/</span>
                    <span className="font-medium text-slate-800">Folder Główny</span>
                  </>
              )}
            </div>
          )}
        </div>
        <div className="flex gap-2 flex-wrap justify-end items-center">
          {selectedFiles.size > 0 && (
              <div className="flex items-center gap-2 mr-2 bg-blue-50 px-3 py-1.5 rounded-lg border border-blue-100 animate-in fade-in">
                  <span className="text-xs font-bold text-blue-700">{selectedFiles.size} zaznaczono</span>
                  <button onClick={() => setSelectedFiles(new Set())} className="text-blue-400 hover:text-blue-700"><X size={14}/></button>
              </div>
          )}

          <div className="relative group">
            <Search className="absolute left-3 top-2.5 text-slate-400 group-focus-within:text-blue-500" size={16} />
            <input type="text" placeholder="Szukaj..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 pr-4 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm focus:ring-2 focus:ring-blue-500 outline-none w-32 focus:w-48 transition-all"
            />
          </div>
          <div className="w-px h-8 bg-slate-200 mx-1 hidden sm:block"></div>
          {viewMode === 'files' ? (
            <>
              <input type="file" ref={fileInputRef} onChange={handleUpload} className="hidden" />
              <button onClick={() => fileInputRef.current?.click()} className="p-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-sm transition-colors" title="Upload"><Upload size={18} /></button>
              <button onClick={handleCreateFolder} className="p-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors" title="Nowy Folder"><Plus size={18} /></button>
              <button onClick={() => setViewMode('trash')} className="p-2 bg-white border border-slate-200 text-slate-500 rounded-lg hover:bg-red-50 hover:text-red-600 hover:border-red-100 transition-colors" title="Kosz"><Trash2 size={18} /></button>
            </>
          ) : (
            <>
              <button onClick={handleEmptyTrash} className="flex items-center gap-2 px-3 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 text-sm font-medium shadow-sm"><AlertTriangle size={16} /> <span className="hidden sm:inline">Opróżnij</span></button>
              <button onClick={() => setViewMode('files')} className="p-2 bg-white border border-red-200 text-slate-600 rounded-lg hover:bg-slate-50 transition-colors" title="Powrót"><CornerUpLeft size={18} /></button>
            </>
          )}
        </div>
      </div>

      <div className="flex-1 bg-white border-x border-slate-200 overflow-y-auto">
        <table className="w-full text-left border-collapse select-none">
          <thead className="sticky top-0 bg-white z-10 shadow-sm">
            <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase">
              <SortHeader label="Nazwa" sortKey="name" className="w-auto" />
              <SortHeader label="Rozmiar" sortKey="size" className="w-32" />
              <SortHeader label={viewMode === 'trash' ? 'Usunięto' : 'Zmodyfikowano'} sortKey="date" className="w-48 hidden sm:table-cell" />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {viewMode === 'files' && currentPath && !searchQuery && (
              <tr onClick={handleGoBack} className="hover:bg-slate-50 cursor-pointer transition-colors group">
                <td className="px-6 py-3 flex items-center gap-3">
                  <div className="p-2 bg-slate-100 rounded-lg group-hover:bg-slate-200 transition-colors"><ArrowLeft size={18} className="text-slate-500" /></div>
                  <span className="font-medium text-slate-600">.. (W górę)</span>
                </td><td colSpan={2}></td>
              </tr>
            )}
            {loading ? (
              <tr><td colSpan={3} className="px-6 py-12 text-center text-slate-500 animate-pulse">Wczytywanie...</td></tr>
            ) : processedFiles.length === 0 ? (
              <tr><td colSpan={3} className="px-6 py-12 text-center text-slate-400 italic">Brak plików</td></tr>
            ) : (
              processedFiles.map((file, index) => (
                <tr 
                  key={file.name} 
                  className={`
                    group transition-colors cursor-context-menu 
                    ${selectedFiles.has(file.name) ? 'bg-blue-50 hover:bg-blue-100' : 'hover:bg-slate-50'}
                    ${contextMenu?.file.name === file.name && !selectedFiles.has(file.name) ? 'bg-blue-50' : ''}
                  `}
                  onClick={(e) => handleSelection(e, file, index)}
                  onContextMenu={(e) => handleContextMenu(e, file)}
                >
                  <td className="px-6 py-3">
                    <div className={`flex items-center gap-3 ${file.isDirectory && viewMode === 'files' ? 'cursor-pointer' : ''}`} 
                      onDoubleClick={(e) => {
                          e.stopPropagation();
                          if (file.isDirectory && viewMode === 'files') handleNavigate(file.name);
                          else if (!file.isDirectory && viewMode === 'files') handlePreview(file);
                      }}
                    >
                      <div className="shrink-0 relative">
                          {getIcon(file)}
                          {selectedFiles.has(file.name) && (
                              <div className="absolute -top-1 -right-1 bg-blue-600 rounded-full text-white p-0.5 border border-white shadow-sm">
                                  <X size={10} />
                              </div>
                          )}
                      </div>
                      <div className="flex flex-col">
                          <span className={`font-medium truncate max-w-[200px] sm:max-w-md ${file.isDirectory ? 'text-slate-800' : 'text-slate-600'} ${viewMode === 'trash' ? 'line-through opacity-60' : ''}`}>{file.name}</span>
                          {viewMode === 'trash' && (<span className="text-[10px] text-slate-400 font-mono hidden sm:inline-block">{file.logicalPath}</span>)}
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-3 text-sm text-slate-500 font-mono whitespace-nowrap">{file.isDirectory ? '-' : formatBytes(file.size)}</td>
                  <td className="px-6 py-3 text-sm text-slate-400 hidden sm:table-cell whitespace-nowrap">{formatDate(file.lastModified)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="bg-slate-50 border-t border-x border-b rounded-b-xl border-slate-200 px-4 py-2 text-xs text-slate-500 flex justify-between items-center select-none">
         <div>
            {processedFiles.length} elementów
         </div>
         <div>
            {selectedFiles.size > 0 ? (
                <span className="font-medium text-blue-600">
                    Wybrano: {selectedFiles.size} 
                    ({formatBytes(files.filter(f => selectedFiles.has(f.name)).reduce((acc, curr) => acc + curr.size, 0))})
                </span>
            ) : (
                <span>Wolne miejsce: {formatBytes(files.reduce((acc, curr) => acc + curr.size, 0))} (w tym folderze)</span>
            )}
         </div>
      </div>

      {contextMenu && (
        <div 
            className="fixed z-[100] bg-white rounded-lg shadow-xl border border-slate-100 py-1 w-56 animate-in fade-in zoom-in-95 duration-100"
            style={{ top: contextMenu.y, left: contextMenu.x }}
            onClick={(e) => e.stopPropagation()}
        >
            <div className="px-3 py-2 border-b border-slate-50 bg-slate-50/50">
                <p className="text-xs font-bold text-slate-500 truncate">
                    {selectedFiles.size > 1 ? `${selectedFiles.size} elementów` : contextMenu.file.name}
                </p>
            </div>
            
            {viewMode === 'files' ? (
              selectedFiles.size > 1 ? (
                <>
                    <button onClick={() => { handleBatchDownload(); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-emerald-50 hover:text-emerald-700 flex items-center gap-2">
                        <Download size={16} /> Pobierz zaznaczone
                    </button>
                    <div className="h-px bg-slate-100 my-1"></div>
                    <button onClick={() => { handleBatchDeleteSoft(); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2">
                        <Trash2 size={16} /> Usuń zaznaczone
                    </button>
                </>
              ) : (
                <>
                    <button onClick={() => { handlePreview(contextMenu.file); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-blue-50 hover:text-blue-700 flex items-center gap-2">
                        <Eye size={16} /> Podgląd / Edycja
                    </button>
                    
                    <button onClick={() => { handleDownload(contextMenu.file.name); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-slate-700 hover:bg-emerald-50 hover:text-emerald-700 flex items-center gap-2">
                        <Download size={16} /> Pobierz
                    </button>

                    <div className="h-px bg-slate-100 my-1"></div>
                    
                    <button onClick={() => { handleDeleteSoft(contextMenu.file.name); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2">
                        <Trash2 size={16} /> Usuń do kosza
                    </button>
                </>
              )
            ) : (
              selectedFiles.size > 1 ? (
                <>
                    <button onClick={() => { handleBatchRestore(); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-emerald-700 hover:bg-emerald-50 flex items-center gap-2">
                        <RotateCcw size={16} /> Przywróć zaznaczone
                    </button>
                    <div className="h-px bg-slate-100 my-1"></div>
                    <button onClick={() => { handleBatchDeletePermanent(); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2">
                        <XCircle size={16} /> Usuń trwale
                    </button>
                </>
              ) : (
                <>
                    <button onClick={() => { handleRestore(contextMenu.file); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-emerald-700 hover:bg-emerald-50 flex items-center gap-2">
                        <RotateCcw size={16} /> Przywróć
                    </button>
                    <div className="h-px bg-slate-100 my-1"></div>
                    <button onClick={() => { handleDeletePermanent(contextMenu.file.name); setContextMenu(null); }} className="w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 flex items-center gap-2">
                        <XCircle size={16} /> Usuń trwale
                    </button>
                </>
              )
            )}
        </div>
      )}

      {editor && editor.isOpen && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 backdrop-blur-sm animate-in fade-in">
            <div className="bg-white w-full max-w-4xl h-[80vh] rounded-xl shadow-2xl flex flex-col overflow-hidden">
                <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center bg-slate-50">
                    <div className="flex items-center gap-3">
                        <FileCode className="text-blue-600" size={24} />
                        <div>
                            <h3 className="font-bold text-slate-800">Edytor Pliku</h3>
                            <p className="text-xs text-slate-500 font-mono">{editor.file?.name}</p>
                        </div>
                    </div>
                    <div className="flex gap-2">
                        <button onClick={() => setEditor(null)} className="px-4 py-2 text-slate-600 hover:bg-slate-200 rounded-lg font-medium transition-colors">Anuluj</button>
                        <button 
                            onClick={handleSaveEditor} 
                            disabled={editor.saving}
                            className="px-4 py-2 bg-blue-600 text-white hover:bg-blue-700 rounded-lg font-medium flex items-center gap-2 disabled:opacity-50"
                        >
                            <Save size={18} /> {editor.saving ? 'Zapisywanie...' : 'Zapisz'}
                        </button>
                    </div>
                </div>
                <div className="flex-1 p-0 relative">
                    <textarea 
                        className="w-full h-full p-6 font-mono text-sm text-slate-800 bg-slate-50 resize-none outline-none focus:bg-white transition-colors leading-relaxed"
                        value={editor.content}
                        onChange={(e) => setEditor({...editor, content: e.target.value})}
                        spellCheck={false}
                    />
                </div>
            </div>
        </div>
      )}

      {previewFile && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/90 backdrop-blur-sm animate-in fade-in duration-200" onClick={closePreview}>
          <button onClick={(e) => { e.stopPropagation(); navigatePreview('prev'); }} className="absolute left-4 top-1/2 -translate-y-1/2 text-white/50 hover:text-white p-2 rounded-full hover:bg-white/10 transition-all">
            <ChevronLeft size={48} />
          </button>
          
          <button onClick={(e) => { e.stopPropagation(); navigatePreview('next'); }} className="absolute right-4 top-1/2 -translate-y-1/2 text-white/50 hover:text-white p-2 rounded-full hover:bg-white/10 transition-all">
            <ChevronRight size={48} />
          </button>

          <button onClick={closePreview} className="absolute top-4 right-4 text-white/70 hover:text-white p-2 bg-white/10 rounded-full hover:bg-white/20 transition-all"><X size={32} /></button>
          
          <div className="max-w-5xl max-h-[90vh] p-4 relative flex items-center justify-center" onClick={e => e.stopPropagation()}>
            {previewFile.type === 'image' ? (
              <img src={previewFile.url} alt={previewFile.name} className="max-w-full max-h-[85vh] rounded-lg shadow-2xl object-contain" />
            ) : (
              <video src={previewFile.url} controls autoPlay className="max-w-full max-h-[85vh] rounded-lg shadow-2xl bg-black" />
            )}
            <div className="absolute bottom-[-40px] left-0 right-0 text-center text-white font-medium">{previewFile.name}</div>
          </div>
        </div>
      )}
    </div>
  );
};