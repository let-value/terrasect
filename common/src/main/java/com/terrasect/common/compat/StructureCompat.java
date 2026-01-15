package com.terrasect.common.compat;

import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class StructureCompat {

    private StructureCompat() {
    }

    public static String getStructureId(Holder<Structure> structure) {
        return structure.unwrapKey().map(ResourceKeyCompat::getKeyId).orElse("unknown");
    }

    public static Stream<TagKey<Structure>> getTags(Holder<Structure> structure) {
        return structure.tags();
    }
}
