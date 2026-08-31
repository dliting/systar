package com.systar.server.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BatchResult {

    private final List<Integer> success = new ArrayList<>();
    private final Map<Integer, String> failed = new LinkedHashMap<>();

    public void addSuccess(int id) {
        success.add(id);
    }

    public void addFailure(int id, String reason) {
        failed.put(id, reason);
    }

    public List<Integer> getSuccess() {
        return success;
    }

    public Map<Integer, String> getFailed() {
        return failed;
    }
}
