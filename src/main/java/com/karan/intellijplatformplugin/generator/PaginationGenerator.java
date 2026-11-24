package com.karan.intellijplatformplugin.generator;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.karan.intellijplatformplugin.model.ClassMeta;
import com.karan.intellijplatformplugin.util.FileExistsUtil;
import com.karan.intellijplatformplugin.util.PsiDirectoryUtil;

import java.util.List;

/**
 * Generates shared pagination and sorting support classes.
 *
 * <p>Multi-entity changes:
 * <ul>
 *   <li>Accepts {@code List<ClassMeta> allEntities} — standard multi-entity contract.</li>
 *   <li>{@code PageResponse<T>} and {@code SortDirection} are <em>shared infrastructure</em>
 *       — they are generated exactly once regardless of how many entities are processed.
 *       Both the on-disk {@link FileExistsUtil} check and a PSI directory membership check
 *       guard against duplicate generation within the same {@code WriteCommandAction}.</li>
 *   <li>{@code org.springframework.data.domain.Page} is now a proper import instead of
 *       a fully-qualified inline reference.</li>
 *   <li>{@code PageResponse.of()} is overloaded to also accept a {@code Page<T>} whose
 *       content has already been mapped to DTOs — used by {@link ControllerGenerator}
 *       when it calls {@code pageResult.map(Mapper::toDto)}.</li>
 * </ul>
 */
public class PaginationGenerator {

    /**
     * Entry point — generates both shared pagination classes.
     *
     * @param project     current IntelliJ project
     * @param root        source root ({@code src/main/java})
     * @param meta        metadata of the entity currently being generated
     *                    (used only for the base package — output is entity-independent)
     * @param allEntities full list of project entities (multi-entity contract)
     */
    public static void generate(
            Project project,
            PsiDirectory root,
            ClassMeta meta,
            List<ClassMeta> allEntities
    ) {
        if (project == null || root == null || meta == null) {
            throw new IllegalArgumentException("Project, root directory, and metadata cannot be null");
        }

        generatePageResponseDto(project, root, meta);
        generateSortDirection(project, root, meta);
    }

    // =========================================================================
    // PageResponse<T>
    // =========================================================================

