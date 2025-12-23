package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

/**
 * Generates pagination and sorting support classes.
 */
public class PaginationGenerator {

    public static void generate(Project project, PsiDirectory root, ClassMeta meta) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        generatePageResponseDto(project, root, meta);
        generateSortDirection(project, root, meta);
    }

    /**
     * Generates PageResponse DTO for paginated results
     */
    private static void generatePageResponseDto(Project project, PsiDirectory root, ClassMeta meta) {
        String pkg = meta.basePackage() + ".dto";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                import java.util.List;
                
                /**
                 * Generic paginated response wrapper.
                 * @param <T> Type of content in the page
                 */
                @Schema(description = "Paginated response wrapper")
                public class PageResponse<T> {
                    
                    @Schema(description = "List of items in current page")
                    private List<T> content;
                    
                    @Schema(description = "Current page number (0-indexed)", example = "0")
                    private int pageNumber;
                    
                    @Schema(description = "Number of items per page", example = "10")
                    private int pageSize;
                    
                    @Schema(description = "Total number of items", example = "100")
                    private long totalElements;
                    
                    @Schema(description = "Total number of pages", example = "10")
                    private int totalPages;
                    
                    @Schema(description = "Whether this is the first page")
                    private boolean first;
                    
                    @Schema(description = "Whether this is the last page")
                    private boolean last;
                    
                    @Schema(description = "Whether there are more pages")
                    private boolean hasNext;
                    
                    @Schema(description = "Whether there are previous pages")
                    private boolean hasPrevious;
                    
                    public PageResponse() {}
                    
                    public PageResponse(List<T> content, int pageNumber, int pageSize, 
                                       long totalElements, int totalPages) {
                        this.content = content;
                        this.pageNumber = pageNumber;
                        this.pageSize = pageSize;
                        this.totalElements = totalElements;
                        this.totalPages = totalPages;
                        this.first = pageNumber == 0;
                        this.last = pageNumber == totalPages - 1;
                        this.hasNext = pageNumber < totalPages - 1;
                        this.hasPrevious = pageNumber > 0;
                    }
                    
                    // Static factory method
                    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> page) {
                        return new PageResponse<>(
                            page.getContent(),
                            page.getNumber(),
                            page.getSize(),
                            page.getTotalElements(),
                            page.getTotalPages()
                        );
                    }
                    
                    // Getters and Setters
                    public List<T> getContent() {
                        return content;
                    }
                    
                    public void setContent(List<T> content) {
                        this.content = content;
                    }
                    
                    public int getPageNumber() {
                        return pageNumber;
                    }
                    
                    public void setPageNumber(int pageNumber) {
                        this.pageNumber = pageNumber;
                    }
                    
                    public int getPageSize() {
                        return pageSize;
                    }
                    
                    public void setPageSize(int pageSize) {
                        this.pageSize = pageSize;
                    }
                    
                    public long getTotalElements() {
                        return totalElements;
                    }
                    
                    public void setTotalElements(long totalElements) {
                        this.totalElements = totalElements;
                    }
                    
                    public int getTotalPages() {
                        return totalPages;
                    }
                    
                    public void setTotalPages(int totalPages) {
                        this.totalPages = totalPages;
                    }
                    
                    public boolean isFirst() {
                        return first;
                    }
                    
                    public void setFirst(boolean first) {
                        this.first = first;
                    }
                    
                    public boolean isLast() {
                        return last;
                    }
                    
                    public void setLast(boolean last) {
                        this.last = last;
                    }
                    
                    public boolean isHasNext() {
                        return hasNext;
                    }
                    
                    public void setHasNext(boolean hasNext) {
                        this.hasNext = hasNext;
                    }
                    
                    public boolean isHasPrevious() {
                        return hasPrevious;
                    }
                    
                    public void setHasPrevious(boolean hasPrevious) {
                        this.hasPrevious = hasPrevious;
                    }
                    
                    @Override
                    public String toString() {
                        return "PageResponse{" +
                                "pageNumber=" + pageNumber +
                                ", pageSize=" + pageSize +
                                ", totalElements=" + totalElements +
                                ", totalPages=" + totalPages +
                                ", contentSize=" + (content != null ? content.size() : 0) +
                                '}';
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "PageResponse.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }

    /**
     * Generates SortDirection enum
     */
    private static void generateSortDirection(Project project, PsiDirectory root, ClassMeta meta) {
        String pkg = meta.basePackage() + ".dto";
        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        String code = String.format("""
                package %s;
                
                /**
                 * Enum for sort direction.
                 */
                public enum SortDirection {
                    /**
                     * Ascending order (A-Z, 0-9, oldest-newest)
                     */
                    ASC,
                    
                    /**
                     * Descending order (Z-A, 9-0, newest-oldest)
                     */
                    DESC
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(
                        "SortDirection.java",
                        JavaFileType.INSTANCE,
                        code
                );

        dir.add(file);
    }
}