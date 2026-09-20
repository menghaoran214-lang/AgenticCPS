package com.qiji.cps.module.ai.service.capability;

/** Domain-owned local tool risk classification. Unknown tools are denied. */
public interface AiCapabilityRiskProvider {
    String riskOf(String toolName);
}
