package com.systar.server.controller;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.server.controller.vo.GroupTreeVO;
import com.systar.server.controller.vo.GroupVO;
import com.systar.server.controller.vo.TreeNodeVO;
import com.systar.server.dto.GroupMembersRequest;
import com.systar.server.dto.GroupRequest;
import com.systar.server.dto.GroupTreeRequest;
import com.systar.server.repository.GroupRepository;
import com.systar.server.service.GroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Unified asset-tree forest API and group-tree CRUD.
 * Permissions reuse the iot:asset:* codes (no separate menu entries).
 */
@RestController
@RequestMapping("/api/monitor")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    // ======================== forest ========================

    @RequirePermission("iot:asset:list")
    @GetMapping("/asset-tree")
    public Result<List<TreeNodeVO>> getAssetTree(@RequestParam(defaultValue = "kind") String tree) {
        return Result.success(groupService.buildAssetTree(tree));
    }

    // ======================== group trees ========================

    @RequirePermission("iot:asset:list")
    @GetMapping("/group-trees")
    public Result<List<GroupTreeVO>> listTrees() {
        return Result.success(groupService.listTrees().stream()
                .map(t -> new GroupTreeVO(t.id(), t.name(), t.caption(), t.sequence()))
                .toList());
    }

    @RequirePermission("iot:asset:add")
    @PostMapping("/group-trees")
    public Result<Void> createTree(@RequestBody GroupTreeRequest req) {
        groupService.createTree(req.name(), req.caption(), orZero(req.sequence()));
        return Result.success(null);
    }

    @RequirePermission("iot:asset:edit")
    @PutMapping("/group-trees/{id}")
    public Result<Void> updateTree(@PathVariable long id, @RequestBody GroupTreeRequest req) {
        // Update caption is optional: null falls back to the name, symmetric with create semantics.
        groupService.updateTree(id, req.name(),
                req.caption() == null ? req.name() : req.caption(), req.sequence());
        return Result.success(null);
    }

    @RequirePermission("iot:asset:delete")
    @DeleteMapping("/group-trees/{id}")
    public Result<Void> deleteTree(@PathVariable long id) {
        groupService.deleteTree(id);
        return Result.success(null);
    }

    // ======================== groups ========================

    @RequirePermission("iot:asset:list")
    @GetMapping("/groups")
    public Result<List<GroupVO>> listGroups(@RequestParam long treeId) {
        return Result.success(groupService.listGroupVOs(treeId));
    }

    @RequirePermission("iot:asset:add")
    @PostMapping("/groups")
    public Result<Void> createGroup(@RequestBody GroupRequest req) {
        // Missing treeId becomes 0, which the service rejects as an unknown tree
        // (clean 400) instead of failing on primitive unboxing with an opaque 500.
        groupService.createGroup(orZero(req.treeId()), req.name(), req.caption(),
                req.parent() == null ? GroupRepository.TOP_LEVEL_PARENT : req.parent(),
                orZero(req.sequence()));
        return Result.success(null);
    }

    @RequirePermission("iot:asset:edit")
    @PutMapping("/groups/{id}")
    public Result<Void> updateGroup(@PathVariable long id, @RequestBody GroupRequest req) {
        boolean hasMove   = req.parent() != null && req.treeId() != null;
        boolean hasRename = req.name() != null;
        if (!hasMove && !hasRename) {
            throw new IllegalArgumentException("Nothing to update: provide name and/or parent+treeId.");
        }
        if (hasMove) {
            groupService.moveGroup(req.treeId(), id, req.parent());
        }
        if (hasRename) {
            // Update caption is optional: null falls back to the name, symmetric with create semantics.
            groupService.renameGroup(id, req.name(),
                    req.caption() == null ? req.name() : req.caption(), req.sequence());
        }
        return Result.success(null);
    }

    @RequirePermission("iot:asset:delete")
    @DeleteMapping("/groups/{id}")
    public Result<Void> deleteGroup(@PathVariable long id) {
        groupService.deleteGroup(id);
        return Result.success(null);
    }

    @RequirePermission("iot:asset:edit")
    @PutMapping("/groups/{id}/assets")
    public Result<Void> replaceGroupAssets(@PathVariable long id, @RequestBody GroupMembersRequest req) {
        groupService.replaceGroupAssets(id, req.assetIds());
        return Result.success(null);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
