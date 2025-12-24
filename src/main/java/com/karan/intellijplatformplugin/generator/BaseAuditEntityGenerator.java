package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates base auditable entity class with JPA auditing support.
 */
public class BaseAuditEntityGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        String pkg = meta.basePackage() + ".entity";

        // ✅ CHECK IF FILE ALREADY EXISTS
        if (FileExistsUtil.fileExistsInPackage(root, pkg, "BaseAuditEntity.java")) {
            System.out.println("BaseAuditEntity.java already exists, skipping generation.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import jakarta.persistence.Column;
                import jakarta.persistence.EntityListeners;
                import jakarta.persistence.MappedSuperclass;
                import org.springframework.data.annotation.CreatedBy;
                import org.springframework.data.annotation.CreatedDate;
                import org.springframework.data.annotation.LastModifiedBy;
                import org.springframework.data.annotation.LastModifiedDate;
                import org.springframework.data.jpa.domain.support.AuditingEntityListener;
                
                import java.time.LocalDateTime;
                
                /**
                 * Base class for entities requiring audit information.
                 * Automatically tracks creation and modification timestamps and users.
                 */
                @MappedSuperclass
                @EntityListeners(AuditingEntityListener.class)
                public abstract class BaseAuditEntity {
                    
                    @CreatedDate
                    @Column(name = "created_at", nullable = false, updatable = false)
                    private LocalDateTime createdAt;
                    
                    @LastModifiedDate
                    @Column(name = "updated_at", nullable = false)
                    private LocalDateTime updatedAt;
                    
                    @CreatedBy
                    @Column(name = "created_by", updatable = false, length = 100)
                    private String createdBy;
                    
                    @LastModifiedBy
                    @Column(name = "updated_by", length = 100)
                    private String updatedBy;
                    
                    // Getters and Setters
                    
                    public LocalDateTime getCreatedAt() {
                        return createdAt;
                    }
                    
                    public void setCreatedAt(LocalDateTime createdAt) {
                        this.createdAt = createdAt;
                    }
                    
                    public LocalDateTime getUpdatedAt() {
                        return updatedAt;
                    }
                    
                    public void setUpdatedAt(LocalDateTime updatedAt) {
                        this.updatedAt = updatedAt;
                    }
                    
                    public String getCreatedBy() {
                        return createdBy;
                    }
                    
                    public void setCreatedBy(String createdBy) {
                        this.createdBy = createdBy;
                    }
                    
                    public String getUpdatedBy() {
                        return updatedBy;
                    }
                    
                    public void setUpdatedBy(String updatedBy) {
                        this.updatedBy = updatedBy;
                    }
                    
                    @Override
                    public String toString() {
                        return "BaseAuditEntity{" +
                                "createdAt=" + createdAt +
                                ", updatedAt=" + updatedAt +
                                ", createdBy='" + createdBy + '\\'' +
                                ", updatedBy='" + updatedBy + '\\'' +
                                '}';
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "BaseAuditEntity.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}