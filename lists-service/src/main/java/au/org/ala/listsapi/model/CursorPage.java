package au.org.ala.listsapi.model;

import java.util.List;

public class CursorPage<T> {
    private final List<T> content;
    private final String cursor;
    private final long totalElements;
    
    public CursorPage(List<T> content, String cursor, long totalElements) {
        this.content = content;
        this.cursor = cursor;
        this.totalElements = totalElements;
    }
    
    // Getters...
    public List<T> getContent() { return content; }
    public String getCursor() { return cursor; }
    public long getTotalElements() { return totalElements; }
    public boolean hasNext() { return cursor != null; }
}
