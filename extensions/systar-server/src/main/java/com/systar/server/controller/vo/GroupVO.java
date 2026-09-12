package com.systar.server.controller.vo;

import java.util.List;

public record GroupVO(long id, long treeId, String name, String caption,
                      long parent, int level, int sequence, List<Long> assetIds) {
}
