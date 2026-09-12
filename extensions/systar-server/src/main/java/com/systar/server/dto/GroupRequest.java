package com.systar.server.dto;

public record GroupRequest(Long treeId, String name, String caption, Long parent, Integer sequence) {
}