    /**
     * Generates a generic {@code PageResponse<T>} wrapper DTO.
     *
     * <p>This class is <strong>shared</strong> — it is not tied to any specific entity.
     * It is placed in {@code <basePackage>.dto} alongside entity-specific DTOs.
     *
     * <p>Generation is skipped if the file already exists either:
     * <ul>
     *   <li>On disk (checked via {@link FileExistsUtil})</li>
     *   <li>In the PSI directory in-memory state (checked via
     *       {@link #fileExistsInPsiDirectory}) — guards against repeated calls
     *       within a single {@code WriteCommandAction} when multiple entities
     *       are generated in sequence</li>
     * </ul>
     */
    private static void generatePageResponseDto(
            Project project,
            PsiDirectory root,
            ClassMeta meta
    ) {
        String pkg      = meta.basePackage() + ".dto";
        String fileName = "PageResponse.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                import io.swagger.v3.oas.annotations.media.Schema;
                import org.springframework.data.domain.Page;
                import java.util.List;
                
                /**
                 * Generic paginated response wrapper used by all paginated endpoints.
                 *
                 * <p>Usage in controllers:
                 * <pre>
                 *   Page&lt;EntityDto&gt; dtoPage = service.findAllPaginated(...).map(Mapper::toDto);
                 *   PageResponse&lt;EntityDto&gt; response = PageResponse.of(dtoPage);
                 * </pre>
                 *
                 * @param <T> type of content items — always a DTO, never a raw entity
                 */
                @Schema(description = "Paginated response wrapper")
                public class PageResponse<T> {
                
                    @Schema(description = "List of items in current page")
                    private List<T> content;
                
                    @Schema(description = "Current page number (0-indexed)", example = "0")
                    private int pageNumber;
                
                    @Schema(description = "Number of items per page", example = "10")
                    private int pageSize;
                
                    @Schema(description = "Total number of items across all pages", example = "100")
                    private long totalElements;
                
                    @Schema(description = "Total number of pages", example = "10")
                    private int totalPages;
                
                    @Schema(description = "Whether this is the first page")
                    private boolean first;
                
                    @Schema(description = "Whether this is the last page")
                    private boolean last;
                
                    @Schema(description = "Whether a next page exists")
                    private boolean hasNext;
                
                    @Schema(description = "Whether a previous page exists")
                    private boolean hasPrevious;
                
                    // ── Constructors ──────────────────────────────────────────────────
                
                    public PageResponse() {}
                
                    public PageResponse(
                            List<T> content,
                            int pageNumber,
                            int pageSize,
                            long totalElements,
                            int totalPages
                    ) {
                        this.content       = content;
                        this.pageNumber    = pageNumber;
                        this.pageSize      = pageSize;
                        this.totalElements = totalElements;
                        this.totalPages    = totalPages;
                        this.first         = (pageNumber == 0);
                        this.last          = (pageNumber >= totalPages - 1);
                        this.hasNext       = (pageNumber < totalPages - 1);
                        this.hasPrevious   = (pageNumber > 0);
                    }
                
                    // ── Factory methods ───────────────────────────────────────────────
                
                    /**
                     * Creates a {@code PageResponse} directly from a Spring {@link Page}.
                     *
                     * <p>Use this overload when the page content is already the correct
                     * type (e.g. after calling {@code page.map(Mapper::toDto)}).
                     *
                     * @param page Spring Data page (content type matches {@code T})
                     * @param <T>  DTO type
                     */
                    public static <T> PageResponse<T> of(Page<T> page) {
                        return new PageResponse<>(
                                page.getContent(),
                                page.getNumber(),
                                page.getSize(),
                                page.getTotalElements(),
                                page.getTotalPages()
                        );
                    }
                
                    // ── Getters & Setters ─────────────────────────────────────────────
                
                    public List<T> getContent() { return content; }
                    public void setContent(List<T> content) { this.content = content; }
                
                    public int getPageNumber() { return pageNumber; }
                    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
                
                    public int getPageSize() { return pageSize; }
                    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
                
                    public long getTotalElements() { return totalElements; }
                    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
                
                    public int getTotalPages() { return totalPages; }
                    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
                
                    public boolean isFirst() { return first; }
                    public void setFirst(boolean first) { this.first = first; }
                
                    public boolean isLast() { return last; }
                    public void setLast(boolean last) { this.last = last; }
                
                    public boolean isHasNext() { return hasNext; }
                    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
                
                    public boolean isHasPrevious() { return hasPrevious; }
                    public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }
                
                    // ── toString ──────────────────────────────────────────────────────
                
                    @Override
                    public String toString() {
                        return "PageResponse{"
                                + "pageNumber=" + pageNumber
                                + ", pageSize=" + pageSize
                                + ", totalElements=" + totalElements
                                + ", totalPages=" + totalPages
                                + ", contentSize=" + (content != null ? content.size() : 0)
                                + '}';
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }

    // =========================================================================
    // SortDirection enum
    // =========================================================================

    /**
     * Generates a {@code SortDirection} enum for use as a typed request parameter
     * alternative to raw {@code String} sort direction values.
     *
     * <p>Like {@code PageResponse}, this is shared infrastructure generated once.
     */
    private static void generateSortDirection(
            Project project,
            PsiDirectory root,
            ClassMeta meta
    ) {
        String pkg      = meta.basePackage() + ".dto";
        String fileName = "SortDirection.java";

        if (FileExistsUtil.fileExistsInPackage(root, pkg, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists on disk — skipping.");
            return;
        }

        PsiDirectory dir = PsiDirectoryUtil.createPackageDirs(root, pkg);

        if (fileExistsInPsiDirectory(dir, fileName)) {
            System.out.println("ℹ️  " + fileName + " already exists in PSI directory — skipping.");
            return;
        }

        String code = String.format("""
                package %s;
                
                /**
                 * Sort direction options for paginated queries.
                 *
                 * <p>Maps directly to {@link org.springframework.data.domain.Sort.Direction}.
                 * Used as a typed alternative to raw String parameters in service/repository calls.
                 */
                public enum SortDirection {
                
                    /**
                     * Ascending order — A→Z, 0→9, oldest→newest.
                     */
                    ASC,
                
                    /**
                     * Descending order — Z→A, 9→0, newest→oldest.
                     */
                    DESC;
                
                    /**
                     * Converts this enum value to a Spring Data {@link org.springframework.data.domain.Sort.Direction}.
                     *
                     * @return corresponding Spring {@code Sort.Direction}
                     */
                    public org.springframework.data.domain.Sort.Direction toSpringDirection() {
                        return this == DESC
                                ? org.springframework.data.domain.Sort.Direction.DESC
                                : org.springframework.data.domain.Sort.Direction.ASC;
                    }
                
                    /**
                     * Case-insensitive parse from a raw request parameter string.
                     * Defaults to {@link #ASC} for any unrecognised value.
                     *
                     * @param value raw string value (e.g. "asc", "DESC")
                     * @return parsed {@code SortDirection}, never {@code null}
                     */
                    public static SortDirection fromString(String value) {
                        if (value == null) return ASC;
                        return "DESC".equalsIgnoreCase(value.trim()) ? DESC : ASC;
                    }
                }
                """, pkg);

        PsiFile file = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, JavaFileType.INSTANCE, code);

        dir.add(file);
        System.out.println("✅ Generated " + fileName);
    }

    // =========================================================================
    // PSI directory guard — prevents duplicate add within WriteCommandAction
    // =========================================================================

    /**
     * Checks whether a file with the given name already exists inside the
     * given PSI directory's current in-memory children.
     *
     * <p>This is a complementary guard to the on-disk {@link FileExistsUtil} check.
     * Within a single {@code WriteCommandAction}, files added early in the action
     * are visible as PSI children but may not yet be flushed to disk, so the
     * disk check alone is not sufficient.
     *
     * @param dir      PSI directory to inspect
     * @param fileName file name to look for, e.g. {@code "PageResponse.java"}
     * @return {@code true} if a child with that name already exists
     */
    private static boolean fileExistsInPsiDirectory(PsiDirectory dir, String fileName) {
        if (dir == null || fileName == null) return false;
        for (PsiFile existing : dir.getFiles()) {
            if (fileName.equals(existing.getName())) return true;
        }
        return false;
    }
}