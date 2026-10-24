package com.systar.monitor.server;

import com.systar.monitor.alarm.AlarmRule;
import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetLoader;
import com.systar.monitor.linkage.LinkageRuleBean;
import com.systar.monitor.linkage.LinkageRuleCauseBean;
import com.systar.monitor.linkage.LinkageRuleEffectBean;

import java.util.List;

/**
 * Runtime port of the monitoring engine. Implemented by the closed-source
 * engine's {@code MonitorServer}; the open server injects it optionally
 * ({@code ObjectProvider}) and degrades to no-ops when absent.
 * <p>
 * Asset queries do NOT go through this port — inject {@code AssetStore}
 * directly. This port carries only the operations that need the engine's
 * scheduler, handlers and lifecycle.
 * <p>
 * Engine-internal surface: {@code detectOnce}, {@code controlExecute},
 * {@code receivePassiveData}, {@code reloadAlarmRules} and
 * {@code reloadLinkageRules} mirror the engine facade for use inside the
 * engine and have no callers in the open tree — the open server drives
 * detection and control through {@code detectImmediately} /
 * {@code controlImmediately}. The full surface is pinned by the contract
 * test.
 */
public interface MonitorEngine {

    void loadAssets(AssetLoader loader);

    void loadAlarmRules(List<AlarmRule> rules);

    void reloadAlarmRules(List<AlarmRule> rules);

    void loadLinkageRules(List<LinkageRuleBean> rules,
                          List<LinkageRuleCauseBean> causes,
                          List<LinkageRuleEffectBean> effects);

    void reloadLinkageRules(List<LinkageRuleBean> rules,
                            List<LinkageRuleCauseBean> causes,
                            List<LinkageRuleEffectBean> effects);

    void startUp();

    void shutDown();

    void addAsset(Asset<?> asset);

    void removeAsset(int id);

    void updateAsset(Asset<?> asset);

    void startMonitor(int id);

    void stopMonitor(int id);

    void detectOnce(int id);

    void detectImmediately(int id);

    String controlExecute(int controlId, String command);

    void controlImmediately(int controlId, String command);

    void receivePassiveData(String registerKey, Object value);
}
