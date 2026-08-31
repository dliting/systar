package com.systar.monitor.linkage;

import com.systar.monitor.asset.*;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.MonitorResultEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LinkageHandler {

    private static final Logger log = LoggerFactory.getLogger(LinkageHandler.class);

    private static final RuleIndex EMPTY = new RuleIndex(Map.of(), Map.of(), Map.of(), Map.of());

    private volatile RuleIndex ruleIndex = EMPTY;

    private final AssetStore          assetStore;
    private final LinkageRepository   linkageRepository;
    private final LinkageTriggerStrategy triggerStrategy;

    @Autowired
    public LinkageHandler(AssetStore assetStore, LinkageRepository linkageRepository) {
        this(assetStore, linkageRepository, new BooleanMarkerTriggerStrategy());
    }

    public LinkageHandler(AssetStore assetStore, LinkageRepository linkageRepository,
                          LinkageTriggerStrategy triggerStrategy) {
        this.assetStore       = assetStore;
        this.linkageRepository = linkageRepository;
        this.triggerStrategy  = triggerStrategy;
    }

    // ======================== event listener ========================

    @EventListener
    public void onMonitorResult(MonitorResultEvent event) {
        MonitorResult result = event.getResult();
        if (result == null || result.getMonitor() == null) {
            return;
        }

        Monitor<?> monitor = result.getMonitor();
        RuleIndex idx = this.ruleIndex;
        int monitorId = monitor.getId();

        // --- Monitor-triggered linkage ---
        if (triggerStrategy.shouldTrigger(monitor, result)) {
            List<LinkageRuleCauseBean> causes = idx.causeByMonitor().get(monitorId);
            if (causes != null) {
                String valueStr = String.valueOf(result.getValue());
                for (LinkageRuleCauseBean cause : causes) {
                    if (valueStr.equals(cause.getTriggerValue())) {
                        LinkageRuleBean rule = idx.ruleById().get(cause.getRuleId());
                        if (rule != null && rule.isEnabled() && rule.getCauseType() == CauseType.MONITOR) {
                            fireLinkage(rule, cause, monitor, result);
                        }
                    }
                }
            }
        }

        // --- Alarm-triggered linkage ---
        if (result.getStatus() == AssetState.WARNING || result.getStatus() == AssetState.ERROR) {
            List<Integer> alarmRuleIds = idx.alarmByMonitor().get(monitorId);
            if (alarmRuleIds != null) {
                for (Integer ruleId : alarmRuleIds) {
                    LinkageRuleBean rule = idx.ruleById().get(ruleId);
                    if (rule != null && rule.isEnabled() && rule.getCauseType() == CauseType.ALARM) {
                        fireAlarmLinkage(rule, monitor, result);
                    }
                }
            }
        }
    }

    @EventListener
    public void onCorrelationGroup(CorrelationGroupEvent event) {
        RuleIndex idx = this.ruleIndex;
        Integer deviceId = event.getDeviceId();
        if (deviceId == null) {
            return;
        }

        List<Integer> ruleIds = idx.correlationByDevice().get(deviceId);
        if (ruleIds == null) {
            return;
        }

        for (Integer ruleId : ruleIds) {
            LinkageRuleBean rule = idx.ruleById().get(ruleId);
            if (rule != null && rule.isEnabled() && rule.getCauseType() == CauseType.CORRELATION_GROUP) {
                fireCorrelationLinkage(rule, event);
            }
        }
    }

    // ======================== linkage execution ========================

    private void fireLinkage(LinkageRuleBean rule, LinkageRuleCauseBean cause,
                             Monitor<?> triggerMonitor, MonitorResult result) {
        List<LinkageRuleEffectBean> effects = rule.getEffects();
        if (effects == null || effects.isEmpty()) {
            log.debug("Linkage rule {} matched but has no effects.", rule.getId());
            return;
        }
        log.info("Linkage triggered: ruleId={}, causeType=MONITOR, triggerMonitor={}, value={}",
                rule.getId(), triggerMonitor.getId(), result.getValue());
        for (LinkageRuleEffectBean effect : effects) {
            executeEffect(rule.getId(), effect, triggerMonitor);
        }
    }

    private void fireAlarmLinkage(LinkageRuleBean rule,
                                  Monitor<?> triggerMonitor, MonitorResult result) {
        List<LinkageRuleEffectBean> effects = rule.getEffects();
        if (effects == null || effects.isEmpty()) {
            log.debug("Linkage rule {} matched but has no effects.", rule.getId());
            return;
        }
        log.info("Linkage triggered: ruleId={}, causeType=ALARM, triggerMonitor={}, status={}",
                rule.getId(), triggerMonitor.getId(), result.getStatus());
        for (LinkageRuleEffectBean effect : effects) {
            executeEffect(rule.getId(), effect, triggerMonitor);
        }
    }

    private void fireCorrelationLinkage(LinkageRuleBean rule, CorrelationGroupEvent event) {
        List<LinkageRuleEffectBean> effects = rule.getEffects();
        if (effects == null || effects.isEmpty()) {
            log.debug("Linkage rule {} matched but has no effects.", rule.getId());
            return;
        }
        log.info("Linkage triggered: ruleId={}, causeType=CORRELATION_GROUP, device={}, group={}",
                rule.getId(), event.getDeviceId(), event.getCorrelationGroup());

        Asset<?> triggerAsset = assetStore.findAsset(event.getDeviceId());
        for (LinkageRuleEffectBean effect : effects) {
            int targetId = effect.getEffectMonitorId();
            String command = effect.getEffectCommand();

            Asset<?> asset = assetStore.findAsset(targetId);
            if (!(asset instanceof Control control)) {
                log.warn("Linkage effect target {} is not a Control (found: {}). Skipping.",
                        targetId, asset != null ? asset.getClass().getSimpleName() : "null");
                linkageRepository.saveLinkageLog(rule.getId(), event.getDeviceId() != null
                        ? event.getDeviceId() : 0, targetId, command, false);
                continue;
            }

            boolean success;
            try {
                control.execute(command);
                success = true;
                log.info("Linkage effect executed: control={}, command={}", targetId, command);
            } catch (Exception e) {
                success = false;
                log.error("Linkage effect failed: control={}, command={}, error={}",
                        targetId, command, e.getMessage(), e);
            }
            linkageRepository.saveLinkageLog(rule.getId(), event.getDeviceId() != null
                    ? event.getDeviceId() : 0, targetId, command, success);
        }
    }

    private void executeEffect(int ruleId, LinkageRuleEffectBean effect,
                               Monitor<?> triggerMonitor) {
        int targetId = effect.getEffectMonitorId();
        String command = effect.getEffectCommand();

        Asset<?> asset = assetStore.findAsset(targetId);
        if (!(asset instanceof Control control)) {
            log.warn("Linkage effect target {} is not a Control (found: {}). Skipping.",
                    targetId, asset != null ? asset.getClass().getSimpleName() : "null");
            linkageRepository.saveLinkageLog(ruleId, triggerMonitor.getId(),
                    targetId, command, false);
            return;
        }

        boolean success;
        try {
            control.execute(command);
            success = true;
            log.info("Linkage effect executed: control={}, command={}", targetId, command);
        } catch (Exception e) {
            success = false;
            log.error("Linkage effect failed: control={}, command={}, error={}",
                    targetId, command, e.getMessage(), e);
        }
        linkageRepository.saveLinkageLog(ruleId, triggerMonitor.getId(),
                targetId, command, success);
    }

    // ======================== public API ========================

    public void loadRules(List<LinkageRuleBean> rules,
                          List<LinkageRuleCauseBean> allCauses,
                          List<LinkageRuleEffectBean> allEffects) {
        Map<Integer, LinkageRuleBean> ruleById = new HashMap<>();
        Map<Integer, List<LinkageRuleCauseBean>> causeByMonitor = new HashMap<>();
        Map<Integer, List<Integer>> alarmByMonitor = new HashMap<>();
        Map<Integer, List<Integer>> correlationByDevice = new HashMap<>();

        if (rules != null) {
            for (LinkageRuleBean rule : rules) {
                ruleById.put(rule.getId(), rule);
            }
        }

        if (allCauses != null) {
            for (LinkageRuleCauseBean cause : allCauses) {
                int mid = cause.getCauseMonitorId();
                LinkageRuleBean rule = ruleById.get(cause.getRuleId());
                if (rule == null) continue;

                switch (rule.getCauseType()) {
                    case MONITOR -> causeByMonitor.computeIfAbsent(mid, k -> new ArrayList<>()).add(cause);
                    case ALARM   -> alarmByMonitor.computeIfAbsent(mid, k -> new ArrayList<>()).add(rule.getId());
                    case CORRELATION_GROUP -> correlationByDevice.computeIfAbsent(mid, k -> new ArrayList<>())
                            .add(rule.getId());
                }
            }
        }

        if (allEffects != null) {
            Map<Integer, List<LinkageRuleEffectBean>> effectByRule = new HashMap<>();
            for (LinkageRuleEffectBean effect : allEffects) {
                effectByRule.computeIfAbsent(effect.getRuleId(), k -> new ArrayList<>())
                        .add(effect);
            }
            for (LinkageRuleBean rule : ruleById.values()) {
                rule.setEffects(effectByRule.getOrDefault(rule.getId(), List.of()));
            }
        }

        this.ruleIndex = new RuleIndex(
                Collections.unmodifiableMap(ruleById),
                toUnmodifiableMap(causeByMonitor),
                toUnmodifiableListMap(alarmByMonitor),
                toUnmodifiableListMap(correlationByDevice));

        log.info("Loaded linkage rules: {} rules, {} monitor-causes, {} alarm-monitors, {} correlation-devices.",
                ruleById.size(),
                causeByMonitor.values().stream().mapToInt(List::size).sum(),
                alarmByMonitor.values().stream().mapToInt(List::size).sum(),
                correlationByDevice.values().stream().mapToInt(List::size).sum());
    }

    private static <K, V> Map<K, List<V>> toUnmodifiableMap(Map<K, List<V>> source) {
        Map<K, List<V>> result = new HashMap<>();
        source.forEach((k, v) -> result.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(result);
    }

    private static <K> Map<K, List<Integer>> toUnmodifiableListMap(Map<K, List<Integer>> source) {
        Map<K, List<Integer>> result = new HashMap<>();
        source.forEach((k, v) -> result.put(k, List.copyOf(v)));
        return Collections.unmodifiableMap(result);
    }

    private record RuleIndex(
            Map<Integer, LinkageRuleBean> ruleById,
            Map<Integer, List<LinkageRuleCauseBean>> causeByMonitor,
            Map<Integer, List<Integer>> alarmByMonitor,
            Map<Integer, List<Integer>> correlationByDevice
    ) {}
}
