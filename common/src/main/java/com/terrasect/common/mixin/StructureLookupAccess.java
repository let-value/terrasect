package com.terrasect.common.mixin;

import com.terrasect.common.lookup.StructureLookup;

public interface StructureLookupAccess {
    StructureLookup terrasect$getStructureLookup();

    void terrasect$setStructureLookup(StructureLookup lookup);
}
