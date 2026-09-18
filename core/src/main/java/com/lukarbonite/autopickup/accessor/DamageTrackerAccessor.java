package com.lukarbonite.autopickup.accessor;

import java.util.UUID;
import java.util.List;

public interface DamageTrackerAccessor {
    void autopickup_addAttacker(UUID playerUuid);
    List<UUID> autopickup_getAttackers();
}