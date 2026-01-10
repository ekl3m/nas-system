import { useState, useEffect, useCallback } from 'react';
import { Play, Image as ImageIcon, X, ChevronLeft, ChevronRight } from 'lucide-react';
import { fileService } from '../services/api';
import type { FileInfo } from '../types/api';

const AuthenticatedMedia = ({ file, onClick }: { file: FileInfo; onClick: () => void }) => {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    
    const fetchMedia = async () => {
      try {
        let pathToSend = file.logicalPath;
        pathToSend = pathToSend.replace(/\\/g, '/');

        if (pathToSend.includes('/')) {
            const parts = pathToSend.split('/');
            pathToSend = parts.slice(1).join('/');
        }


        const blob = await fileService.getFileBlob(pathToSend);
        if (active) {
          const url = URL.createObjectURL(blob);
          setObjectUrl(url);
        }
      } catch (err) {
        console.error("Błąd ładowania miniatury:", file.name, err);
      } finally {
        if (active) setLoading(false);
      }
    };

    fetchMedia();

    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [file.logicalPath]);

  const isVideo = ['mp4', 'mkv', 'mov', 'avi'].some(ext => file.name.toLowerCase().endsWith(ext));

  if (loading) {
    return (
      <div className="w-full h-48 bg-slate-100 animate-pulse rounded-lg flex items-center justify-center">
        <ImageIcon className="text-slate-300" />
      </div>
    );
  }

  return (
    <div 
      className="group relative w-full h-48 bg-slate-100 rounded-lg overflow-hidden cursor-pointer border border-slate-200 hover:shadow-md transition-all"
      onClick={onClick}
    >
      {objectUrl ? (
        isVideo ? (
          <div className="w-full h-full flex items-center justify-center bg-slate-900 relative">
             <video src={objectUrl} className="w-full h-full object-cover opacity-60" />
             <div className="absolute inset-0 flex items-center justify-center">
                <div className="bg-white/20 p-3 rounded-full backdrop-blur-sm">
                  <Play className="text-white fill-white" size={24} />
                </div>
             </div>
          </div>
        ) : (
          <img src={objectUrl} alt={file.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" />
        )
      ) : (
        <div className="flex items-center justify-center h-full text-slate-400">Brak podglądu</div>
      )}
      
      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-3 translate-y-full group-hover:translate-y-0 transition-transform">
        <p className="text-white text-xs truncate">{file.name}</p>
      </div>
    </div>
  );
};

export const Media = () => {
  const [files, setFiles] = useState<FileInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedFile, setSelectedFile] = useState<FileInfo | null>(null); 

  useEffect(() => {
    loadMedia();
  }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!selectedFile) return;
      
      if (e.key === 'ArrowRight') navigate('next');
      if (e.key === 'ArrowLeft') navigate('prev');
      if (e.key === 'Escape') setSelectedFile(null);
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [selectedFile, files]);

  const loadMedia = async () => {
    try {
      const data = await fileService.getRecent(20, true); 
      setFiles(data);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  };

  const navigate = useCallback((direction: 'next' | 'prev') => {
    if (!selectedFile || files.length === 0) return;

    const currentIndex = files.findIndex(f => f.logicalPath === selectedFile.logicalPath);
    if (currentIndex === -1) return;

    let newIndex;
    if (direction === 'next') {
      newIndex = (currentIndex + 1) % files.length; 
    } else {
      newIndex = (currentIndex - 1 + files.length) % files.length; 
    }

    setSelectedFile(files[newIndex]);
  }, [selectedFile, files]);

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      setSelectedFile(null);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Galeria Multimediów</h1>
        <p className="text-slate-500">Ostatnie zdjęcia i filmy.</p>
      </div>

      {loading ? (
        <div className="text-center py-10 text-slate-500">Ładowanie galerii...</div>
      ) : files.length === 0 ? (
        <div className="text-center py-10 bg-white rounded-xl border border-slate-200">
          <ImageIcon size={48} className="mx-auto text-slate-300 mb-2" />
          <p className="text-slate-500">Nie znaleziono multimediów.</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
          {files.map((file) => (
            <AuthenticatedMedia 
              key={file.logicalPath} 
              file={file} 
              onClick={() => setSelectedFile(file)}
            />
          ))}
        </div>
      )}

      {selectedFile && (
        <div 
          className="fixed inset-0 z-50 bg-black/95 flex items-center justify-center backdrop-blur-sm animate-in fade-in duration-200"
          onClick={handleBackdropClick}
        >
          <button 
            onClick={() => setSelectedFile(null)}
            className="absolute top-4 right-4 p-2 bg-white/10 hover:bg-white/20 text-white rounded-full transition-colors z-50"
          >
            <X size={32} />
          </button>

          <button 
            onClick={(e) => { e.stopPropagation(); navigate('prev'); }}
            className="absolute left-4 p-2 bg-white/10 hover:bg-white/20 text-white rounded-full transition-colors z-50 hidden md:block"
          >
            <ChevronLeft size={48} />
          </button>

          <button 
            onClick={(e) => { e.stopPropagation(); navigate('next'); }}
            className="absolute right-4 p-2 bg-white/10 hover:bg-white/20 text-white rounded-full transition-colors z-50 hidden md:block"
          >
            <ChevronRight size={48} />
          </button>
          
          <div 
            className="max-w-7xl max-h-[90vh] w-full flex flex-col items-center justify-center p-4"
            onClick={(e) => e.stopPropagation()} 
          >
             <AuthenticatedMediaContent file={selectedFile} />
             <p className="text-white/80 mt-4 font-medium text-sm bg-black/50 px-4 py-1 rounded-full">
                {selectedFile.name}
             </p>
          </div>
        </div>
      )}
    </div>
  );
};

const AuthenticatedMediaContent = ({ file }: { file: FileInfo }) => {
  const [src, setSrc] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(false);

    const loadFullRes = async () => {
      try {
        let pathToSend = file.logicalPath;
        
        if (!pathToSend) {
            if (!file.parentPath) pathToSend = file.name;
            else pathToSend = `${file.parentPath}/${file.name}`;
        }

        pathToSend = pathToSend.replace(/\\/g, '/');

        if (pathToSend.includes('/')) {
            const parts = pathToSend.split('/');
            pathToSend = parts.slice(1).join('/');
        }


        const blob = await fileService.getFileBlob(pathToSend);
        if (active) {
          setSrc(URL.createObjectURL(blob));
        }
      } catch (err) {
        console.error("Błąd pobierania pełnego pliku:", err);
        if (active) setError(true);
      } finally {
        if (active) setLoading(false);
      }
    };

    loadFullRes();

    return () => {
      active = false;
      if (src) URL.revokeObjectURL(src);
    };
  }, [file]);
  if (loading) return <div className="text-white text-lg animate-pulse">Ładowanie pełnej jakości...</div>;
  if (error || !src) return <div className="text-red-400">Nie udało się załadować podglądu.</div>;

  const isVideo = ['mp4', 'mkv', 'mov', 'avi'].some(ext => file.name.toLowerCase().endsWith(ext));

  if (isVideo) {
    return (
      <video 
        src={src} 
        controls 
        autoPlay 
        className="max-w-full max-h-[85vh] rounded-lg shadow-2xl outline-none bg-black" 
      />
    );
  }
  
  return (
    <img 
      src={src} 
      alt={file.name} 
      className="max-w-full max-h-[85vh] object-contain rounded-lg shadow-2xl bg-black/20" 
    />
  );
};