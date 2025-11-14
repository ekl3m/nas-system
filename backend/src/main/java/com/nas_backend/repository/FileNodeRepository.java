package com.nas_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nas_backend.model.entity.FileNode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileNodeRepository extends JpaRepository<FileNode, Long> {

    // Spring understands what these methods are supposed to do just by their names

    Optional<FileNode> findByLogicalPath(String logicalPath);

    List<FileNode> findByParentPath(String parentPath);
    
    boolean existsByLogicalPath(String logicalPath);

    List<FileNode> findByLogicalPathStartingWith(String prefix);
    
    List<FileNode> findByParentPathEndingWithAndModifiedAtBefore(String parentSuffix, Instant cutoffDate);

    @Query("SELECT n FROM FileNode n WHERE n.isDirectory = false AND n.parentPath NOT LIKE %:suffix")
    List<FileNode> findAllActiveFiles(@Param("suffix") String suffix);
}
